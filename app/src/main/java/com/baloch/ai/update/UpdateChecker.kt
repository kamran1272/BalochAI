package com.baloch.ai.update

import android.app.DownloadManager
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.Settings
import androidx.core.content.FileProvider
import com.baloch.ai.brain.BalochLanguage
import com.baloch.ai.brain.L3
import com.baloch.ai.settings.LanguageManager
import java.io.File
import java.net.HttpURLConnection
import java.net.URL
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject

/**
 * One GitHub release as Baloch understands it.
 *
 * RELEASE-NAME CONVENTION (factory contract — see UPDATES-DOCS-ADD.md):
 * the release **name** must read `v1.0.0 (code 3)` — a semver name plus
 * the integer versionCode in parentheses. The updater parses both from
 * there; the changelog is the release body; the APK is the first `.apk`
 * asset attached to the release.
 */
data class ReleaseInfo(
    val versionName: String,
    val versionCode: Int,
    val changelog: String,
    val apkUrl: String,
    val publishedAt: String,
)

/** Outcome of an update check. The parent maps each to UI + voice. */
sealed interface UpdateResult {
    /** Already on the newest release. */
    data object UpToDate : UpdateResult

    /** A newer release exists — parent asks Kamran before downloading. */
    data class Available(val release: ReleaseInfo) : UpdateResult

    /**
     * No usable internet. Parent shows the Roman-Urdu line:
     * "Internet chahiye, sir — baad mein check kar lena."
     */
    data object Offline : UpdateResult

    /** Anything else (HTTP error, bad JSON, timeout). Never crashes. */
    data class Error(val msg: String) : UpdateResult
}

/**
 * Checks the BalochAI GitHub releases feed for a newer build and, on
 * Kamran's word, downloads the APK and hands it to the system installer.
 *
 * NEVER auto-installs: the OS install prompt is the final gate, and
 * [downloadAndPromptInstall] refuses to fire the install intent until
 * the download completes successfully.
 */
object UpdateChecker {

    private const val LATEST_URL =
        "https://api.github.com/repos/kamran1272/BalochAI/releases/latest"
    private const val CONNECT_TIMEOUT_MS = 8_000
    private const val READ_TIMEOUT_MS = 8_000

    /** Matches the factory's release-name convention: `v1.0.0 (code 3)`. */
    private val NAME_PATTERN = Regex("""v?(\d+\.\d+\.\d+)\s*\(code\s*(\d+)\)""")

    private const val PREFS = "baloch_updates"
    private const val KEY_PENDING_ID = "pending_download_id"
    private const val KEY_PENDING_VERSION = "pending_download_version"
    private const val DOWNLOAD_SUBDIR = "BalochAI"

    /**
     * Hits the GitHub releases API on [Dispatchers.IO].
     * Returns [UpdateResult.Offline] when there is no usable network
     * rather than failing — the parent turns that into the friendly
     * "check later" line instead of an error.
     */
    suspend fun checkForUpdates(ctx: Context): UpdateResult {
        val appCtx = ctx.applicationContext
        if (!hasInternet(appCtx)) return UpdateResult.Offline
        return try {
            val release = withContext(Dispatchers.IO) { fetchLatest(appCtx) }
                ?: return UpdateResult.Error(
                    UpdateStrings.RELEASE_UNREADABLE.pick(langOf(appCtx)),
                )
            if (release.versionCode > APP_VERSION_CODE) {
                UpdateResult.Available(release)
            } else {
                // Equal or older: never suggest a downgrade.
                UpdateResult.UpToDate
            }
        } catch (e: Exception) {
            UpdateResult.Error(e.message?.take(120) ?: "unknown error")
        }
    }

