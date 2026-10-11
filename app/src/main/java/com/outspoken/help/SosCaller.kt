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
import android.telecom.PhoneAccountHandle
import android.telecom.TelecomManager
import android.telephony.SmsManager
import android.telephony.SubscriptionManager
import android.telephony.TelephonyManager
import androidx.core.content.ContextCompat
import com.outspoken.log.AppLog

/**
 * Texts and then calls the SOS contact when the help alarm sounds, over the phone network: no
 * internet. The text goes first, as it arrives even when the call is not picked up; the call
 * waits for the text to be sent, up to [TEXT_WAIT_MS]. Both go out on a SIM the app picks, so the
 * phone never asks which SIM to use. Each step and its outcome go in the log, with the number
 * masked. [onStatus] gets a short line for the help screen.
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

    /**
     * Whether the alarm can text and call without the phone asking anything: texts and calls
     * allowed, and, when no SIM is set for calls, the phone state too, to pick one.
     */
    fun allowed(): Boolean = has(Manifest.permission.SEND_SMS) && has(Manifest.permission.CALL_PHONE) &&
        (has(Manifest.permission.READ_PHONE_STATE) || SubscriptionManager.isValidSubscriptionId(SubscriptionManager.getDefaultVoiceSubscriptionId()))

    /** Texts [message] if [text], then calls if [call]; [number] as [sosNumber] gives it. */
    fun alert(number: String, message: String, text: Boolean, call: Boolean) {
        handler.removeCallbacksAndMessages(null)
        pendingCall = null
        val who = maskedNumber(number)
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
        val account = callAccount(who)
        return try {
            val intent = Intent(Intent.ACTION_CALL, Uri.fromParts("tel", number, null))
            account?.let { intent.putExtra(TelecomManager.EXTRA_PHONE_ACCOUNT_HANDLE, it) }
            activity.startActivity(intent)
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
     * The SIM to call from, when the phone has none set for calls and would ask; null to let the
     * phone use its own. Finding it needs the phone state permission.
     */
    private fun callAccount(who: String): PhoneAccountHandle? {
        val sim = sosSim(SubscriptionManager.getDefaultVoiceSubscriptionId(), SubscriptionManager.getDefaultSubscriptionId(), "calls")
        if (SubscriptionManager.isValidSubscriptionId(SubscriptionManager.getDefaultVoiceSubscriptionId())) {
            AppLog.write("help", "SOS call to $who on ${sim.why}")
            return null
        }
        if (ContextCompat.checkSelfPermission(activity, Manifest.permission.READ_PHONE_STATE) != PackageManager.PERMISSION_GRANTED) {
            AppLog.write("help", "SOS call to $who: no SIM set for calls and no permission to pick one; the phone may ask which SIM to use")
            return null
        }
        val subId = sim.subId ?: return null
        return try {
            val telecom = activity.getSystemService(TelecomManager::class.java)
            val telephony = activity.getSystemService(TelephonyManager::class.java)
            telecom.callCapablePhoneAccounts.firstOrNull { telephony.getSubscriptionId(it) == subId }.also {
                AppLog.write("help", if (it != null) "SOS call to $who on SIM $subId (${sim.why})" else "SOS call to $who: SIM $subId has no calling account; the phone may ask which SIM to use")
            }
        } catch (e: SecurityException) {
            AppLog.write("help", "SOS call to $who: could not pick a SIM ($e)")
            null
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
        SmsManager.RESULT_NO_DEFAULT_SMS_APP -> "no SIM chosen for texts"
        else -> "code $code"
    }

    private companion object {
        /** Longest the call waits for the text's result; a sent text reports in about 1 to 3 s. */
        const val TEXT_WAIT_MS = 5_000L

        /** How long the log waits for the phone to go into the call. */
        const val CALL_WATCH_S = 20L
    }
}
