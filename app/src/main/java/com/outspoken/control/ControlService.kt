package com.outspoken.control

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.IBinder
import androidx.core.content.ContextCompat
import com.outspoken.R
import com.outspoken.log.AppLog

/**
 * Keeps the front camera allowed while another app is in front, for phone control. Android lets
 * an app use the camera only while it is on screen, unless a camera foreground service was
 * started while it was; this is that service. It shows a notification while it runs.
 */
class ControlService : Service() {

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val manager = getSystemService(NotificationManager::class.java)
        manager.createNotificationChannel(NotificationChannel(CHANNEL, "Phone control", NotificationManager.IMPORTANCE_LOW))
        val notification = Notification.Builder(this, CHANNEL)
            .setSmallIcon(R.drawable.ic_eye)
            .setContentTitle("Outspoken is reading your eyes")
            .setContentText("Phone control: the eyes work in other apps too. Tap Stop at the top of the screen to end it.")
            .setOngoing(true)
            .build()
        try {
            startForeground(NOTIFICATION_ID, notification, ServiceInfo.FOREGROUND_SERVICE_TYPE_CAMERA)
            AppLog.write("control", "camera service on")
        } catch (e: RuntimeException) {
            // Android refuses when the app was not on screen when this was started.
            AppLog.write("control", "camera service refused: $e")
            stopSelf()
        }
        return START_NOT_STICKY
    }

    override fun onDestroy() {
        AppLog.write("control", "camera service off")
        super.onDestroy()
    }

    companion object {
        private const val CHANNEL = "control"
        private const val NOTIFICATION_ID = 7

        /** Call while Outspoken is on screen. */
        fun start(context: Context) {
            ContextCompat.startForegroundService(context, Intent(context, ControlService::class.java))
        }

        fun stop(context: Context) {
            context.stopService(Intent(context, ControlService::class.java))
        }
    }
}