    /**
     * Enqueues the APK in [DownloadManager] under
     * Downloads/BalochAI/ and arms a one-shot completion receiver.
     * When the download finishes OK, the receiver fires the system
     * install prompt via FileProvider — Kamran taps Install himself.
     * NOTHING installs silently, ever.
     *
     * Call only after Kamran confirms (voice "yes" / dialog tap).
     */
    fun downloadAndPromptInstall(ctx: Context, info: ReleaseInfo) {
        val appCtx = ctx.applicationContext
        val fileName = "baloch-v${info.versionName}.apk"
        val request = DownloadManager.Request(Uri.parse(info.apkUrl))
            .setTitle("BalochAI v${info.versionName}")
            .setDescription(
                UpdateStrings.DOWNLOAD_DESC.pick(langOf(appCtx)),
            )
            .setNotificationVisibility(
                DownloadManager.Request.VISIBILITY_VISIBLE_NOTIFY_COMPLETED,
            )
            .setDestinationInExternalPublicDir(
                Environment.DIRECTORY_DOWNLOADS,
                "$DOWNLOAD_SUBDIR/$fileName",
            )
            .setMimeType("application/vnd.android.package-archive")

        val dm = appCtx.getSystemService(Context.DOWNLOAD_SERVICE) as DownloadManager
        val downloadId = dm.enqueue(request)

        appCtx.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit()
            .putLong(KEY_PENDING_ID, downloadId)
            .putString(KEY_PENDING_VERSION, info.versionName)
            .apply()

        val receiver = object : BroadcastReceiver() {
            override fun onReceive(c: Context, intent: Intent) {
                val id = intent.getLongExtra(DownloadManager.EXTRA_DOWNLOAD_ID, -1)
                if (id != downloadId) return
                appCtx.unregisterReceiver(this)
                if (downloadSucceeded(appCtx, downloadId)) {
                    promptInstall(appCtx, fileName)
                }
                // On failure the DownloadManager notification already tells
                // the story; we stay silent rather than nagging.
            }
        }
        val filter = IntentFilter(DownloadManager.ACTION_DOWNLOAD_COMPLETE)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            appCtx.registerReceiver(receiver, filter, Context.RECEIVER_NOT_EXPORTED)
        } else {
            @Suppress("UnspecifiedRegisterReceiverFlag")
            appCtx.registerReceiver(receiver, filter)
        }
    }

    // ------------------------------------------------------------------
    // Internals
    // ------------------------------------------------------------------

    private fun langOf(ctx: Context): BalochLanguage =
        try {
            LanguageManager(ctx).current()
        } catch (_: Exception) {
            BalochLanguage.ENGLISH
        }

    private fun hasInternet(ctx: Context): Boolean = try {
        val cm = ctx.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
        val caps = cm.getNetworkCapabilities(cm.activeNetwork)
        caps?.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET) == true
    } catch (_: Exception) {
        false
    }

    /** GETs the latest-release JSON. Null when the payload is unusable. */
    private fun fetchLatest(ctx: Context): ReleaseInfo? {
        var conn: HttpURLConnection? = null
        return try {
            conn = (URL(LATEST_URL).openConnection() as HttpURLConnection).apply {
                connectTimeout = CONNECT_TIMEOUT_MS
                readTimeout = READ_TIMEOUT_MS
                requestMethod = "GET"
                // GitHub's API 403s requests without a User-Agent.
                setRequestProperty(
                    "User-Agent",
                    "BalochAI-Android/$APP_VERSION_NAME",
                )
                setRequestProperty("Accept", "application/vnd.github+json")
            }
            if (conn.responseCode != HttpURLConnection.HTTP_OK) return null
            val body = conn.inputStream.bufferedReader().use { it.readText() }
            parseRelease(JSONObject(body))
        } finally {
            conn?.disconnect()
        }
    }

    /**
     * Parses name/body/assets per the factory convention.
     * Fallback order for the version: release `name` → `tag_name`.
     * Fallback order for the APK: first `.apk` asset → `.apk` URL found
     * in the body. No APK anywhere → null (not an installable release).
     */
    private fun parseRelease(json: JSONObject): ReleaseInfo? {
        val name = json.optString("name").ifBlank { json.optString("tag_name") }
        val match = NAME_PATTERN.find(name) ?: NAME_PATTERN.find(json.optString("tag_name"))
        val versionName = match?.groupValues?.get(1) ?: return null
        val versionCode = match.groupValues[2].toIntOrNull() ?: return null

        var apkUrl: String? = null
        val assets = json.optJSONArray("assets")
        if (assets != null) {
            for (i in 0 until assets.length()) {
                val asset = assets.optJSONObject(i) ?: continue
                if (asset.optString("name").endsWith(".apk", ignoreCase = true)) {
                    apkUrl = asset.optString("browser_download_url").ifBlank { null }
                    break
                }
            }
        }
        if (apkUrl == null) {
            apkUrl = Regex("""https://\S+\.apk""")
                .find(json.optString("body"))
                ?.value
        }
        if (apkUrl.isNullOrBlank()) return null

        return ReleaseInfo(
            versionName = versionName,
            versionCode = versionCode,
            changelog = json.optString("body").trim().ifBlank { "—" },
            apkUrl = apkUrl,
            publishedAt = json.optString("published_at"),
        )
    }

    private fun downloadSucceeded(ctx: Context, downloadId: Long): Boolean {
        val dm = ctx.getSystemService(Context.DOWNLOAD_SERVICE) as DownloadManager
        dm.query(DownloadManager.Query().setFilterById(downloadId)).use { cursor ->
            if (!cursor.moveToFirst()) return false
            val status = cursor.getInt(
                cursor.getColumnIndexOrThrow(DownloadManager.COLUMN_STATUS),
            )
            return status == DownloadManager.STATUS_SUCCESSFUL
        }
    }

    /**
     * Fires the system install prompt for the downloaded APK.
     * API 26+ requires the "Install unknown apps" grant for our package;
     * without it we deep-link Kamran to the exact settings page instead
     * of showing a dead installer.
     */
    private fun promptInstall(ctx: Context, fileName: String) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O &&
            !ctx.packageManager.canRequestPackageInstalls()
        ) {
            val settingsIntent = Intent(
                Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES,
                Uri.parse("package:${ctx.packageName}"),
            ).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            ctx.startActivity(settingsIntent)
            return
        }
        @Suppress("DEPRECATION")
        val apkFile = File(
            Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS),
            "$DOWNLOAD_SUBDIR/$fileName",
        )
        if (!apkFile.exists()) return
        val uri = FileProvider.getUriForFile(
            ctx,
            "${ctx.packageName}.fileprovider",
            apkFile,
        )
        val install = Intent(Intent.ACTION_VIEW).apply {
            setDataAndType(uri, "application/vnd.android.package-archive")
            addFlags(Intent.FLAG_GRANT_READ_URI)
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        ctx.startActivity(install)
    }
}

