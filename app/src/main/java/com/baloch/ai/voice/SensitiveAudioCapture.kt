package com.baloch.ai.voice

import android.content.Context
import android.media.AudioFormat
import android.media.AudioRecord
import android.media.MediaRecorder
import android.media.audiofx.AutomaticGainControl
import android.media.audiofx.NoiseSuppressor
import android.os.SystemClock
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlin.math.log10
import kotlin.math.sqrt
import kotlin.math.tanh

/**
 * Sensitive (quiet-voice) microphone capture for Baloch.
 *
 * Kamran's order: even when he speaks softly or whispers, Baloch must catch
 * every word — and when he whispers, Baloch replies softly (context-aware).
 *
 * THE AUDIO CHAIN (how it works, in plain terms):
 *
 *   mic → [software gain + soft limiter] → [RMS normalisation]
 *       → [NoiseSuppressor + AutomaticGainControl (built-in Android effects)]
 *       → energy gating → chunks out
 *
 * Think of it like a hearing aid with four stages:
 *  1. GAIN turns the volume knob up on the raw mic signal. A whisper is a
 *     tiny electrical wobble; multiplying every sample by [gain] (default
 *     2x, configurable 1x–4x) makes it big enough for the rest of the chain
 *     to work with. The SOFT LIMITER is the "safety valve": if a loud sound
 *     comes in (door slam, laugh), naive gain would push samples past the
 *     16-bit ceiling (±32767) and they'd CLIP — the waveform gets its tops
 *     chopped flat, which sounds harsh and confuses speech recognisers. The
 *     limiter uses a tanh curve: gentle for normal levels, progressively
 *     squashing as the signal approaches ±30000, so loud peaks get rounded
 *     instead of chopped. (DSP name for this: "soft clipping".)
 *  2. NORMALISATION evens out loud vs. quiet: every chunk is scaled so its
 *     loudness (RMS, see below) lands near a fixed target (~6000). A whisper
 *     gets lifted; normal speech is left alone (scale = 1.0 when it's already
 *     loud enough, so we never "blow out" a loud room). There's a ceiling on
 *     the scale factor so a nearly-silent chunk of pure noise doesn't get
 *     amplified into a roar.
 *  3. Android's BUILT-IN EFFECTS: NoiseSuppressor eats steady background
 *     hum (fan, AC, traffic drone) and AutomaticGainControl is the OS's own
 *     volume-riding loop. Attached to the AudioRecord session when the
 *     device offers them; everything degrades gracefully if not.
 *  4. ENERGY GATING decides "is someone actually talking?" by comparing each
 *     chunk's loudness against a threshold, so silence doesn't get fed to
 *     the recogniser (and doesn't drain battery on pointless work).
 *
 * RMS (root mean square) — the "loudness number" used everywhere here. For
 * a chunk of N samples x[i], RMS = sqrt(mean(x[i]^2)). It measures the
 * average POWER of the signal, which tracks perceived loudness far better
 * than peak amplitude. dB values in this file are dBFS (decibels relative
 * to digital full scale): dB = 20 * log10(rms / 32768). Full-scale sine ≈
 * 0 dB; a whisper is around −40 dB; the noise floor of a quiet room is
 * around −58 dB.
 *
 * THREADING: readChunk() BLOCKS (up to ~100 ms per call waiting for the
 * mic to fill a chunk), so call it from Dispatchers.IO or a worker thread —
 * captureUtterance() already does this internally. Never call from the main
 * thread.
 *
 * OWNERSHIP: this class owns only ITS OWN AudioRecord. VoiceInput /
 * UtteranceCapture (sibling classes) are untouched — the parent wires this
 * into the audio path in the final pass via [readChunk] / [captureUtterance]
 * / [whisperListener].
 */
class SensitiveAudioCapture(context: Context) {

    private val appContext: Context = context.applicationContext

    private val prefs = appContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    private var recorder: AudioRecord? = null
    private var noiseSuppressor: NoiseSuppressor? = null
    private var autoGainControl: AutomaticGainControl? = null

    @Volatile
    private var running = false

