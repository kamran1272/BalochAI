package com.baloch.ai.engine

import android.Manifest
import android.app.AlarmManager
import android.app.PendingIntent
import android.bluetooth.BluetoothManager
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.hardware.camera2.CameraManager
import android.media.AudioManager
import android.net.wifi.WifiManager
import android.os.BatteryManager
import android.os.Build
import android.provider.AlarmClock
import android.provider.ContactsContract
import android.provider.Settings
import android.telephony.SmsManager
import androidx.core.content.ContextCompat
import com.baloch.ai.brain.BalochLanguage
import com.baloch.ai.brain.DeviceAction
import com.baloch.ai.brain.DeviceContext
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/** Outcome of running one [DeviceAction]. */
sealed interface ActionResult {
    data class Ok(val message: String) : ActionResult
    data class NeedPermission(val permission: String, val message: String) : ActionResult
    data class Failed(val message: String) : ActionResult
}

/**
 * Executes [DeviceAction]s against real Android APIs.
 * Every privileged call is guarded: denial returns [ActionResult.NeedPermission]
 * with a human message instead of crashing.
 */
class DeviceActions(private val context: Context) {

    private val app: Context get() = context.applicationContext

    // ------------------------------------------------------------------
    // Live device context for the brain
    // ------------------------------------------------------------------

    fun currentContext(language: BalochLanguage): DeviceContext {
        val bm = app.getSystemService(Context.BATTERY_SERVICE) as BatteryManager
        val pct = bm.getIntProperty(BatteryManager.BATTERY_PROPERTY_CAPACITY)
        val charging = bm.isCharging
        val time = SimpleDateFormat("h:mm a", Locale.US).format(Date())
        val date = SimpleDateFormat("EEEE, d MMMM", Locale.US).format(Date())
        return DeviceContext(pct, charging, time, date, language)
    }

    // ------------------------------------------------------------------
    // Contact lookup (for call / SMS)
    // ------------------------------------------------------------------

    /** Returns the phone number for a contact name (fuzzy), or null. */
    fun resolveContact(name: String): String? {
        if (!has(Manifest.permission.READ_CONTACTS)) return null
        val resolver = app.contentResolver
        val cursor = resolver.query(
            ContactsContract.CommonDataKinds.Phone.CONTENT_URI,
            arrayOf(
                ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME,
                ContactsContract.CommonDataKinds.Phone.NUMBER,
            ),
            "${ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME} LIKE ?",
            arrayOf("%$name%"),
            null,
        ) ?: return null
        cursor.use {
            if (it.moveToFirst()) {
                return it.getString(1)?.replace(Regex("[^+\\d]"), "")
            }
        }
        return null
    }

    // ------------------------------------------------------------------
    // Dispatcher
    // ------------------------------------------------------------------

    fun execute(action: DeviceAction): ActionResult = try {
        when (action) {
            is DeviceAction.OpenApp -> openApp(action.appName)
            is DeviceAction.SetAlarm -> setAlarm(action.hour, action.minute, action.label)
            is DeviceAction.StartTimer -> startTimer(action.seconds, action.label)
            is DeviceAction.AddReminder -> addReminder(action.text, action.minutesFromNow)
            is DeviceAction.ToggleWifi -> toggleWifi(action.on)
            is DeviceAction.ToggleBluetooth -> toggleBluetooth(action.on)
            is DeviceAction.ToggleFlashlight -> toggleFlashlight(action.on)
            is DeviceAction.SetVolume -> setVolume(action.levelPercent)
            is DeviceAction.AdjustVolume -> adjustVolume(action.up)
            is DeviceAction.SetMuted -> setMuted(action.muted)
            is DeviceAction.SetBrightness -> setBrightness(action.levelPercent)
            is DeviceAction.WebSearch -> webSearch(action.query)
            is DeviceAction.AddNote -> ActionResult.Ok("note saved")
            is DeviceAction.RememberFact -> ActionResult.Ok("fact saved")
            is DeviceAction.CallContact -> call(action.number)
            is DeviceAction.SendSms -> sendSms(action.number, action.message)
            is DeviceAction.SetDnd -> setDnd(action.on)
            is DeviceAction.DisableDndDelayed -> disableDndDelayed(action.minutes)
            is DeviceAction.WhatsAppDraft -> whatsAppDraft(action.number, action.text)
            is DeviceAction.EmergencyCall -> emergencyCall(action.number)
            else -> ActionResult.Ok("done")
        }
    } catch (e: SecurityException) {
        ActionResult.NeedPermission("", "A permission was denied, sir: ${e.message}")
    } catch (e: Exception) {
        ActionResult.Failed("That didn't work, sir: ${e.message}")
    }

