package com.baloch.ai.life

import android.content.Context
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import com.baloch.ai.brain.BalochLanguage
import com.baloch.ai.brain.L3
import com.baloch.ai.settings.isJarvisMode
import org.json.JSONObject
import java.io.File
import java.net.HttpURLConnection
import java.net.URL
import java.util.Calendar
import java.util.TimeZone
import java.util.concurrent.TimeUnit
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.sin

/**
 * Prayer times + Qibla.
 *
 * Times come from the Aladhan API (timingsByCity), cached once per day in
 * app-private storage so the feature keeps working offline. Next-prayer
 * countdown is computed locally from the cache.
 *
 * Alerts fire through [PrayerAlertWorker] (WorkManager, every 30 min),
 * JARVIS-gated like every other proactive worker: in MANUAL mode the
 * worker stands down silently.
 *
 * Voice: "namaz ka time", "next prayer", "qibla kidhar hai".
 */

// ------------------------------------------------------------------
// Data
// ------------------------------------------------------------------

/** One prayer: name + trigger time as millis since epoch (Asia/Karachi). */
data class PrayerTiming(val name: String, val atMillis: Long)

/** The five daily prayers in display order. */
val PRAYER_ORDER = listOf("Fajr", "Dhuhr", "Asr", "Maghrib", "Isha")

/** Kaaba coordinates. */
const val KAABA_LAT = 21.4225
const val KAABA_LON = 39.8262

/** Device default when no location fix is available (Lahore, Kamran's city). */
const val DEFAULT_LAT = 31.52
const val DEFAULT_LON = 74.36

// ------------------------------------------------------------------
// Qibla bearing
// ------------------------------------------------------------------

/**
 * Initial great-circle bearing from ([lat1],[lon1]) to ([lat2],[lon2]),
 * in degrees clockwise from true north (0–360).
 */
fun qiblaBearing(lat1: Double, lon1: Double, lat2: Double, lon2: Double): Double {
    val phi1 = Math.toRadians(lat1)
    val phi2 = Math.toRadians(lat2)
    val dLon = Math.toRadians(lon2 - lon1)
    val y = sin(dLon) * cos(phi2)
    val x = cos(phi1) * sin(phi2) - sin(phi1) * cos(phi2) * cos(dLon)
    return (Math.toDegrees(atan2(y, x)) + 360.0) % 360.0
}

// ------------------------------------------------------------------
// Live compass (rotation-vector sensor)
// ------------------------------------------------------------------

/**
 * Live compass feeding the Qibla dial. Uses the rotation-vector sensor
 * (no magnetometer calibration dance). Call [start] when the compass
 * screen is visible, [stop] when it leaves — battery-friendly.
 *
 * [onAzimuth] receives the device azimuth in degrees (0 = north), so the
 * UI can rotate the needle by (qiblaBearing − azimuth).
 */
class QiblaCompass(
    context: Context,
    private val onAzimuth: (Float) -> Unit,
) : SensorEventListener {

    private val sensorManager =
        context.getSystemService(Context.SENSOR_SERVICE) as SensorManager
    private val rotationVector: Sensor? =
        sensorManager.getDefaultSensor(Sensor.TYPE_ROTATION_VECTOR)
    private var running = false

    private val rotMatrix = FloatArray(9)
    private val remapped = FloatArray(9)
    private val orientation = FloatArray(3)

    fun hasSensor(): Boolean = rotationVector != null

    fun start() {
        if (running || rotationVector == null) return
        sensorManager.registerListener(
            this, rotationVector, SensorManager.SENSOR_DELAY_UI
        )
        running = true
    }

    fun stop() {
        if (!running) return
        sensorManager.unregisterListener(this)
        running = false
    }

    override fun onSensorChanged(event: SensorEvent) {
        if (event.sensor.type != Sensor.TYPE_ROTATION_VECTOR) return
        SensorManager.getRotationMatrixFromVector(rotMatrix, event.values)
        // Remap for portrait-held phones.
        SensorManager.remapCoordinateSystem(
            rotMatrix,
            SensorManager.AXIS_X,
            SensorManager.AXIS_Z,
            remapped,
        )
        SensorManager.getOrientation(remapped, orientation)
        val azimuthDeg = Math.toDegrees(orientation[0].toDouble()).toFloat()
        onAzimuth((azimuthDeg + 360f) % 360f)
    }

    override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) = Unit
}

// ------------------------------------------------------------------
// Aladhan fetch + daily cache
// ------------------------------------------------------------------

