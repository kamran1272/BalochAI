package com.baloch.ai.voice

import android.media.AudioFormat
import android.media.AudioRecord
import android.media.MediaRecorder
import kotlin.concurrent.thread

/**
 * Raw-microphone capture: 16 kHz, mono, 16-bit PCM — the exact format the
 * VoiceLockManager's MFCC pipeline expects.
 *
 * HOW IT WORKS (for Kamran): your phone's mic produces a stream of numbers
 * (16,000 per second), each number = how far the microphone diaphragm moved
 * at that instant. AudioRecord reads those numbers; this class collects
 * them into one ShortArray while recording runs in the background.
 *
 * LIFECYCLE: start() → (audio flows for as long as you let it) → stop().
 * stop() returns EVERYTHING recorded since start(), or null if the mic
 * could not be opened (e.g. RECORD_AUDIO permission missing/denied).
 *
 * THREADING: reading happens on a background thread because AudioRecord
 * must be drained continuously — its internal buffer is small and would
 * overflow (dropping audio) if we only read at stop() time.
 */
class UtteranceCapture(
    private val sampleRate: Int = SAMPLE_RATE,
) {
    private var recorder: AudioRecord? = null
    private var readerThread: Thread? = null

    /** Guarded by [chunksLock]; chunks are appended on the reader thread. */
    private val chunks = ArrayList<ShortArray>()
    private val chunksLock = Any()

    @Volatile
    private var running = false

    /**
     * Opens the mic and starts capturing. Returns false when the mic cannot
     * be opened — callers should then fall back to the PIN (never to "no
     * verification").
     */
    fun start(): Boolean {
        if (running) return true // already capturing; harmless no-op
        val minBuf = AudioRecord.getMinBufferSize(
            sampleRate,
            AudioFormat.CHANNEL_IN_MONO,
            AudioFormat.ENCODING_PCM_16BIT,
        )
        if (minBuf <= 0) return false // device reports no usable input config
        return try {
            val rec = AudioRecord(
                MediaRecorder.AudioSource.MIC,
                sampleRate,
                AudioFormat.CHANNEL_IN_MONO,
                AudioFormat.ENCODING_PCM_16BIT,
                // 4x headroom so a slow reader thread never starves the mic.
                minBuf * 4,
            )
            if (rec.state != AudioRecord.STATE_INITIALIZED) {
                rec.release()
                return false
            }
            recorder = rec
            running = true
            readerThread = thread(name = "utterance-capture", isDaemon = true) {
                drainLoop(rec)
            }
            rec.startRecording()
            true
        } catch (e: SecurityException) {
            // RECORD_AUDIO not granted — the ONLY expected failure mode.
            running = false
            recorder = null
            false
        } catch (e: IllegalArgumentException) {
            running = false
            recorder = null
            false
        }
    }

    /**
     * Stops the mic, waits for the reader thread to finish, and returns all
     * captured samples. Returns null if nothing was captured.
     */
    fun stop(): ShortArray? {
        running = false
        try {
            readerThread?.join(READER_JOIN_MS)
        } catch (_: InterruptedException) {
            Thread.currentThread().interrupt()
        }
        readerThread = null
        val rec = recorder
        recorder = null
        try {
            rec?.stop()
        } catch (_: Exception) {
            // Already stopped or never started — nothing to do.
        }
        rec?.release()

        synchronized(chunksLock) {
            if (chunks.isEmpty()) return null
            val total = chunks.sumOf { it.size }
            if (total == 0) return null
            val out = ShortArray(total)
            var pos = 0
            for (c in chunks) {
                c.copyInto(out, pos)
                pos += c.size
            }
            chunks.clear()
            return out
        }
    }

    /** Continuously pulls samples off the mic into [chunks] until stopped. */
    private fun drainLoop(rec: AudioRecord) {
        val buf = ShortArray(READ_CHUNK)
        while (running) {
            val n = try {
                rec.read(buf, 0, buf.size)
            } catch (_: Exception) {
                break
            }
            if (n < 0) break // ERROR_INVALID_OPERATION / ERROR_BAD_VALUE
            if (n > 0) {
                synchronized(chunksLock) {
                    chunks.add(buf.copyOf(n))
                }
            }
        }
    }

    companion object {
        /** 16 kHz — the standard rate for speech work; MFCC math assumes it. */
        const val SAMPLE_RATE = 16000

        /** Samples read per AudioRecord.read() call (100 ms of audio). */
        private const val READ_CHUNK = 1600

        /** Max time to wait for the reader thread to finish on stop(). */
        private const val READER_JOIN_MS = 2000L
    }
}