    // ------------------------------------------------------------------
    // Implementations
    // ------------------------------------------------------------------

    private fun openApp(appName: String): ActionResult {
        val pm = app.packageManager
        val main = Intent(Intent.ACTION_MAIN, null).addCategory(Intent.CATEGORY_LAUNCHER)
        val launchables = pm.queryIntentActivities(main, 0)
        val q = appName.lowercase()
        // 1) exact label match, 2) label contains, 3) package contains
        val best = launchables.firstOrNull {
            it.loadLabel(pm).toString().lowercase() == q
        } ?: launchables.firstOrNull {
            it.loadLabel(pm).toString().lowercase().contains(q)
        } ?: launchables.firstOrNull {
            it.activityInfo.packageName.lowercase().contains(q.replace(" ", ""))
        } ?: return ActionResult.Failed("I couldn't find an app called \"$appName\", sir.")
        val launch = pm.getLaunchIntentForPackage(best.activityInfo.packageName)
            ?: return ActionResult.Failed("I found it but couldn't launch it, sir.")
        launch.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        app.startActivity(launch)
        return ActionResult.Ok("opened ${best.loadLabel(pm)}")
    }

    private fun setAlarm(hour: Int, minute: Int, label: String): ActionResult {
        val intent = Intent(AlarmClock.ACTION_SET_ALARM).apply {
            putExtra(AlarmClock.EXTRA_HOUR, hour)
            putExtra(AlarmClock.EXTRA_MINUTES, minute)
            if (label.isNotBlank()) putExtra(AlarmClock.EXTRA_MESSAGE, label)
            putExtra(AlarmClock.EXTRA_SKIP_UI, true)
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        return if (intent.resolveActivity(app.packageManager) != null) {
            app.startActivity(intent)
            ActionResult.Ok("alarm set")
        } else {
            ActionResult.Failed("This device has no alarm app I can use, sir.")
        }
    }

    private fun startTimer(seconds: Int, label: String): ActionResult {
        val intent = Intent(AlarmClock.ACTION_SET_TIMER).apply {
            putExtra(AlarmClock.EXTRA_LENGTH, seconds)
            if (label.isNotBlank()) putExtra(AlarmClock.EXTRA_MESSAGE, label)
            putExtra(AlarmClock.EXTRA_SKIP_UI, true)
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        return if (intent.resolveActivity(app.packageManager) != null) {
            app.startActivity(intent)
            ActionResult.Ok("timer started")
        } else {
            ActionResult.Failed("This device has no timer app I can use, sir.")
        }
    }

    private fun addReminder(text: String, minutesFromNow: Int): ActionResult {
        // Real scheduling via AlarmManager -> ReminderReceiver -> notification.
        val am = app.getSystemService(Context.ALARM_SERVICE) as AlarmManager
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S && !am.canScheduleExactAlarms()) {
            return ActionResult.NeedPermission(
                Manifest.permission.SCHEDULE_EXACT_ALARM,
                "I need the Alarms permission for timed reminders, sir — allow it in Settings."
            )
        }
        val requestCode = (text + System.currentTimeMillis()).hashCode()
        val pending = PendingIntent.getBroadcast(
            app,
            requestCode,
            Intent(app, ReminderReceiver::class.java).putExtra(ReminderReceiver.EXTRA_TEXT, text),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
        am.setExactAndAllowWhileIdle(
            AlarmManager.RTC_WAKEUP,
            System.currentTimeMillis() + minutesFromNow * 60_000L,
            pending,
        )
        return ActionResult.Ok("reminder scheduled")
    }

    private fun toggleWifi(on: Boolean): ActionResult {
        // Since Android 10 (API 29) apps can no longer toggle Wi-Fi directly;
        // the approved path is the system Wi-Fi panel.
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            val panel = Intent(Settings.Panel.ACTION_WIFI).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            app.startActivity(panel)
            ActionResult.Ok("wifi panel opened")
        } else {
            @Suppress("DEPRECATION")
            val wm = app.getSystemService(Context.WIFI_SERVICE) as WifiManager
            @Suppress("DEPRECATION")
            wm.isWifiEnabled = on
            ActionResult.Ok("wifi toggled")
        }
    }

    private fun toggleBluetooth(on: Boolean): ActionResult {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S && !has(Manifest.permission.BLUETOOTH_CONNECT)) {
            return ActionResult.NeedPermission(
                Manifest.permission.BLUETOOTH_CONNECT,
                "I need the Bluetooth permission, sir."
            )
        }
        val adapter = (app.getSystemService(Context.BLUETOOTH_SERVICE) as BluetoothManager).adapter
            ?: return ActionResult.Failed("This device has no Bluetooth, sir.")
        return if (on) {
            @Suppress("MissingPermission")
            if (adapter.enable()) ActionResult.Ok("bluetooth on")
            else ActionResult.Failed("Couldn't switch Bluetooth on, sir.")
        } else {
            // Disabling programmatically is restricted on modern Android;
            // open settings so the user does it in one tap.
            val intent = Intent(Settings.ACTION_BLUETOOTH_SETTINGS).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            app.startActivity(intent)
            ActionResult.Ok("bluetooth settings opened")
        }
    }

