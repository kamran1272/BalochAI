package com.baloch.ai.voice

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.floor
import kotlin.math.log10
import kotlin.math.pow
import kotlin.math.sqrt

/**
 * VOICE LOCK — speaker authentication. Baloch obeys ONLY Kamran's voice.
 *
 * THE IDEA, IN PLAIN WORDS (for Kamran): everyone's voice colours sound
 * differently — your vocal cords, throat and mouth shape leave a fingerprint
 * on the *tone* of each sound. This manager measures that tone with a
 * technique called MFCC (the same one real voice assistants use), averages
 * it over 3 enrollment recordings of "Hey Baloch, it is me", and stores the
 * average as your "voiceprint". Later, every voice command is measured the
 * same way and compared — if it is far from your voiceprint, Baloch refuses
 * to act, no matter what the words say.
 *
 * HONEST LIMITS (Kamran's order — say it straight):
 *  - This reliably rejects CASUAL STRANGERS (a friend picking up the phone
 *    and talking will not match your voiceprint).
 *  - It is NOT spoof-proof: a clear RECORDING of your voice played back
 *    through a good speaker CAN fool it. There is no liveness detection
 *    here (that needs special hardware or ML models).
 *  - So the PIN (PinManager) + optional biometric app lock are the HARD
 *    guarantees. Voice Lock is the convenient front door, the PIN is the
 *    deadbolt.
 *
 * Everything runs ON-DEVICE: no cloud, no network, no downloaded ML
 * models — just arithmetic on the audio samples (pure Kotlin, no DSP
 * libraries).
 */
class VoiceLockManager(private val appContext: Context) {

    // ------------------------------------------------------------------
    // Public API
    // ------------------------------------------------------------------

    /**
     * Records ONE enrollment sample (~2.5 s) and returns its 26-dim voice
     * vector, or null when the mic failed or no speech was detected (the UI
     * should ask Kamran to try again — never store a bad sample).
     */
    suspend fun enrollSample(): FloatArray? = withContext(Dispatchers.IO) {
        val capture = UtteranceCapture()
        if (!capture.start()) return@withContext null // mic unavailable → null, UI falls back to PIN
        delay(ENROLL_MS)
        val audio = capture.stop() ?: return@withContext null
        vectorFromAudio(audio)
    }

    /**
     * Full enrollment: 3 samples → mean voiceprint + per-dimension variance
     * → stored in DataStore. Returns false if ANY sample failed (partial
     * enrollment is worse than none — it would weaken the voiceprint).
     */
    suspend fun enroll(context: Context): Boolean = withContext(Dispatchers.IO) {
        val samples = ArrayList<FloatArray>(ENROLL_SAMPLE_COUNT)
        repeat(ENROLL_SAMPLE_COUNT) {
            samples.add(enrollSample() ?: return@withContext false)
        }
        // The voiceprint = average of the 3 sample vectors, then normalised.
        val mean = FloatArray(VECTOR_DIM) { d ->
            samples.sumOf { it[d].toDouble() }.toFloat() / ENROLL_SAMPLE_COUNT
        }
        // Per-dimension variance tells the FUTURE how consistent Kamran was
        // across samples (stored for diagnostics/tuning, not used in verify).
        val variance = FloatArray(VECTOR_DIM) { d ->
            val m = mean[d].toDouble()
            (samples.sumOf { val diff = it[d] - m; diff * diff } / ENROLL_SAMPLE_COUNT).toFloat()
        }
        l2NormalizeInPlace(mean)
        context.voiceLockStore.edit { prefs ->
            prefs[KEY_VOICEPRINT] = mean.joinToString(",")
            prefs[KEY_VARIANCE] = variance.joinToString(",")
            prefs[KEY_ENROLLED] = true
        }
        true
    }

    /**
     * Checks a captured utterance against the stored voiceprint.
     * Returns true only when the cosine distance is within threshold —
     * i.e. the speaker is (almost certainly) Kamran.
     */
    suspend fun verify(context: Context, audio: ShortArray): Boolean =
        withContext(Dispatchers.IO) {
            if (!isEnrolled(context)) return@withContext false
            val stored = loadVoiceprint(context) ?: return@withContext false
            val sample = vectorFromAudio(audio) ?: return@withContext false
            cosineDistance(sample, stored) <= VERIFY_THRESHOLD
        }

