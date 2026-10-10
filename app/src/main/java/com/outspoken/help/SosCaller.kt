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
import android.net.Uri
import android.telephony.SmsManager
import androidx.core.content.ContextCompat
import com.outspoken.log.AppLog

/**
 * Texts and then calls the SOS contact when the help alarm sounds, over the phone network: no
 * internet. The text goes first, as it arrives even when the call is not picked up. Each step
 * and its outcome go in the log, with the number masked. [onStatus] gets a short line for the
 * help screen.
 */
class SosCaller(private val activity: Activity, private val onStatus: (String) -> Unit) {

    private val sentAction = "${activity.packageName}.SOS_TEXT_SENT"
    private val sentReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            val sent = resultCode == Activity.RESULT_OK
            AppLog.write("help", if (sent) "SOS text sent" else "SOS text not sent (${textError(resultCode)})")
        }
    }

    init {
        ContextCompat.registerReceiver(activity, sentReceiver, IntentFilter(sentAction), ContextCompat.RECEIVER_NOT_EXPORTED)
    }

    fun release() {
        runCatching { activity.unregisterReceiver(sentReceiver) }
    }

    fun has(permission: String) = ContextCompat.checkSelfPermission(activity, permission) == PackageManager.PERMISSION_GRANTED

    /** Texts [message] if [text], then calls if [call]; [number] as [sosNumber] gives it. */
    fun alert(number: String, message: String, text: Boolean, call: Boolean) {
        val who = maskedNumber(number)
        val done = mutableListOf<String>()
        if (text) {
            if (sendText(number, message)) done += "texting" else AppLog.write("help", "SOS text to $who skipped: no permission to send texts")
        }
        if (call) {
            when {
                isEmergencyNumber(number) -> AppLog.write("help", "SOS call to $who skipped: Android lets no app call emergency numbers itself")
                placeCall(number) -> done += "calling"
                else -> Unit
            }
        }
        onStatus(if (done.isEmpty()) "Could not reach the SOS contact; see the log" else "${done.joinToString(" and ").replaceFirstChar { it.uppercase() }} $who")
    }

    private fun sendText(number: String, message: String): Boolean {
        if (ContextCompat.checkSelfPermission(activity, Manifest.permission.SEND_SMS) != PackageManager.PERMISSION_GRANTED) return false
        return runCatching {
            val sms = activity.getSystemService(SmsManager::class.java)
            val parts = sms.divideMessage(message)
            val sent = PendingIntent.getBroadcast(
                activity,
                0,
                Intent(sentAction).setPackage(activity.packageName),
                PendingIntent.FLAG_IMMUTABLE,
            )
            sms.sendMultipartTextMessage(number, null, parts, ArrayList(List(parts.size) { sent }), null)
            AppLog.write("help", "SOS text to ${maskedNumber(number)} handed to the phone, ${parts.size} part(s)")
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
            true
        } catch (e: SecurityException) {
            AppLog.write("help", "SOS call to $who refused: $e")
            false
        } catch (e: ActivityNotFoundException) {
            AppLog.write("help", "SOS call to $who: no phone app ($e)")
            false
        }
    }

    private fun textError(code: Int) = when (code) {
        SmsManager.RESULT_ERROR_NO_SERVICE -> "no network service"
        SmsManager.RESULT_ERROR_RADIO_OFF -> "radio off, airplane mode?"
        SmsManager.RESULT_ERROR_NULL_PDU -> "message could not be built"
        SmsManager.RESULT_ERROR_GENERIC_FAILURE -> "failed"
        else -> "code $code"
    }
}