    private fun toggleFlashlight(on: Boolean): ActionResult {
        if (!has(Manifest.permission.CAMERA)) {
            return ActionResult.NeedPermission(
                Manifest.permission.CAMERA,
                "I need the Camera permission for the torch, sir."
            )
        }
        val cm = app.getSystemService(Context.CAMERA_SERVICE) as CameraManager
        val id = cm.cameraIdList.firstOrNull { cm.getCameraCharacteristics(it)
            .get(android.hardware.camera2.CameraCharacteristics.FLASH_INFO_AVAILABLE) == true }
            ?: return ActionResult.Failed("This device has no flash, sir.")
        cm.setTorchMode(id, on)
        return ActionResult.Ok("torch ${if (on) "on" else "off"}")
    }

    private fun audio(): AudioManager =
        app.getSystemService(Context.AUDIO_SERVICE) as AudioManager

    private fun setVolume(percent: Int): ActionResult {
        val am = audio()
        val max = am.getStreamMaxVolume(AudioManager.STREAM_MUSIC)
        am.setStreamVolume(AudioManager.STREAM_MUSIC, (max * percent / 100f).toInt(), 0)
        return ActionResult.Ok("volume $percent%")
    }

    private fun adjustVolume(up: Boolean): ActionResult {
        audio().adjustStreamVolume(
            AudioManager.STREAM_MUSIC,
            if (up) AudioManager.ADJUST_RAISE else AudioManager.ADJUST_LOWER,
            AudioManager.FLAG_SHOW_UI,
        )
        return ActionResult.Ok("volume adjusted")
    }

    private fun setMuted(muted: Boolean): ActionResult {
        audio().adjustStreamVolume(
            AudioManager.STREAM_MUSIC,
            if (muted) AudioManager.ADJUST_MUTE else AudioManager.ADJUST_UNMUTE,
            0,
        )
        return ActionResult.Ok(if (muted) "muted" else "unmuted")
    }

