package com.outspoken.help

import android.Manifest
import android.app.Activity
import android.app.PendingIntent
import android.content.ActivityNotFoundException
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.PackageManager
import android.media.AudioManager
import android.net.Uri
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import android.telephony.SmsManager
import android.telephony.SubscriptionManager
import android.telephony.TelephonyManager
import androidx.core.content.ContextCompat
import com.outspoken.log.AppLog

/**
 * Texts and then calls the SOS contact when the help alarm sounds, over the phone network: no
 * internet. The text goes first, as it arrives even when the call is not picked up; the call
 * waits for the text to be sent, up to [TEXT_WAIT_MS]. Each step and its outcome go in the log,
 * with the number masked. [onStatus] gets a short line for the help screen.
 */
class SosCaller(private val activity: Activity, private val onStatus: (String) -> Unit) {

    private val handler = Handler(Looper.getMainLooper())
    private var pendingCall: (() -> Unit)? = null
    private val callNow = Runnable { startPendingCall() }
    private var callWatch: Runnable? = null

    private val sentAction = "${activity.packageName}.SOS_TEXT_SENT"
    private val sentReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            val sent = resultCode == Activity.RESULT_OK
            AppLog.write("help", if (sent) "SOS text sent" else "SOS text not sent (${textError(resultCode)})")
            if (!sent) onStatus(if (pendingCall != null) "Calling; the text did not go, see the log" else "The text did not go; see the log")
            startPendingCall()
        }
    }

    init {
        ContextCompat.registerReceiver(activity, sentReceiver, IntentFilter(sentAction), ContextCompat.RECEIVER_NOT_EXPORTED)
    }

    fun release() {
        handler.removeCallbacksAndMessages(null)
        runCatching { activity.unregisterReceiver(sentReceiver) }
    }

    fun has(permission: String) = ContextCompat.checkSelfPermission(activity, permission) == PackageManager.PERMISSION_GRANTED

    fun allowed(): Boolean = has(Manifest.permission.SEND_SMS) && has(Manifest.permission.CALL_PHONE)

    /**
     * Whether the phone has a SIM to text and call from. Android's default SIM is the one for
     * calls, else the first SIM, so none means no SIM at all. The owner's phone has none, and
     * every SOS text failed with code 32 (06:15:56, 06:16:35, 06:16:56).
     */
    fun hasSim(): Boolean = SubscriptionManager.isValidSubscriptionId(SubscriptionManager.getDefaultSubscriptionId())

    /**
     * The phone's region, for a number saved without a country code, and where it came from: the
     * mobile network's, else the SIM's, else the phone's language region ("English (India)").
     */
    private fun region(): Pair<String, String>? {
        val telephony = activity.getSystemService(TelephonyManager::class.java)
        telephony?.networkCountryIso?.takeIf { it.isNotBlank() }?.let { return it.uppercase() to "the mobile network" }
        telephony?.simCountryIso?.takeIf { it.isNotBlank() }?.let { return it.uppercase() to "the SIM" }
        activity.resources.configuration.locales[0].country.takeIf { it.isNotBlank() }?.let { return it to "the phone's language" }
        return null
    }

    /** The number WhatsApp gets: as saved with "+", else with the phone's country code added. */
    fun whatsAppNumberFor(number: String): String? = whatsAppNumber(number, region()?.first?.let(::callingCode))

    /** Whether WhatsApp is on this phone (either app). */
    fun hasWhatsApp(): Boolean = WHATSAPP_PACKAGES.any { pkg ->
        runCatching { activity.packageManager.getPackageInfo(pkg, 0) }.isSuccess
    }

    /**
     * Texts [message] if [text], then calls if [call]; [number] as [sosNumber] gives it. With no
     * SIM, a WhatsApp message if [whatsAppText] and a WhatsApp call if [whatsAppCall] instead.
     */
    fun alert(number: String, message: String, text: Boolean, call: Boolean, whatsAppText: Boolean = false, whatsAppCall: Boolean = false) {
        handler.removeCallbacksAndMessages(null)
        pendingCall = null
        val who = maskedNumber(number)
        if (!hasSim()) {
            if (whatsAppText || whatsAppCall) {
                whatsApp(number, message, whatsAppText, whatsAppCall)
            } else {
                AppLog.write("help", "SOS to $who: no SIM in the phone, so no text or call can go out")
                onStatus("No SIM in this phone: $who cannot be texted or called")
            }
            return
        }
        val texting = text && sendText(number, message)
        if (text && !texting && !has(Manifest.permission.SEND_SMS)) AppLog.write("help", "SOS text to $who skipped: no permission to send texts")
        val calling = call && !isEmergencyNumber(number)
        if (call && !calling) AppLog.write("help", "SOS call to $who skipped: Android lets no app call emergency numbers itself")
        val doing = listOfNotNull("texting".takeIf { texting }, "calling".takeIf { calling })
        onStatus(if (doing.isEmpty()) "Could not reach the SOS contact; see the log" else "${doing.joinToString(" and ").replaceFirstChar { it.uppercase() }} $who")
        if (!calling) return
        pendingCall = {
            if (!placeCall(number)) onStatus(if (texting) "Texting $who; the call could not start, see the log" else "Could not reach the SOS contact; see the log")
        }
        // The call screen took over 11 ms after the text was handed to the phone (06:15:56), so
        // the call now waits for the text's result, which then reaches the log first.
        if (texting) handler.postDelayed(callNow, TEXT_WAIT_MS) else startPendingCall()
    }

    /** Opens the WhatsApp chat with the message typed in; the accessibility helper presses the buttons. */
    private fun whatsApp(number: String, message: String, text: Boolean, call: Boolean) {
        val who = maskedNumber(number)
        val waNumber = whatsAppNumberFor(number) ?: run {
            AppLog.write("help", "WhatsApp SOS to $who skipped: the number has no country code and the phone's region is not known, so WhatsApp cannot find it")
            onStatus("No SIM, and the SOS number needs its country code (+91 …) for WhatsApp")
            return
        }
        if (!number.startsWith("+")) {
            val (code, from) = region() ?: ("" to "")
            AppLog.write("help", "WhatsApp SOS to $who: country code ${callingCode(code)} added, region $code from $from")
        }
        val link = Uri.parse(whatsAppChatLink(waNumber, if (text) message else ""))
        val app = WHATSAPP_PACKAGES.firstOrNull { pkg ->
            try {
                activity.startActivity(Intent(Intent.ACTION_VIEW, link).setPackage(pkg))
                true
            } catch (e: ActivityNotFoundException) {
                false
            }
        }
        if (app == null) {
            AppLog.write("help", "WhatsApp SOS to $who skipped: WhatsApp is not on this phone")
            onStatus("No SIM, and no WhatsApp: $who cannot be reached")
            return
        }
        val helped = WhatsAppRun.start(whatsAppSteps(text, call), who)
        AppLog.write(
            "help",
            "WhatsApp SOS to $who: chat opened in $app" +
                if (helped) ", the helper presses ${whatsAppSteps(text, call).joinToString(", then ") { it.what }}" else ", but the helper is off in Accessibility, so someone must press Send",
        )
        val doing = listOfNotNull("message".takeIf { text }, "call".takeIf { call }).joinToString(" and ")
        onStatus(if (helped) "No SIM: WhatsApp $doing to $who" else "No SIM: WhatsApp is open for $who; press Send")
    }

    private fun startPendingCall() {
        handler.removeCallbacks(callNow)
        val call = pendingCall ?: return
        pendingCall = null
        call()
    }

    private fun sendText(number: String, message: String): Boolean {
        if (ContextCompat.checkSelfPermission(activity, Manifest.permission.SEND_SMS) != PackageManager.PERMISSION_GRANTED) return false
        val sim = sosSim(SubscriptionManager.getDefaultSmsSubscriptionId(), SubscriptionManager.getDefaultSubscriptionId(), "texts")
        return runCatching {
            val default = activity.getSystemService(SmsManager::class.java)
            val sms = sim.subId?.let { default.createForSubscriptionId(it) } ?: default
            val parts = sms.divideMessage(message)
            val sent = PendingIntent.getBroadcast(
                activity,
                0,
                Intent(sentAction).setPackage(activity.packageName),
                PendingIntent.FLAG_IMMUTABLE,
            )
            sms.sendMultipartTextMessage(number, null, parts, ArrayList(List(parts.size) { sent }), null)
            AppLog.write("help", "SOS text to ${maskedNumber(number)} handed to the phone, ${parts.size} part(s), SIM ${sim.subId ?: "not chosen"} (${sim.why})")
        }.onFailure { AppLog.write("help", "SOS text failed: $it") }.isSuccess
    }

    private fun placeCall(number: String): Boolean {
        val who = maskedNumber(number)
        if (ContextCompat.checkSelfPermission(activity, Manifest.permission.CALL_PHONE) != PackageManager.PERMISSION_GRANTED) {
            AppLog.write("help", "SOS call to $who skipped: no permission to make calls")
            return false
        }
        return try {
            activity.startActivity(Intent(Intent.ACTION_CALL, Uri.fromParts("tel", number, null)))
            AppLog.write("help", "SOS call to $who started")
            watchCall(who)
            true
        } catch (e: SecurityException) {
            AppLog.write("help", "SOS call to $who refused: $e")
            false
        } catch (e: ActivityNotFoundException) {
            AppLog.write("help", "SOS call to $who: no phone app ($e)")
            false
        }
    }

    /**
     * Logs whether the phone went into the call. Before, the log said only "call started", and
     * the app was back on screen 1.7 to 3.5 s later (06:15:59, 06:16:37, 06:16:58), which could
     * be the call or a question the phone asked.
     */
    private fun watchCall(who: String) {
        callWatch?.let(handler::removeCallbacks)
        val audio = activity.getSystemService(AudioManager::class.java)
        val since = SystemClock.elapsedRealtime()
        val check = object : Runnable {
            override fun run() {
                val waited = (SystemClock.elapsedRealtime() - since) / 1_000
                when {
                    audio.mode == AudioManager.MODE_IN_CALL -> AppLog.write("help", "SOS call to $who: the phone is in the call ($waited s)")
                    waited >= CALL_WATCH_S -> AppLog.write("help", "SOS call to $who: no call after $waited s (asked which SIM, no signal, or ended)")
                    else -> handler.postDelayed(this, 1_000)
                }
            }
        }
        callWatch = check
        handler.postDelayed(check, 1_000)
    }

    private fun textError(code: Int) = when (code) {
        SmsManager.RESULT_ERROR_NO_SERVICE -> "no network service"
        SmsManager.RESULT_ERROR_RADIO_OFF -> "radio off, airplane mode?"
        SmsManager.RESULT_ERROR_NULL_PDU -> "message could not be built"
        SmsManager.RESULT_ERROR_GENERIC_FAILURE -> "failed"
        SmsManager.RESULT_NO_DEFAULT_SMS_APP -> "no SIM for texts"
        else -> "code $code"
    }

    private companion object {
        /** Longest the call waits for the text's result; a sent text reports in about 1 to 3 s. */
        const val TEXT_WAIT_MS = 5_000L

        /** How long the log waits for the phone to go into the call. */
        const val CALL_WATCH_S = 20L
    }
}
