package com.baloch.ai.power

import android.content.Context
import android.media.MediaCodec
import android.media.MediaExtractor
import android.media.MediaFormat
import android.media.MediaPlayer
import android.media.MediaRecorder
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import org.vosk.Model
import org.vosk.Recognizer
import java.io.File
import java.nio.ByteOrder
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * MEETING RECORDER — "meeting record karo" → "recording band karo". 🎙️
 *
 * Records to AAC-in-MP4 in app-private storage (filesDir/meetings/) via
 * MediaRecorder. Needs RECORD_AUDIO (runtime permission — the caller must
 * hold it before calling [startRecording]; the feature toggle alone does
 * not grant it). Files NEVER leave the device: no upload, no cloud.
 *
 * The settings toggle gates the whole feature (voice commands refuse when
 * OFF); [PowerPrefs.RECORDER_TRANSCRIBE] makes every stop auto-transcribe
 * on-device via Vosk.
 *
 * TRANSCRIPTION — honest quality note: on-device Vosk decoding of the
 * decoded AAC. It decodes the AAC track to PCM with MediaExtractor +
 * MediaCodec, resamples to the 16 kHz mono the model expects, then runs
 * the same Vosk model the wake-word engine uses (filesDir/vosk-model —
 * no second download). Best-effort: noisy rooms, overlapping speakers
 * and accents degrade it. It returns null when the model isn't installed
 * instead of pretending.
 */
object MeetingRecorder {

    private const val DIR = "meetings"

    @Volatile
    private var recorder: MediaRecorder? = null

    @Volatile
    private var currentFile: File? = null

    @Volatile
    var isRecording: Boolean = false
        private set

    fun isOn(context: Context): Boolean = PowerPrefs.isOn(context, PowerPrefs.RECORDER)

    fun setOn(context: Context, on: Boolean) {
        if (!on) stopRecording()
        PowerPrefs.setOn(context, PowerPrefs.RECORDER, on)
    }

    fun transcribeOn(context: Context): Boolean =
        PowerPrefs.isOn(context, PowerPrefs.RECORDER_TRANSCRIBE)

    fun setTranscribeOn(context: Context, on: Boolean) =
        PowerPrefs.setOn(context, PowerPrefs.RECORDER_TRANSCRIBE, on)

    /**
     * Starts recording. Returns the destination file, or null if the
     * recorder is already running / the feature is off / prepare failed.
     * Caller must hold RECORD_AUDIO.
     */
    @Synchronized
    fun startRecording(context: Context): File? {
        if (!isOn(context) || isRecording) return null
        return try {
            val dir = File(context.filesDir, DIR).apply { mkdirs() }
            val stamp = SimpleDateFormat("yyyyMMdd-HHmmss", Locale.US).format(Date())
            val file = File(dir, "meeting-$stamp.m4a")
            @Suppress("DEPRECATION")
            val mr = MediaRecorder().apply {
                setAudioSource(MediaRecorder.AudioSource.MIC)
                setOutputFormat(MediaRecorder.OutputFormat.MPEG_4)
                setAudioEncoder(MediaRecorder.AudioEncoder.AAC)
                setAudioSamplingRate(44100)
                setAudioEncodingBitRate(128_000)
                setOutputFile(file.absolutePath)
                prepare()
                start()
            }
            recorder = mr
            currentFile = file
            isRecording = true
            file
        } catch (_: Exception) {
            cleanup()
            null
        }
    }

    /** Stops recording. Returns the finished file, or null if none was running. */
    @Synchronized
    fun stopRecording(): File? {
        val file = currentFile
        try {
            recorder?.apply { stop(); release() }
        } catch (_: Exception) {
            // stop() throws if start() never really began — still clean up.
        }
        cleanup()
        // Drop empty/corrupt stubs so the list never shows ghosts.
        if (file != null && (!file.exists() || file.length() < 1024)) {
            runCatching { file.delete() }
            return null
        }
        return file
    }

