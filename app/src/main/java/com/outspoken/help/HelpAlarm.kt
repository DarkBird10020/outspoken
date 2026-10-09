package com.outspoken.help

import android.content.Context
import android.media.AudioAttributes
import android.media.AudioManager
import android.media.Ringtone
import android.media.RingtoneManager
import com.outspoken.log.AppLog

/**
 * The loud part of the help alarm (PRD F8): the phone's alarm sound on the alarm stream, looping,
 * at full volume until someone comes. The volume goes back to where it was when it stops.
 */
class HelpAlarm(private val context: Context) {

    private val audio = context.getSystemService(AudioManager::class.java)
    private var ringtone: Ringtone? = null
    private var savedVolume: Int? = null

    fun start() {
        if (ringtone != null) return
        val sound = RingtoneManager.getActualDefaultRingtoneUri(context, RingtoneManager.TYPE_ALARM)
            ?: RingtoneManager.getDefaultUri(RingtoneManager.TYPE_ALARM)
            ?: RingtoneManager.getDefaultUri(RingtoneManager.TYPE_RINGTONE)
        // Do Not Disturb can refuse volume changes; the alarm still sounds at the set volume.
        runCatching {
            savedVolume = audio.getStreamVolume(AudioManager.STREAM_ALARM)
            audio.setStreamVolume(AudioManager.STREAM_ALARM, audio.getStreamMaxVolume(AudioManager.STREAM_ALARM), 0)
        }.onFailure { AppLog.write("help", "could not raise the alarm volume: ${it.message}") }
        ringtone = RingtoneManager.getRingtone(context, sound)?.apply {
            audioAttributes = AudioAttributes.Builder()
                .setUsage(AudioAttributes.USAGE_ALARM)
                .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                .build()
            isLooping = true
            play()
        }
        AppLog.write("help", if (ringtone == null) "no alarm sound found on this phone" else "alarm sounding")
    }

    fun stop() {
        val playing = ringtone ?: return
        playing.stop()
        ringtone = null
        savedVolume?.let { volume -> runCatching { audio.setStreamVolume(AudioManager.STREAM_ALARM, volume, 0) } }
        savedVolume = null
        AppLog.write("help", "alarm stopped")
    }
}