    private fun setBrightness(percent: Int): ActionResult {
        // Needs the special WRITE_SETTINGS grant (Settings → Apps → Baloch → Modify system settings).
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M && !Settings.System.canWrite(app)) {
            return ActionResult.NeedPermission(
                Manifest.permission.WRITE_SETTINGS,
                "I need the 'Modify system settings' grant for brightness, sir — it's in Baloch's app settings."
            )
        }
        Settings.System.putInt(
            app.contentResolver,
            Settings.System.SCREEN_BRIGHTNESS,
            (percent / 100f * 255).toInt().coerceIn(1, 255),
        )
        return ActionResult.Ok("brightness $percent%")
    }

    private fun webSearch(query: String): ActionResult {
        val intent = Intent(
            Intent.ACTION_VIEW,
            android.net.Uri.parse(
                "https://www.google.com/search?q=" + android.net.Uri.encode(query)
            ),
        ).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        app.startActivity(intent)
        return ActionResult.Ok("search opened")
    }

    private fun call(number: String): ActionResult {
        if (!has(Manifest.permission.CALL_PHONE)) {
            return ActionResult.NeedPermission(
                Manifest.permission.CALL_PHONE,
                "I need the Phone permission to call, sir."
            )
        }
        val intent = Intent(
            Intent.ACTION_CALL,
            android.net.Uri.parse("tel:$number")
        ).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        app.startActivity(intent)
        return ActionResult.Ok("calling $number")
    }

    private fun sendSms(number: String, message: String): ActionResult {
        if (!has(Manifest.permission.SEND_SMS)) {
            return ActionResult.NeedPermission(
                Manifest.permission.SEND_SMS,
                "I need the SMS permission to send messages, sir."
            )
        }
        val sm = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            app.getSystemService(SmsManager::class.java)
        } else {
            @Suppress("DEPRECATION")
            SmsManager.getDefault()
        }
        val parts = sm.divideMessage(message)
        sm.sendMultipartTextMessage(number, null, parts, null, null)
        return ActionResult.Ok("sms sent")
    }

    // ------------------------------------------------------------------
    // PRO PACK
    // ------------------------------------------------------------------

    private fun setDnd(on: Boolean): ActionResult {
        val nm = app.getSystemService(Context.NOTIFICATION_SERVICE) as android.app.NotificationManager
        if (!nm.isNotificationPolicyAccessGranted) {
            return ActionResult.NeedPermission(
                "Do Not Disturb access",
                "For do-not-disturb I need the DND access grant, sir — " +
                    "it's in Settings → Apps → Baloch → Do Not Disturb access."
            )
        }
        nm.setInterruptionFilter(
            if (on) android.app.NotificationManager.INTERRUPTION_FILTER_NONE
            else android.app.NotificationManager.INTERRUPTION_FILTER_ALL
        )
        return ActionResult.Ok("dnd ${if (on) "on" else "off"}")
    }

    private fun disableDndDelayed(minutes: Int): ActionResult {
        // Inexact alarm on purpose: no SCHEDULE_EXACT_ALARM permission needed.
        val am = app.getSystemService(Context.ALARM_SERVICE) as AlarmManager
        val pending = PendingIntent.getBroadcast(
            app,
            0,
            Intent(app, com.baloch.ai.engine.pro.DndOffReceiver::class.java),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
        am.setAndAllowWhileIdle(
            AlarmManager.RTC_WAKEUP,
            System.currentTimeMillis() + minutes.coerceIn(1, 24 * 60) * 60_000L,
            pending,
        )
        return ActionResult.Ok("dnd auto-off scheduled in $minutes min")
    }

    private fun whatsAppDraft(number: String, text: String): ActionResult {
        val digits = number.replace(Regex("[^+\\d]"), "")
        val send = Intent(
            Intent.ACTION_SENDTO,
            android.net.Uri.parse("smsto:$digits")
        ).apply {
            setPackage("com.whatsapp")
            putExtra(Intent.EXTRA_TEXT, text)
            putExtra("sms_body", text)
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        return try {
            if (send.resolveActivity(app.packageManager) != null) {
                app.startActivity(send)
                ActionResult.Ok("whatsapp draft opened")
            } else {
                whatsAppWebFallback(digits, text)
            }
        } catch (_: Exception) {
            whatsAppWebFallback(digits, text)
        }
    }

    private fun whatsAppWebFallback(digits: String, text: String): ActionResult {
        return try {
            val url = "https://api.whatsapp.com/send?phone=${digits.trimStart('+')}" +
                "&text=" + android.net.Uri.encode(text)
            val browser = Intent(
                Intent.ACTION_VIEW,
                android.net.Uri.parse(url)
            ).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            app.startActivity(browser)
            ActionResult.Ok("whatsapp web fallback opened")
        } catch (e: Exception) {
            ActionResult.Failed("Hmm, ye nahi ho paya, sir — couldn't open WhatsApp: ${e.message}")
        }
    }

    private fun emergencyCall(number: String): ActionResult {
        if (!has(Manifest.permission.CALL_PHONE)) {
            return ActionResult.NeedPermission(
                Manifest.permission.CALL_PHONE,
                "I need the Phone permission for the emergency call, sir."
            )
        }
        val intent = Intent(
            Intent.ACTION_CALL,
            android.net.Uri.parse("tel:$number")
        ).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        app.startActivity(intent)
        return ActionResult.Ok("emergency call placed")
    }

    // ------------------------------------------------------------------

    private fun has(permission: String): Boolean =
        ContextCompat.checkSelfPermission(app, permission) == PackageManager.PERMISSION_GRANTED
}