object PrayerTimesApi {

    private const val CACHE_FILE = "prayer_times_cache.json"
    private const val TIMEOUT_MS = 8000
    private val KARACHI_TZ: TimeZone = TimeZone.getTimeZone("Asia/Karachi")

    /**
     * Today's timings. Returns the daily cache when it is fresh; fetches
     * from Aladhan only when the cache is missing or from another day.
     * Never throws — returns an empty list when offline and uncached.
     */
    fun todaysTimings(context: Context): List<PrayerTiming> {
        val cached = readCache(context)
        if (cached != null && isToday(cached.first)) return cached.second
        val fresh = fetch(context)
        return fresh ?: cached?.second.orEmpty()
    }

    /** Next prayer at or after [nowMillis], or tomorrow's Fajr. */
    fun nextPrayer(timings: List<PrayerTiming>, nowMillis: Long): PrayerTiming? {
        if (timings.isEmpty()) return null
        val upcoming = timings.filter { it.atMillis >= nowMillis - 60_000L }
        return upcoming.minByOrNull { it.atMillis }
    }

    /** Friendly "2h 14m" countdown to [prayer] from [nowMillis]. */
    fun countdownText(prayer: PrayerTiming, nowMillis: Long, lang: BalochLanguage): String {
        val diff = (prayer.atMillis - nowMillis).coerceAtLeast(0L)
        val hours = diff / 3_600_000L
        val mins = (diff % 3_600_000L) / 60_000L
        return when (lang) {
            BalochLanguage.ENGLISH ->
                if (hours > 0) "${prayer.name} in ${hours}h ${mins}m, sir"
                else "${prayer.name} in ${mins}m, sir"
            BalochLanguage.URDU ->
                if (hours > 0) "سر، ${prayer.name} میں ${hours} گھنٹے ${mins} منٹ باقی"
                else "سر، ${prayer.name} میں ${mins} منٹ باقی"
            BalochLanguage.HINDI ->
                if (hours > 0) "सर, ${prayer.name} में ${hours} घंटे ${mins} मिनट बाकी"
                else "सर, ${prayer.name} में ${mins} मिनट बाकी"
        }
    }

    // ---------------- internals ----------------

    private fun cacheFile(context: Context) = File(context.filesDir, CACHE_FILE)

    /** Pair of (cached-day key, timings). */
    private fun readCache(context: Context): Pair<String, List<PrayerTiming>>? {
        val f = cacheFile(context)
        if (!f.exists()) return null
        return try {
            val root = JSONObject(f.readText())
            val day = root.optString("day", "")
            val arr = root.optJSONArray("timings") ?: return null
            val list = List(arr.length()) { i ->
                val o = arr.getJSONObject(i)
                PrayerTiming(o.getString("name"), o.getLong("at"))
            }
            day to list
        } catch (_: Exception) {
            null
        }
    }

    private fun isToday(dayKey: String): Boolean {
        val cal = Calendar.getInstance(KARACHI_TZ)
        val today = "%04d-%02d-%02d".format(
            cal.get(Calendar.YEAR),
            cal.get(Calendar.MONTH) + 1,
            cal.get(Calendar.DAY_OF_MONTH),
        )
        return dayKey == today
    }

    private fun fetch(context: Context): List<PrayerTiming>? {
        return try {
            val url = URL(
                "https://api.aladhan.com/v1/timingsByCity" +
                    "?city=Lahore&country=Pakistan&method=2"
            )
            val conn = (url.openConnection() as HttpURLConnection).apply {
                connectTimeout = TIMEOUT_MS
                readTimeout = TIMEOUT_MS
                requestMethod = "GET"
            }
            if (conn.responseCode != HttpURLConnection.HTTP_OK) return null
            val body = conn.inputStream.bufferedReader().use { it.readText() }
            val timingsObj = JSONObject(body)
                .getJSONObject("data")
                .getJSONObject("timings")
            val cal = Calendar.getInstance(KARACHI_TZ)
            val dayKey = "%04d-%02d-%02d".format(
                cal.get(Calendar.YEAR),
                cal.get(Calendar.MONTH) + 1,
                cal.get(Calendar.DAY_OF_MONTH),
            )
            val list = PRAYER_ORDER.mapNotNull { name ->
                val raw = timingsObj.optString(name, "").substringBefore(" ").trim()
                val (h, m) = raw.split(":").mapNotNull { it.toIntOrNull() }
                    .takeIf { it.size == 2 } ?: return@mapNotNull null
                val c = Calendar.getInstance(KARACHI_TZ).apply {
                    set(Calendar.HOUR_OF_DAY, h)
                    set(Calendar.MINUTE, m)
                    set(Calendar.SECOND, 0)
                    set(Calendar.MILLISECOND, 0)
                }
                PrayerTiming(name, c.timeInMillis)
            }
            if (list.size == PRAYER_ORDER.size) writeCache(context, dayKey, list)
            list.ifEmpty { null }
        } catch (_: Exception) {
            null // offline or API hiccup — the cache (if any) is used instead
        }
    }