    /** Blocking read (small DataStore read on IO) for UI hot paths. */
    fun isEnrolled(context: Context): Boolean = runBlocking(Dispatchers.IO) {
        context.voiceLockStore.data.map { it[KEY_ENROLLED] == true }.first()
    }

    /** Blocking read (small DataStore read on IO) for UI hot paths. */
    fun isEnabled(context: Context): Boolean = runBlocking(Dispatchers.IO) {
        context.voiceLockStore.data.map { it[KEY_ENABLED] == true }.first()
    }

    suspend fun setEnabled(context: Context, enabled: Boolean) {
        withContext(Dispatchers.IO) {
            context.voiceLockStore.edit { it[KEY_ENABLED] = enabled }
        }
    }

    /**
     * May the Voice Lock be turned OFF right now?
     *
     * POLICY (Kamran's order): disabling is a security-sensitive act, so it
     * needs proof of identity — EITHER the voiceprint verifies on a fresh
     * utterance OR the PIN is entered. This function covers the VOICE half
     * only: it captures ~2.5 s and verifies it. The PIN half needs the PIN
     * typed into the UI, so the PARENT collects it and calls
     * PinManager.checkPin() itself — the parent enforces voice-OR-PIN before
     * it ever calls setEnabled(false). This split is deliberate: a manager
     * that silently accepted "no PIN available" would be a bypass hole.
     */
    suspend fun canDisable(context: Context): Boolean {
        if (!isEnrolled(context) || !isEnabled(context)) return true // nothing to protect
        val capture = UtteranceCapture()
        if (!capture.start()) return false // mic dead → voice path fails; UI must offer the PIN path
        delay(ENROLL_MS)
        val audio = capture.stop() ?: return false
        return verify(context, audio)
    }

    // ------------------------------------------------------------------
    // Voiceprint storage
    // ------------------------------------------------------------------

    private suspend fun loadVoiceprint(context: Context): FloatArray? =
        withContext(Dispatchers.IO) {
            val raw = context.voiceLockStore.data
                .map { it[KEY_VOICEPRINT] }
                .first() ?: return@withContext null
            val parts = raw.split(",").mapNotNull { it.toFloatOrNull() }
            if (parts.size != VECTOR_DIM) return@withContext null // corrupted → refuse, never guess
            parts.toFloatArray()
        }

    // ------------------------------------------------------------------
    // The MFCC pipeline — each stage is a small commented function.
    //
    // STAGE 1 — pre-emphasis: speech energy falls off at high frequencies,
    // so we boost them slightly. y[n] = x[n] − 0.97·x[n−1].
    // Think of it like turning up the treble so consonants ("s", "t")
    // don't get drowned by vowels.
    // ------------------------------------------------------------------

    private fun preEmphasize(samples: DoubleArray): DoubleArray {
        val out = DoubleArray(samples.size)
        out[0] = samples[0]
        for (n in 1 until samples.size) {
            out[n] = samples[n] - PRE_EMPHASIS * samples[n - 1]
        }
        return out
    }

    // ------------------------------------------------------------------
    // STAGE 2 — framing + Hamming window: speech changes over time, so we
    // chop it into short 25 ms slices (400 samples), stepping 10 ms (160
    // samples) so slices overlap. Each slice is treated as one "snapshot"
    // of the voice. The Hamming window fades each slice's edges to zero —
    // without it, the FFT (next stage) sees fake sharp edges and invents
    // frequencies that aren't there.
    // ------------------------------------------------------------------

    private fun frameSignal(samples: DoubleArray): List<DoubleArray> {
        val frames = ArrayList<DoubleArray>()
        var start = 0
        while (start + FRAME_LEN <= samples.size) {
            val frame = DoubleArray(FRAME_LEN)
            for (n in 0 until FRAME_LEN) {
                val hamming = 0.54 - 0.46 * cos(2.0 * PI * n / (FRAME_LEN - 1))
                frame[n] = samples[start + n] * hamming
            }
            frames.add(frame)
            start += HOP
        }
        return frames
    }