    /**
     * Software mic-gain multiplier, 1x–4x. The setter clamps to that range:
     * below 1x is pointless (that's what normal capture already is) and
     * above 4x starts amplifying the mic's own electronic noise faster than
     * the voice, so 4x is the sane ceiling.
     */
    var gain: Float = DEFAULT_GAIN
        set(value) {
            field = value.coerceIn(1.0f, MAX_GAIN)
        }

    /**
     * "Quiet voice mode" — ON by default. When ON:
     *  - the energy gate drops from NORMAL_ENERGY_THRESHOLD to the much
     *    lower QUIET_ENERGY_THRESHOLD, so whispers pass the gate;
     *  - the end-of-utterance silence timeout stretches (whispers naturally
     *    have longer pauses between words).
     * TRADE-OFF (honest): a lower gate means background noise (keyboard,
     * AC hum, a TV in the next room) is more likely to trip the gate too —
     * more false wakes in noisy rooms. In a quiet room it is pure win.
     * Persisted in SharedPreferences so it survives app restarts.
     */
    var quietMode: Boolean = prefs.getBoolean(PREF_KEY_QUIET_MODE, true)
        set(value) {
            field = value
            prefs.edit().putBoolean(PREF_KEY_QUIET_MODE, value).apply()
        }

    /**
     * Optional push callback: every chunk that flows through readChunk() /
     * captureUtterance() is delivered here as PROCESSED PCM (gain + limiter
     * + normalisation applied — the same bytes a recogniser would eat),
     * plus the RAW input level in dB and the whisper verdict for that chunk.
     * Set by the parent wiring; null by default.
     */
    var whisperListener: WhisperListener? = null

    /**
     * Raw (pre-processing) input level of the most recent chunk, in dBFS.
     * Debug/tuning use: watch this on a quiet room vs. a whisper vs. normal
     * speech and you'll see why the thresholds below are where they are.
     */
    @Volatile
    var lastInputRmsDb: Float = FLOOR_DB
        private set

    /**
     * Whisper verdict of the most recent READ chunk. Note: this is per-chunk,
     * and a quiet syllable mid-sentence can flip it — use
     * [Utterance.wasWhisper] (majority vote over the whole utterance) for the
     * "Baloch replies softly" decision. Set false when the mic is idle.
     */
    @Volatile
    var lastWasWhisper: Boolean = false
        private set

    // ------------------------------------------------------------------
    // Lifecycle
    // ------------------------------------------------------------------