/**
 * Every user-facing update line in all three languages (en-GB English).
 * The parent surfaces these through the normal reply pipeline so they
 * are spoken/written in Kamran's active language.
 */
object UpdateStrings {

    /** Parent's canonical offline line (shown verbatim, Roman Urdu). */
    const val OFFLINE_LINE = "Internet chahiye, sir — baad mein check kar lena."

    val OFFLINE = L3(
        en = "I need the internet for that, sir — we'll check again later.",
        ur = "اس کے لیے انٹرنیٹ چاہیے، سر — بعد میں دوبارہ چیک کر لیں گے۔",
        hi = "इसके लिए इंटरनेट चाहिए, सर — बाद में दोबारा जाँच कर लेंगे।",
    )
    val CHECKING = L3(
        en = "Checking for updates, sir — one moment.",
        ur = "اپ ڈیٹس چیک کر رہا ہوں، سر — ایک لمحہ۔",
        hi = "अपडेट्स जाँच रहा हूँ, सर — एक पल।",
    )
    val UP_TO_DATE = L3(
        en = "You're on the latest version, sir — v$APP_VERSION_NAME. Nothing to do.",
        ur = "آپ تازہ ترین ورژن پر ہیں، سر — v$APP_VERSION_NAME۔ کچھ کرنے کی ضرورت نہیں۔",
        hi = "आप नवीनतम संस्करण पर हैं, सर — v$APP_VERSION_NAME। कुछ करने की ज़रूरत नहीं।",
    )
    val AVAILABLE = L3(
        en = "A new Baloch is ready, sir — v{version}. Shall I download it?",
        ur = "بلوچ کا نیا ورژن تیار ہے، سر — v{version}۔ کیا میں اسے ڈاؤن لوڈ کروں؟",
        hi = "बलोच का नया संस्करण तैयार है, सर — v{version}। क्या मैं इसे डाउनलोड करूँ?",
    )
    val DOWNLOAD_DESC = L3(
        en = "Baloch update — I'll ask before installing anything.",
        ur = "بلوچ اپ ڈیٹ — انسٹال کرنے سے پہلے میں پوچھوں گا۔",
        hi = "बलोच अपडेट — इंस्टॉल करने से पहले मैं पूछूँगा।",
    )
    val DOWNLOAD_STARTED = L3(
        en = "Downloading the update, sir — I'll ask before installing.",
        ur = "اپ ڈیٹ ڈاؤن لوڈ ہو رہی ہے، سر — انسٹال کرنے سے پہلے پوچھوں گا۔",
        hi = "अपडेट डाउनलोड हो रहा है, सर — इंस्टॉल करने से पहले पूछूँगा।",
    )
    val INSTALL_READY = L3(
        en = "The update is downloaded, sir — tap Install when you're ready. Nothing installs without your say-so.",
        ur = "اپ ڈیٹ ڈاؤن لوڈ ہو گئی ہے، سر — جب تیار ہوں تو انسٹال دبائیں۔ آپ کی اجازت کے بغیر کچھ انسٹال نہیں ہوگا۔",
        hi = "अपडेट डाउनलोड हो गया है, सर — तैयार हों तो इंस्टॉल दबाएँ। आपकी अनुमति के बिना कुछ इंस्टॉल नहीं होगा।",
    )
    val UNKNOWN_SOURCES = L3(
        en = "Please allow “Install unknown apps” for Baloch in Settings, sir — then tap install again.",
        ur = "براہ کرم سیٹنگز میں بلوچ کے لیے “نامعلوم ایپس انسٹال کریں” کی اجازت دیں، سر — پھر دوبارہ انسٹال دبائیں۔",
        hi = "कृपया सेटिंग्स में बलोच के लिए “अज्ञात ऐप्स इंस्टॉल करें” की अनुमति दें, सर — फिर दोबारा इंस्टॉल दबाएँ।",
    )
    val RELEASE_UNREADABLE = L3(
        en = "I couldn't read the release notes, sir — the update feed looks off.",
        ur = "میں ریلیز نوٹس نہیں پڑھ سکا، سر — اپ ڈیٹ فیڈ کچھ ٹھیک نہیں لگ رہی۔",
        hi = "मैं रिलीज़ नोट्स नहीं पढ़ सका, सर — अपडेट फ़ीड कुछ ठीक नहीं लग रही।",
    )
    val CHECK_FAILED = L3(
        en = "The update check hit a snag, sir — {msg}.",
        ur = "اپ ڈیٹ چیک میں رکاوٹ آئی، سر — {msg}۔",
        hi = "अपडेट जाँच में अड़चन आई, सर — {msg}।",
    )
}
