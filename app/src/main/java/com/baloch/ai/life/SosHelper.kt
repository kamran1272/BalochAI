package com.baloch.ai.life

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.location.Location
import android.location.LocationManager
import android.os.Build
import android.telephony.SmsManager
import androidx.core.content.ContextCompat
import com.baloch.ai.brain.BalochLanguage
import com.baloch.ai.brain.L3
import com.baloch.ai.engine.pro.EmergencyStore

/**
 * SOS — "SOS" sends a location SMS to the ICE contact:
 *   "SOS! I need help. My location: <maps link>"
 *
 * Risk tier: RISKY — the parent routes this through the confirmation
 * dialog (see BrainEngine.SendSosAlert.needsConfirmation) before this
 * code ever runs.
 *
 * Battery-friendly: uses the last-known location (zero GPS wait) and
 * only falls back to one single fresh fix when nothing is cached.
 */
sealed interface SosResult {
    /** No ICE contact saved — the Settings row must be filled first. */
    data object NeedsIceContact : SosResult
    /** SEND_SMS not granted. */
    data object NoSmsPermission : SosResult
    data class Sent(val withLocation: Boolean) : SosResult
    data class Failed(val reason: String) : SosResult
}

object SosHelper {

    /**
     * Sends the SOS SMS. Must be called AFTER the user confirms
     * (parent's confirmation path). Never call from a worker.
     */
    fun sendSos(context: Context): SosResult {
        val number = EmergencyStore.get(context) ?: return SosResult.NeedsIceContact
        if (ContextCompat.checkSelfPermission(context, Manifest.permission.SEND_SMS) !=
            PackageManager.PERMISSION_GRANTED
        ) return SosResult.NoSmsPermission

        val location = bestLocation(context)
        val link = location?.let {
            "https://maps.google.com/?q=${it.latitude},${it.longitude}"
        }
        val message = if (link != null) {
            "SOS! I need help. My location: $link"
        } else {
            "SOS! I need help. (Location unavailable — call me.)"
        }
        return try {
            val sms = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                context.getSystemService(SmsManager::class.java)
            } else {
                @Suppress("DEPRECATION")
                SmsManager.getDefault()
            }
            val parts = sms.divideMessage(message)
            if (parts.size <= 1) {
                sms.sendTextMessage(number, null, message, null, null)
            } else {
                sms.sendMultipartTextMessage(number, null, parts, null, null)
            }
            SosResult.Sent(withLocation = link != null)
        } catch (e: Exception) {
            SosResult.Failed(e.message ?: "SMS send failed")
        }
    }

    /**
     * Battery-friendly single location fix: last-known first (free),
     * then ONE fresh network fix with a short timeout, never a
     * continuous GPS session.
     */
    fun bestLocation(context: Context): Location? {
        if (ContextCompat.checkSelfPermission(
                context, Manifest.permission.ACCESS_FINE_LOCATION
            ) != PackageManager.PERMISSION_GRANTED &&
            ContextCompat.checkSelfPermission(
                context, Manifest.permission.ACCESS_COARSE_LOCATION
            ) != PackageManager.PERMISSION_GRANTED
        ) return null // no location permission — SMS still goes out without it

        val lm = context.getSystemService(Context.LOCATION_SERVICE) as LocationManager
        val providers = lm.getProviders(true)
        var best: Location? = null
        for (p in providers) {
            @Suppress("MissingPermission")
            val loc = runCatching { lm.getLastKnownLocation(p) }.getOrNull()
            if (loc != null && (best == null || loc.time > best!!.time)) best = loc
        }
        if (best != null && System.currentTimeMillis() - best.time < 10 * 60_000L) {
            return best // fresh enough — no GPS wake at all
        }
        // One single fresh fix, network provider, 8 s timeout.
        return try {
            val latch = java.util.concurrent.CountDownLatch(1)
            var fresh: Location? = null
            val listener = object : android.location.LocationListener {
                override fun onLocationChanged(location: Location) {
                    fresh = location
                    latch.countDown()
                }
                override fun onProviderEnabled(provider: String) = Unit
                override fun onProviderDisabled(provider: String) = Unit
                @Deprecated("deprecated")
                override fun onStatusChanged(
                    provider: String?, status: Int, extras: android.os.Bundle?
                ) = Unit
            }
            @Suppress("MissingPermission")
            lm.requestSingleUpdate(LocationManager.NETWORK_PROVIDER, listener, null)
            latch.await(8, java.util.concurrent.TimeUnit.SECONDS)
            runCatching { lm.removeUpdates(listener) }
            fresh ?: best
        } catch (_: Exception) {
            best
        }
    }
}