    /** Raw energy of a frame (before windowing) — used to find speech vs silence. */
    private fun frameEnergy(raw: DoubleArray, start: Int): Double {
        var e = 0.0
        for (n in 0 until FRAME_LEN) {
            val s = raw[start + n]
            e += s * s
        }
        return e / FRAME_LEN
    }

    // ------------------------------------------------------------------
    // STAGE 3 — 512-point FFT: converts each time-slice into its frequency
    // content ("how much bass, how much treble"). Implemented by hand as a
    // radix-2 Cooley–Tukey transform:
    //   1) bit-reversal reorder (puts samples in the order the butterflies need)
    //   2) log2(512) = 9 "butterfly" stages, each combining pairs with
    //      rotating twiddle factors e^(−2πi·k/len).
    // We only need the power (magnitude²) of the first 257 bins — the rest
    // mirror them for real-valued input.
    // ------------------------------------------------------------------

    private fun fft(re: DoubleArray, im: DoubleArray) {
        val n = re.size
        require(n == im.size && n and (n - 1) == 0) { "FFT size must be a power of two" }

        // Pass 1: bit-reversal permutation.
        var j = 0
        for (i in 1 until n) {
            var bit = n shr 1
            while (j and bit != 0) {
                j = j xor bit
                bit = bit shr 1
            }
            j = j xor bit
            if (i < j) {
                val tr = re[i]; re[i] = re[j]; re[j] = tr
                val ti = im[i]; im[i] = im[j]; im[j] = ti
            }
        }

        // Pass 2: butterfly stages, block size doubling 2 → 4 → … → n.
        var len = 2
        while (len <= n) {
            val angle = -2.0 * PI / len // negative sign = forward transform
            val wLenRe = cos(angle)
            val wLenIm = kotlin.math.sin(angle)
            var i = 0
            while (i < n) {
                var wRe = 1.0
                var wIm = 0.0
                for (k in 0 until len / 2) {
                    // u = upper sample, v = lower sample × current twiddle
                    val uRe = re[i + k]
                    val uIm = im[i + k]
                    val vRe = re[i + k + len / 2] * wRe - im[i + k + len / 2] * wIm
                    val vIm = re[i + k + len / 2] * wIm + im[i + k + len / 2] * wRe
                    re[i + k] = uRe + vRe
                    im[i + k] = uIm + vIm
                    re[i + k + len / 2] = uRe - vRe
                    im[i + k + len / 2] = uIm - vIm
                    // Advance the twiddle: w *= wLen (rotation by one step).
                    val nextWRe = wRe * wLenRe - wIm * wLenIm
                    val nextWIm = wRe * wLenIm + wIm * wLenRe
                    wRe = nextWRe
                    wIm = nextWIm
                }
                i += len
            }
            len = len shl 1
        }
    }

    private fun powerSpectrum(windowedFrame: DoubleArray): DoubleArray {
        val re = DoubleArray(N_FFT)
        val im = DoubleArray(N_FFT)
        windowedFrame.copyInto(re) // zero-padded: 400 samples in a 512 slot
        fft(re, im)
        val nBins = N_FFT / 2 + 1
        return DoubleArray(nBins) { k -> (re[k] * re[k] + im[k] * im[k]) / N_FFT }
    }

    // ------------------------------------------------------------------
    // STAGE 4 — mel filterbank: human hearing resolves low frequencies
    // finely and high ones coarsely. We mimic that with 26 triangular
    // filters spaced on the "mel" scale (perceptual pitch) between
    // 300 Hz and 8000 Hz, then take the LOG of each filter's energy —
    // log matches how we perceive loudness (twice the energy ≠ twice as
    // loud to a human ear).
    // ------------------------------------------------------------------

    private fun hzToMel(hz: Double): Double = 2595.0 * log10(1.0 + hz / 700.0)
    private fun melToHz(mel: Double): Double = 700.0 * (10.0.pow(mel / 2595.0) - 1.0)