    /**
     * Opens the mic: 16 kHz, mono, 16-bit PCM — the standard speech format
     * (UtteranceCapture's MFCC pipeline and most recognisers assume it).
     * Buffer is 2x the device minimum: enough headroom that a 100 ms read
     * cadence never starves, without adding needless latency.
     * Returns false when the mic can't be opened (permission denied etc.).
     */
    fun start(): Boolean {
        if (running) return true // already capturing; harmless no-op
        val minBuf = AudioRecord.getMinBufferSize(
            SAMPLE_RATE,
            AudioFormat.CHANNEL_IN_MONO,
            AudioFormat.ENCODING_PCM_16BIT,
        )
        if (minBuf <= 0) return false // device reports no usable input config
        return try {
            val rec = AudioRecord(
                MediaRecorder.AudioSource.MIC,
                SAMPLE_RATE,
                AudioFormat.CHANNEL_IN_MONO,
                AudioFormat.ENCODING_PCM_16BIT,
                minBuf * 2,
            )
            if (rec.state != AudioRecord.STATE_INITIALIZED) {
                rec.release()
                return false
            }
            attachBuiltInEffects(rec)
            recorder = rec
            running = true
            rec.startRecording()
            true
        } catch (e: SecurityException) {
            // RECORD_AUDIO not granted.
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
     * Reads ONE chunk (100 ms of audio), runs it through the full chain
     * (gain → soft limiter → normalisation), updates [lastInputRmsDb] and
     * [lastWasWhisper] from the RAW input, fires [whisperListener], and
     * returns the processed PCM ready for a recogniser.
     * Returns null when the mic is not running or a read error occurs.
     * BLOCKS until a chunk is available — IO thread only.
     */
    fun readChunk(): ShortArray? = readAndProcess()?.pcm

    /** Stops capture and releases the mic. Idempotent — safe to call twice. */
    fun stop() {
        running = false
        val rec = recorder
        recorder = null
        try {
            rec?.stop()
        } catch (_: Exception) {
            // Already stopped or never started — nothing to do.
        }
        rec?.release()
        releaseEffects()
    }

    /** Full teardown: stop() plus dropping every native effect handle. */
    fun release() {
        stop()
        whisperListener = null
    }

    // ------------------------------------------------------------------
    // DSP stages (pure functions — no Android calls, easy to unit-test)
    // ------------------------------------------------------------------

    /**
     * RMS loudness of a chunk, in raw sample units (0–32767 scale).
     * rms() is the yardstick every threshold in this file is measured in.
     */
    fun rms(samples: ShortArray): Float {
        if (samples.isEmpty()) return 0f
        var sum = 0.0
        for (s in samples) {
            val v = s.toDouble()
            sum += v * v
        }
        return sqrt(sum / samples.size).toFloat()
    }

    /**
     * Stage 1+2 of the chain, applied per sample:
     *   out = SOFT_CLIP * tanh((in * gain) / SOFT_CLIP)
     *
     * The multiply is the software mic-gain ([gain], 1x–4x). The tanh is the
     * soft limiter: for small inputs tanh(x) ≈ x, so quiet audio passes
     * through almost untouched; as the amplified value nears ±SOFT_CLIP the
     * curve flattens and the peak gets ROUNDED at ~±30000 instead of chopped
     * at ±32767 (hard clipping). Loud transients stay recognisable and never
     * wrap around into distortion.
     */
    fun applyGainAndLimit(samples: ShortArray, gain: Float = this.gain): ShortArray {
        val g = gain.coerceIn(1.0f, MAX_GAIN)
        val out = ShortArray(samples.size)
        for (i in samples.indices) {
            val amplified = samples[i].toDouble() * g
            // tanh-style soft clip at ±SOFT_CLIP.
            val limited = SOFT_CLIP * tanh(amplified / SOFT_CLIP)
            out[i] = limited.toInt().coerceIn(Short.MIN_VALUE.toInt(), Short.MAX_VALUE.toInt()).toShort()
        }
        return out
    }

    /**
     * Stage 3: RMS normalisation. Scales the chunk so its RMS lands on
     * TARGET_RMS (~6000 ≈ −14.7 dBFS, a healthy level for recognisers).
     *
     *  - Input already at/above target (loud room, normal speech): scale =
     *    1.0 — untouched, so loud environments never "blow out".
     *  - Quiet speech/whisper: scaled UP toward the target.
     *  - Near-silence (below NOISE_FLOOR_RMS): untouched — lifting pure noise
     *    would just amplify hiss, and the energy gate drops these chunks
     *    anyway.
     *  - The scale is capped at MAX_NORMALISE_SCALE so a freak faint chunk
     *    can't explode into a burst of amplified noise.
     */
    fun normalise(samples: ShortArray): ShortArray {
        val inputRms = rms(samples)
        if (inputRms < NOISE_FLOOR_RMS) return samples.copyOf() // silence — don't lift noise
        if (inputRms >= TARGET_RMS) return samples.copyOf()    // already loud — ceiling, leave alone
        val scale = (TARGET_RMS / inputRms).coerceAtMost(MAX_NORMALISE_SCALE)
        val out = ShortArray(samples.size)
        for (i in samples.indices) {
            out[i] = (samples[i] * scale).toInt()
                .coerceIn(Short.MIN_VALUE.toInt(), Short.MAX_VALUE.toInt()).toShort()
        }
        return out
    }

    /**
     * Whisper verdict for a chunk of RAW (unprocessed) input: the RMS sits
     * BELOW WHISPER_RMS_THRESHOLD (so it's genuinely soft) but ABOVE the
     * noise floor (so it's voice, not room hiss). Mid-range between the two
     * is the "whisper band" — that's what triggers Baloch's soft reply.
     */
    fun isWhisper(samples: ShortArray): Boolean {
        val r = rms(samples)
        return r in NOISE_FLOOR_RMS..WHISPER_RMS_THRESHOLD
    }

    /**
     * One-line debug string for tuning logs, e.g.
     *   "-41.2 dB | whisper | quiet=true"
     * Watch it in logcat while whispering vs. speaking normally in the
     * target room — that is how the thresholds below get validated.
     */
    fun dbString(): String {
        val label = when {
            rmsDbToRms(lastInputRmsDb) < NOISE_FLOOR_RMS -> "silent"
            lastWasWhisper -> "whisper"
            else -> "speech"
        }
        return "%.1f dB | %s | quiet=%b".format(lastInputRmsDb, label, quietMode)
    }

    // ------------------------------------------------------------------
    // Utterance capture: energy-gated start, silence-timeout end
    // ------------------------------------------------------------------

    /**
     * Listens until someone speaks, then records until they go quiet.
     *
     *  - START: the first chunk whose raw RMS crosses the energy gate
     *    (QUIET_ENERGY_THRESHOLD in quiet mode, NORMAL_ENERGY_THRESHOLD
     *    otherwise). Nothing before that is kept — silence is discarded.
     *  - END: continuous below-gate audio longer than the silence timeout
     *    ends the utterance. Quiet mode uses the LONGER timeout because
     *    whispers naturally have longer pauses between words; cutting at a
     *    short timeout would chop whispered sentences in half.
     *  - Overall hard cap [timeoutMs] no matter what.
     *
     * Returns [Utterance] with the PROCESSED pcm (recogniser-ready), the
     * majority-vote whisper verdict, and the mean raw input level in dB.
     * An empty pcm (size 0) means nobody spoke before the timeout — or the
     * mic failed to open; the parent should fall back to the normal
     * SpeechRecognizer path rather than erroring at the user.
     */
    suspend fun captureUtterance(timeoutMs: Long = 6000): Utterance =
        withContext(Dispatchers.IO) {
            lastWasWhisper = false
            if (!start()) {
                return@withContext Utterance(ShortArray(0), false, FLOOR_DB)
            }
            val energyGate = if (quietMode) QUIET_ENERGY_THRESHOLD else NORMAL_ENERGY_THRESHOLD
            val silenceTimeoutMs =
                if (quietMode) SILENCE_TIMEOUT_QUIET_MS else SILENCE_TIMEOUT_NORMAL_MS

            val deadline = SystemClock.elapsedRealtime() + timeoutMs
            val pcm = ArrayList<Short>()
            var speechChunks = 0
            var whisperChunks = 0
            var inputRmsSum = 0.0
            var speechStarted = false
            var silenceStartMs = 0L

            try {
                while (SystemClock.elapsedRealtime() < deadline) {
                    val chunk = readAndProcess() ?: break
                    val now = SystemClock.elapsedRealtime()
                    if (!speechStarted) {
                        // Still waiting for someone to talk — discard silence.
                        if (chunk.inputRms >= energyGate) {
                            speechStarted = true
                            silenceStartMs = now
                        } else {
                            continue
                        }
                    }
                    // Speech is underway: keep everything (pauses included,
                    // so whispered sentences stay whole).
                    pcm.ensureCapacity(pcm.size + chunk.pcm.size)
                    for (s in chunk.pcm) pcm.add(s)
                    speechChunks++
                    inputRmsSum += chunk.inputRms
                    if (chunk.whisper) whisperChunks++

                    if (chunk.inputRms < energyGate) {
                        if (now - silenceStartMs >= silenceTimeoutMs) break // they finished talking
                    } else {
                        silenceStartMs = now // talking again — reset the pause clock
                    }
                }
            } finally {
                stop()
            }

            val samples = ShortArray(pcm.size) { pcm[it] }
            val meanRmsDb = if (speechChunks > 0) {
                rmsToDb((inputRmsSum / speechChunks).toFloat())
            } else {
                FLOOR_DB
            }
            // Majority vote: most whisper-classified speech chunks → the
            // user whispered this utterance → Baloch replies softly.
            val wasWhisper = speechChunks > 0 && whisperChunks * 2 >= speechChunks
            lastWasWhisper = wasWhisper
            Utterance(samples, wasWhisper, meanRmsDb)
        }

    // ------------------------------------------------------------------
    // Internals
    // ------------------------------------------------------------------

    /** One read + full processing, keeping the raw measurements alongside. */
    private fun readAndProcess(): ProcessedChunk? {
        val rec = recorder ?: return null
        if (!running) return null
        val raw = ShortArray(READ_CHUNK)
        val n = try {
            rec.read(raw, 0, raw.size) // blocks until a chunk is ready
        } catch (_: Exception) {
            return null
        }
        if (n <= 0) return null // ERROR_INVALID_OPERATION / ERROR_BAD_VALUE
        val input = raw.copyOf(n)
        val inputRms = rms(input)
        lastInputRmsDb = rmsToDb(inputRms)
        val whisper = isWhisper(input)
        lastWasWhisper = whisper
        // Full chain on the audio that leaves this class.
        val processed = normalise(applyGainAndLimit(input))
        whisperListener?.onAudioChunk(processed, lastInputRmsDb, whisper)
        return ProcessedChunk(processed, inputRms, whisper)
    }

    /**
     * Attaches Android's built-in effects to this recording session.
     * Both are guarded by isAvailable() and wrapped in try/catch — on
     * devices without the effect (or where the OEM blocks it on a plain MIC
     * source; some only enable these on VOICE_RECOGNITION), capture works
     * exactly as before, just without that stage. Nothing here can crash
     * start(): failure means "no effect", never "no mic".
     */
    private fun attachBuiltInEffects(rec: AudioRecord) {
        val sessionId = rec.audioSessionId
        if (sessionId == 0) return // no valid session — effects have nowhere to attach
        try {
            // Kills steady background drone (fan/AC/traffic). create() is
            // deprecated at API 31+ but remains the only way to bind an
            // effect to a session id on minSdk 26 — it still works.
            noiseSuppressor = if (NoiseSuppressor.isAvailable()) {
                NoiseSuppressor.create(sessionId)
            } else {
                null
            }
        } catch (_: Exception) {
            noiseSuppressor = null
        }
        try {
            // The OS's own volume-riding loop: evens out near/far talkers.
            // Ours (gain + normalise) runs regardless; this is a bonus stage.
            autoGainControl = if (AutomaticGainControl.isAvailable()) {
                AutomaticGainControl.create(sessionId)
            } else {
                null
            }
        } catch (_: Exception) {
            autoGainControl = null
        }
    }

    private fun releaseEffects() {
        try {
            noiseSuppressor?.release()
        } catch (_: Exception) {
            // ignore
        }
        try {
            autoGainControl?.release()
        } catch (_: Exception) {
            // ignore
        }
        noiseSuppressor = null
        autoGainControl = null
    }

    private fun rmsToDb(r: Float): Float =
        if (r <= 0f) FLOOR_DB else (20 * log10(r / FULL_SCALE)).toFloat()

    private fun rmsDbToRms(db: Float): Float =
        (FULL_SCALE * Math.pow(10.0, (db / 20).toDouble())).toFloat()

    companion object {
        /** 16 kHz — the speech standard; every threshold below assumes it. */
        const val SAMPLE_RATE = 16000

        /** Samples per readChunk() call = 100 ms of audio. */
        private const val READ_CHUNK = 1600

        /** 16-bit full scale: the dB reference (0 dBFS). */
        private const val FULL_SCALE = 32768f

        /** dB value used when there is effectively no signal. */
        private const val FLOOR_DB = -96f

        /** Default software mic-gain; whisper band sits ~12–18 dB below
         *  normal speech, so 2x (+6 dB) recovers most of it without
         *  amplifying room noise too aggressively. */
        private const val DEFAULT_GAIN = 2.0f

        /** Hard ceiling on [gain]: above 4x the mic's own electronic noise
         *  grows faster than the voice. */
        private const val MAX_GAIN = 4.0f

        /**
         * Tanh soft-clip point. Peaks approaching ±30000 get rounded by the
         * tanh curve instead of hard-clipping at ±32767 — leaves ~0.75 dB of
         * headroom so a loud laugh right after a whisper doesn't distort.
         */
        private const val SOFT_CLIP = 30000.0

        /**
         * RMS target for normalisation (~6000 ≈ −14.7 dBFS): loud enough for
         * recognisers, quiet enough that residual peaks stay clear of the
         * limiter. Whispers (RMS ~100–250) get lifted here; speech already
         * above it is left at scale 1.0 (the anti-blowout ceiling).
         */
        private const val TARGET_RMS = 6000f

        /** Below this RMS a chunk is treated as silence/noise and is never
         *  normalised upward — lifting it would just amplify hiss. */
        private const val NOISE_FLOOR_RMS = 40f

        /** Safety cap on the normalisation scale factor: a freak faint chunk
         *  can't explode into amplified noise. */
        private const val MAX_NORMALISE_SCALE = 16f

        /**
         * Whisper band upper edge (RMS ≈ −42 dBFS): below this but above the
         * noise floor counts as a whisper. Sits deliberately BELOW the quiet
         * energy gate (120) so a whisper both WAKES the gate and gets
         * classified as a whisper.
         */
        const val WHISPER_RMS_THRESHOLD = 250f

        /**
         * Energy gate, NORMAL mode (RMS ≈ −33 dBFS): speech must be at least
         * this loud to start an utterance. High enough that keyboard/AC
         * noise rarely trips it.
         */
        const val NORMAL_ENERGY_THRESHOLD = 350f

        /**
         * Energy gate, QUIET mode (RMS ≈ −49 dBFS): ~9 dB more sensitive, so
         * whispers pass. TRADE-OFF: steady background noise sits around this
         * level too, so noisy rooms will false-wake more often. Quiet rooms:
         * pure win.
         */
        const val QUIET_ENERGY_THRESHOLD = 120f

        /** End-of-utterance pause before we decide they finished, normal mode. */
        private const val SILENCE_TIMEOUT_NORMAL_MS = 800L

        /**
         * Same, quiet mode: LONGER, because whispers have longer natural
         * pauses between words — a short timeout would chop one whispered
         * sentence into two half-utterances.
         */
        private const val SILENCE_TIMEOUT_QUIET_MS = 1500L

        private const val PREFS_NAME = "quiet_voice_mode"
        private const val PREF_KEY_QUIET_MODE = "enabled"
    }
}

/**
 * Whisper verdict for one chunk of RAW input, with the measurements kept
 * beside the processed bytes so the parent never has to recompute them.
 */
private data class ProcessedChunk(
    val pcm: ShortArray,
    val inputRms: Float,
    val whisper: Boolean,
)

/**
 * Parent seam: push-style audio feed. [pcm] is PROCESSED audio (gain +
 * limiter + normalisation — recogniser-ready), [rmsDb] is the RAW mic level
 * in dBFS for meters/threshold tuning, [isWhisper] is the raw-input whisper
 * verdict for this chunk.
 */
interface WhisperListener {
    fun onAudioChunk(pcm: ShortArray, rmsDb: Float, isWhisper: Boolean)
}

/**
 * Parent seam: one finished voice command.
 *
 * @property pcm PROCESSED audio (gain + soft limiter + normalisation), ready
 * to feed a recogniser or the voice-lock MFCC pipeline.
 * @property wasWhisper majority vote over the utterance's speech chunks:
 * TRUE means Kamran whispered → the parent MUST reply softly (lower the
 * TTS/stream volume for this one reply — see VoiceOutput — instead of the
 * usual full-volume boost). "If he whispers, Baloch whispers back."
 * @property rmsDb mean RAW input level of the utterance in dBFS (tuning aid).
 */
data class Utterance(
    val pcm: ShortArray,
    val wasWhisper: Boolean,
    val rmsDb: Float,
)