// ------------------------------------------------------------------
// Reply text
// ------------------------------------------------------------------

val SOS_SENT = L3(
    en = "SOS sent to your emergency contact, sir 🚨{loc}Stay safe — I'm right here.",
    ur = "آپ کے ایمرجنسی رابطے کو SOS بھیج دیا، سر 🚨{loc}محفوظ رہیں — میں یہیں ہوں۔",
    hi = "आपके आपातकालीन संपर्क को SOS भेज दिया, सर 🚨{loc}सुरक्षित रहें — मैं यहीं हूँ।",
)

val SOS_SENT_NO_LOC = L3(
    en = "SOS sent to your emergency contact, sir 🚨 — I couldn't get a location fix, so the message went without one. Stay safe.",
    ur = "آپ کے ایمرجنسی رابطے کو SOS بھیج دیا، سر 🚨 — لوکیشن نہیں مل سکی، اس لیے پیغام بغیر لوکیشن کے گیا۔ محفوظ رہیں۔",
    hi = "आपके आपातकालीन संपर्क को SOS भेज दिया, सर 🚨 — लोकेशन नहीं मिल सकी, इसलिए संदेश बिना लोकेशन के गया। सुरक्षित रहें।",
)

val SOS_NO_ICE = L3(
    en = "I don't have an emergency contact saved yet, sir — add one in Settings → SOS → Emergency contact, and I'll be ready when it matters. 🛟",
    ur = "میرے پاس ابھی کوئی ایمرجنسی رابطہ محفوظ نہیں، سر — سیٹنگز → SOS → Emergency contact میں درج کریں، پھر ضرورت کے وقت میں حاضر ہوں گا۔ 🛟",
    hi = "मेरे पास अभी कोई आपातकालीन संपर्क सहेजा नहीं है, सर — सेटिंग्स → SOS → Emergency contact में दर्ज करें, फिर ज़रूरत के समय मैं हाज़िर हूँगा। 🛟",
)

val SOS_NO_SMS_PERM = L3(
    en = "I need the SMS permission to send the SOS, sir — grant it when the system asks, then say SOS again. 📲",
    ur = "SOS بھیجنے کے لیے مجھے SMS کی اجازت چاہیے، سر — سسٹم کے پوچھنے پر اجازت دیں، پھر دوبارہ SOS کہیں۔ 📲",
    hi = "SOS भेजने के लिए मुझे SMS अनुमति चाहिए, सर — सिस्टम के पूछने पर अनुमति दें, फिर दोबारा SOS कहें। 📲",
)

/** Friendly result text for the parent to speak after confirmation. */
fun sosResultText(result: SosResult, lang: BalochLanguage): String = when (result) {
    is SosResult.NeedsIceContact -> SOS_NO_ICE.pick(lang)
    is SosResult.NoSmsPermission -> SOS_NO_SMS_PERM.pick(lang)
    is SosResult.Sent -> if (result.withLocation) {
        SOS_SENT.format(lang, "loc" to " with your live location. ")
    } else {
        SOS_SENT_NO_LOC.pick(lang)
    }
    is SosResult.Failed -> when (lang) {
        BalochLanguage.ENGLISH -> "The SOS didn't go through, sir — ${result.reason}. Please try calling directly. 🚨"
        BalochLanguage.URDU -> "SOS نہیں بھیجا جا سکا، سر — ${result.reason}۔ براہ کرم براہِ راست کال کریں۔ 🚨"
        BalochLanguage.HINDI -> "SOS नहीं भेजा जा सका, सर — ${result.reason}। कृपया सीधे कॉल करें। 🚨"
    }
}