    /** 26 triangular filters × 257 FFT bins, built once and reused. */
    private val melBank: Array<DoubleArray> by lazy {
        val nBins = N_FFT / 2 + 1
        val melLow = hzToMel(MEL_LOW_HZ)
        val melHigh = hzToMel(MEL_HIGH_HZ)
        // 26 filters need 28 boundary points, evenly spaced in mel space.
        val points = IntArray(N_MELS + 2) { i ->
            val hz = melToHz(melLow + i * (melHigh - melLow) / (N_MELS + 1))
            floor(hz / SAMPLE_RATE * N_FFT).toInt().coerceIn(0, nBins - 1)
        }
        Array(N_MELS) { m ->
            val filt = DoubleArray(nBins)
            val left = points[m]
            val center = points[m + 1]
            val right = points[m + 2]
            if (center > left) {
                for (k in left until center) filt[k] = (k - left).toDouble() / (center - left)
            }
            if (right > center) {
                for (k in center..right) filt[k] = (right - k).toDouble() / (right - center)
            }
            filt
        }
    }

    private fun logMelEnergies(power: DoubleArray): DoubleArray {
        return DoubleArray(N_MELS) { m ->
            var energy = 0.0
            val filt = melBank[m]
            for (k in power.indices) energy += power[k] * filt[k]
            kotlin.math.ln(energy.coerceAtLeast(FLOOR)) // log loudness; floor avoids ln(0)
        }
    }

    // ------------------------------------------------------------------
    // STAGE 5 — DCT: the 26 log-energies overlap (neighbouring filters see
    // similar sound). The Discrete Cosine Transform de-correlates them into
    // 13 compact coefficients — the actual "MFCCs". Lower coefficients hold
    // the voice's overall tone (the identity part); higher ones hold fine
    // detail we throw away. This 13-number summary IS the frame's voice
    // fingerprint.
    // ------------------------------------------------------------------

    private fun dct(logEnergies: DoubleArray): DoubleArray {
        val out = DoubleArray(N_MFCC)
        for (k in 0 until N_MFCC) {
            var sum = 0.0
            for (n in logEnergies.indices) {
                sum += logEnergies[n] * cos(PI * k * (2 * n + 1) / (2.0 * N_MELS))
            }
            out[k] = sum
        }
        return out
    }

    // ------------------------------------------------------------------
    // Utterance vector: mean of the MFCCs over VOICED frames + mean of
    // their frame-to-frame deltas, L2-normalised → 26 dimensions.
    //
    // VOICED FRAMES (energy gate): silence and breath carry no identity,
    // so we keep only frames louder than 10% of the utterance's loudest
    // frame. DELTAS: how the tone MOVES between frames (your speech rhythm)
    // — appending them doubles the vector to 26 and makes different
    // speakers easier to tell apart.
    // ------------------------------------------------------------------

    internal fun vectorFromAudio(pcm: ShortArray): FloatArray? {
        if (pcm.size < FRAME_LEN) return null
        // Normalise 16-bit ints to [-1, 1] doubles for stable math.
        val signal = DoubleArray(pcm.size) { pcm[it] / 32768.0 }
        val emphasised = preEmphasize(signal)

        // Frame energies first (on the pre-emphasised signal), to find speech.
        val nFrames = (emphasised.size - FRAME_LEN) / HOP + 1
        val energies = DoubleArray(nFrames) { f -> frameEnergy(emphasised, f * HOP) }
        val maxEnergy = energies.maxOrNull() ?: return null
        if (maxEnergy <= 0.0) return null // pure digital silence

        val mfccs = frameSignal(emphasised).map { frame ->
            dct(logMelEnergies(powerSpectrum(frame)))
        }
        // Voiced = louder than 10% of the loudest frame.
        val voiced = mfccs.indices.filter { energies[it] >= maxEnergy * VOICED_RATIO }
        if (voiced.size < MIN_VOICED_FRAMES) return null // no real speech → caller retries

        val staticMean = DoubleArray(N_MFCC) { d ->
            voiced.sumOf { mfccs[it][d] } / voiced.size
        }
        // Delta of frame t = how the tone changed since the previous frame.
        val deltaMean = DoubleArray(N_MFCC) { d ->
            voiced.sumOf { t ->
                val prev = mfccs[(t - 1).coerceAtLeast(0)][d]
                mfccs[t][d] - prev
            } / voiced.size
        }

        val vec = FloatArray(VECTOR_DIM)
        for (d in 0 until N_MFCC) {
            vec[d] = staticMean[d].toFloat()
            vec[N_MFCC + d] = deltaMean[d].toFloat()
        }
        l2NormalizeInPlace(vec)
        return vec
    }