    private fun cleanup() {
        recorder = null
        currentFile = null
        isRecording = false
    }

    /** Newest first. */
    fun listRecordings(context: Context): List<File> {
        val dir = File(context.filesDir, DIR)
        if (!dir.isDirectory) return emptyList()
        return dir.listFiles { f -> f.isFile && f.name.endsWith(".m4a") }
            ?.sortedByDescending { it.lastModified() }
            ?: emptyList()
    }

    fun deleteRecording(file: File): Boolean {
        RecordingPlayer.stop()
        return runCatching { file.delete() }.getOrDefault(false)
    }

    fun displayName(file: File): String =
        file.name.removeSuffix(".m4a").removePrefix("meeting-")
}

/** Tiny single-track player for the recordings list. */
object RecordingPlayer {

    @Volatile
    private var player: MediaPlayer? = null

    @Volatile
    var playingFile: File? = null
        private set

    val isPlaying: Boolean get() = player != null

    @Synchronized
    fun play(file: File): Boolean {
        stop()
        return try {
            val mp = MediaPlayer().apply {
                setDataSource(file.absolutePath)
                prepare()
                setOnCompletionListener { stop() }
                start()
            }
            player = mp
            playingFile = file
            true
        } catch (_: Exception) {
            stop()
            false
        }
    }

    @Synchronized
    fun stop() {
        try {
            player?.apply { stop(); release() }
        } catch (_: Exception) {
        }
        player = null
        playingFile = null
    }
}

/**
 * On-device transcription of a finished recording via Vosk.
 *
 * Pipeline: MediaExtractor → MediaCodec AAC decode → 16-bit PCM →
 * crude decimation resample to 16 kHz mono → Vosk Recognizer.
 * Blocking JNI + codec work — always call from Dispatchers.IO.
 * Returns null when the Vosk model isn't installed or decode fails.
 */
object MeetingTranscriber {

    private const val MODEL_DIR = "vosk-model"
    private const val MODEL_MARKER = "am/final.mdl"
    private const val VOSK_RATE = 16000

    fun isReady(context: Context): Boolean =
        File(File(context.filesDir, MODEL_DIR), MODEL_MARKER).exists()

    suspend fun transcribe(context: Context, file: File): String? =
        withContext(Dispatchers.IO) {
            if (!isReady(context) || !file.exists()) return@withContext null
            try {
                val pcm16k = decodeTo16kMono(file) ?: return@withContext null
                if (pcm16k.isEmpty()) return@withContext null
                val model = Model(File(context.filesDir, MODEL_DIR).absolutePath)
                val rec = Recognizer(model, VOSK_RATE.toFloat())
                // Vosk is not thread-safe; this whole block runs on one IO thread.
                // Feeding mirrors OfflineSpeechInput exactly: short→LE bytes,
                // acceptWaveForm(bytes, byteCount), finalResult at the end.
                val transcript: String? = synchronized(this) {
                    try {
                        try { rec.reset() } catch (_: Exception) { /* older builds lack reset() */ }
                        val feedChunk = VOSK_RATE * 5 // 5-second feeds
                        val bytes = ByteArray(feedChunk * 2)
                        var off = 0
                        while (off < pcm16k.size) {
                            val n = minOf(feedChunk, pcm16k.size - off)
                            for (i in 0 until n) {
                                val s = pcm16k[off + i].toInt()
                                bytes[i * 2] = (s and 0xFF).toByte()
                                bytes[i * 2 + 1] = ((s shr 8) and 0xFF).toByte()
                            }
                            try {
                                rec.acceptWaveForm(bytes, n * 2)
                            } catch (_: Exception) {
                                return@withContext null
                            }
                            off += n
                        }
                        extractText(rec.finalResult).trim().ifBlank { null }
                    } finally {
                        try { rec.close() } catch (_: Exception) { }
                        try { model.close() } catch (_: Exception) { }
                    }
                }
                transcript
            } catch (_: Exception) {
                null
            }
        }