    private fun writeCache(context: Context, dayKey: String, list: List<PrayerTiming>) {
        try {
            val arr = org.json.JSONArray()
            list.forEach {
                arr.put(JSONObject().put("name", it.name).put("at", it.atMillis))
            }
            val root = JSONObject().put("day", dayKey).put("timings", arr)
            val tmp = File(context.filesDir, "$CACHE_FILE.tmp")
            tmp.writeText(root.toString())
            if (!tmp.renameTo(cacheFile(context))) cacheFile(context).writeText(root.toString())
        } catch (_: Exception) {
            // Cache failure must never break the feature.
        }
    }
}

// ------------------------------------------------------------------
// Alert worker (JARVIS-gated)
// ------------------------------------------------------------------

private val PRAYER_ALERT_TITLE = L3(
    en = "Prayer time, sir 🕌",
    ur = "نماز کا وقت، سر 🕌",
    hi = "नमाज़ का समय, सर 🕌",
)

private val PRAYER_ALERT_BODY = L3(
    en = "It is {name} time, sir.",
    ur = "سر، {name} کا وقت ہو گیا ہے۔",
    hi = "सर, {name} का समय हो गया है।",
)

/**
 * Runs every 30 minutes. If a prayer time falls inside the current
 * 30-minute window and hasn't been announced yet today, it notifies.
 * MANUAL mode → stands down silently (dual-mode contract).
 */
class PrayerAlertWorker(
    appContext: Context,
    params: WorkerParameters,
) : CoroutineWorker(appContext, params) {

    override suspend fun doWork(): Result {
        if (!isJarvisMode(applicationContext)) return Result.success()
        if (!LifeSettings(applicationContext).prayerAlertsNow()) return Result.success()

        val timings = PrayerTimesApi.todaysTimings(applicationContext)
        if (timings.isEmpty()) return Result.success()

        val now = System.currentTimeMillis()
        val due = timings.firstOrNull { it.atMillis in (now - 30 * 60_000L)..now }
            ?: return Result.success()

        val prefs = applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        val cal = Calendar.getInstance(TimeZone.getTimeZone("Asia/Karachi"))
        val dayKey = "%04d-%02d-%02d".format(
            cal.get(Calendar.YEAR), cal.get(Calendar.MONTH) + 1, cal.get(Calendar.DAY_OF_MONTH)
        )
        val announcedKey = "announced_${dayKey}_${due.name}"
        if (prefs.getBoolean(announcedKey, false)) return Result.success()

        val lang = activeLanguage(applicationContext)
        LifeNotify.send(
            applicationContext,
            LifeNotify.CH_PRAYER, "Prayer alerts",
            1000 + PRAYER_ORDER.indexOf(due.name),
            PRAYER_ALERT_TITLE.pick(lang),
            PRAYER_ALERT_BODY.format(lang, "name" to due.name),
        )
        prefs.edit().putBoolean(announcedKey, true).apply()
        return Result.success()
    }

    companion object {
        private const val PREFS = "baloch_life_prayer"
        private const val WORK_NAME = "baloch_prayer_alerts"

        fun schedule(context: Context) {
            val req = PeriodicWorkRequestBuilder<PrayerAlertWorker>(30, TimeUnit.MINUTES)
                .build()
            WorkManager.getInstance(context).enqueueUniquePeriodicWork(
                WORK_NAME,
                ExistingPeriodicWorkPolicy.UPDATE,
                req,
            )
        }

        fun cancel(context: Context) {
            WorkManager.getInstance(context).cancelUniqueWork(WORK_NAME)
        }
    }
}

/** Shared language reader (matches BatteryGuardianWorker's convention). */
internal fun activeLanguage(context: Context): BalochLanguage =
    BalochLanguage.fromCode(
        context.getSharedPreferences("baloch_prefs", Context.MODE_PRIVATE)
            .getString("language", BalochLanguage.ENGLISH.code)
            ?: BalochLanguage.ENGLISH.code
    )