    private fun l2NormalizeInPlace(v: FloatArray) {
        var norm = 0.0
        for (x in v) norm += x * x
        norm = sqrt(norm)
        if (norm > 0.0) {
            for (i in v.indices) v[i] = (v[i] / norm).toFloat()
        }
    }

    /**
     * Cosine DISTANCE between two normalised vectors = 1 − cosine similarity.
     * 0.0 = identical direction, 1.0 = unrelated, 2.0 = opposite.
     */
    private fun cosineDistance(a: FloatArray, b: FloatArray): Double {
        var dot = 0.0
        for (i in a.indices) dot += a[i] * b[i]
        return 1.0 - dot // valid because both vectors are L2-normalised
    }

    companion object {
        /** Fixed phrase every enrollment sample and verification uses. */
        const val ENROLL_PHRASE = "Hey Baloch, it is me"

        /**
         * Accept when cosine distance ≤ 0.30 (similarity ≥ 0.70).
         *
         * THE TRADE-OFF (FAR vs FRR), honestly:
         *  - LOWER threshold (e.g. 0.20) = STRICTER: fewer strangers slip
         *    through (low False Accept Rate) but Kamran gets rejected more
         *    when ill, tired, or in noise (high False Reject Rate) — and
         *    every false reject costs him a PIN entry.
         *  - HIGHER threshold (e.g. 0.40) = LENIENT: Kamran almost never
         *    gets falsely rejected, but a similar-sounding stranger has a
         *    better chance of passing.
         *  0.30 is a pragmatic starting point for TEXT-DEPENDENT matching
         *  (same fixed phrase every time, which keeps same-speaker scores
         *  high). If Kamran reports false rejects on-device, raise toward
         *  0.35; if testing shows near-misses passing, lower toward 0.25.
         *  Tune with real recordings, not theory.
         */
        const val VERIFY_THRESHOLD = 0.30

        // --- DSP constants (the MFCC recipe) ---
        const val SAMPLE_RATE = 16000       // samples per second
        const val FRAME_LEN = 400           // 25 ms window @16 kHz
        const val HOP = 160                 // 10 ms step @16 kHz
        const val N_FFT = 512               // power of two ≥ FRAME_LEN
        const val N_MELS = 26               // mel filters
        const val N_MFCC = 13               // kept cepstral coefficients
        const val VECTOR_DIM = 26           // 13 static means + 13 delta means
        const val MEL_LOW_HZ = 300.0        // ignore rumble below this
        const val MEL_HIGH_HZ = 8000.0      // Nyquist at 16 kHz

        private const val PRE_EMPHASIS = 0.97
        private const val FLOOR = 1e-10      // avoids ln(0)
        private const val VOICED_RATIO = 0.10 // energy gate: 10% of max frame energy
        private const val MIN_VOICED_FRAMES = 8 // fewer → "no speech detected"

        // --- Enrollment capture ---
        const val ENROLL_SAMPLE_COUNT = 3
        const val ENROLL_MS = 2500L         // ~2.5 s per sample

        // --- DataStore keys (unique inside the "voice_lock" store) ---
        private val KEY_VOICEPRINT = stringPreferencesKey("voiceprint")
        private val KEY_VARIANCE = stringPreferencesKey("voiceprint_variance")
        private val KEY_ENROLLED = booleanPreferencesKey("enrolled")
        private val KEY_ENABLED = booleanPreferencesKey("enabled")
        /** Optional biometric app-lock toggle — read/written by the parent, kept here so all lock state lives in one store. */
        val KEY_BIOMETRIC_LOCK = booleanPreferencesKey("biometric_lock")
    }
}

/** Shared DataStore for ALL voice-lock state (voiceprint, PIN hash, toggles). */
private val Context.voiceLockStore: DataStore<Preferences> by
    preferencesDataStore(name = "voice_lock")