    private fun extractText(json: String): String =
        runCatching { JSONObject(json).optString("text", "") }.getOrDefault("")

    /**
     * Decodes the AAC track to 16 kHz mono 16-bit PCM.
     * Resampling is simple decimation/averaging — good enough for a
     * best-effort transcript, documented honestly above.
     */
    private fun decodeTo16kMono(file: File): ShortArray? {
        val extractor = MediaExtractor()
        try {
            extractor.setDataSource(file.absolutePath)
            var track = -1
            var format: MediaFormat? = null
            for (i in 0 until extractor.trackCount) {
                val f = extractor.getTrackFormat(i)
                val mime = f.getString(MediaFormat.KEY_MIME).orEmpty()
                if (mime.startsWith("audio/")) {
                    track = i; format = f; break
                }
            }
            if (track < 0 || format == null) return null
            extractor.selectTrack(track)

            val srcRate = format.getInteger(MediaFormat.KEY_SAMPLE_RATE)
            val channels = format.getInteger(MediaFormat.KEY_CHANNEL_COUNT)
            val mime = format.getString(MediaFormat.KEY_MIME)!!

            val codec = MediaCodec.createDecoderByType(mime)
            codec.configure(format, null, null, 0)
            codec.start()

            val raw = mutableListOf<Short>()
            val info = MediaCodec.BufferInfo()
            var sawInputEos = false
            var sawOutputEos = false
            val timeoutUs = 10_000L

            while (!sawOutputEos) {
                if (!sawInputEos) {
                    val inIdx = codec.dequeueInputBuffer(timeoutUs)
                    if (inIdx >= 0) {
                        val buf = codec.getInputBuffer(inIdx)!!
                        val n = extractor.readSampleData(buf, 0)
                        if (n < 0) {
                            codec.queueInputBuffer(
                                inIdx, 0, 0, 0,
                                MediaCodec.BUFFER_FLAG_END_OF_STREAM
                            )
                            sawInputEos = true
                        } else {
                            codec.queueInputBuffer(inIdx, 0, n, extractor.sampleTime, 0)
                            extractor.advance()
                        }
                    }
                }
                val outIdx = codec.dequeueOutputBuffer(info, timeoutUs)
                when {
                    outIdx >= 0 -> {
                        val buf = codec.getOutputBuffer(outIdx)!!
                        if (info.size > 0) {
                            val shorts = ShortArray(info.size / 2)
                            buf.order(ByteOrder.LITTLE_ENDIAN).asShortBuffer().get(shorts)
                            // Stereo → mono by averaging channel pairs.
                            if (channels == 2) {
                                for (i in shorts.indices step 2) {
                                    raw.add(((shorts[i] + shorts[i + 1]) / 2).toShort())
                                }
                            } else {
                                shorts.forEach { raw.add(it) }
                            }
                        }
                        codec.releaseOutputBuffer(outIdx, false)
                        if (info.flags and MediaCodec.BUFFER_FLAG_END_OF_STREAM != 0) {
                            sawOutputEos = true
                        }
                    }
                    outIdx == MediaCodec.INFO_TRY_AGAIN_LATER -> {
                        if (sawInputEos) sawOutputEos = true // defensive: don't spin forever
                    }
                }
            }
            codec.stop(); codec.release()

            // Decimate to 16 kHz.
            val mono = raw.toShortArray()
            if (srcRate == VOSK_RATE) return mono
            val ratio = srcRate.toDouble() / VOSK_RATE
            if (ratio < 1.0) return mono // upsampling would fake data — skip it
            val outLen = (mono.size / ratio).toInt()
            return ShortArray(outLen) { i -> mono[(i * ratio).toInt().coerceIn(mono.indices)] }
        } catch (_: Exception) {
            return null
        } finally {
            runCatching { extractor.release() }
        }
    }
}
