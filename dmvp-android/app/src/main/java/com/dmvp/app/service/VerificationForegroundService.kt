package com.dmvp.app.service

import android.app.Service
import android.content.Intent
import android.os.IBinder
import android.util.Log
import androidx.core.app.NotificationCompat

class VerificationForegroundService : Service() {
    companion object {
        const val NOTIFICATION_ID = 1001
        const val NOTIFICATION_CHANNEL_ID = "dmvp_verification"
    }
    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        Log.d("VerificationService", "Service started")
        createNotification()
        return START_STICKY
    }
    override fun onBind(intent: Intent?): IBinder? = null
    private fun createNotification() {
        val notification = NotificationCompat.Builder(this, NOTIFICATION_CHANNEL_ID)
            .setContentTitle("DMVP Media Verification")
            .setContentText("Analyzing media...")
            .setSmallIcon(android.R.drawable.ic_dialog_info)
            .build()
        startForeground(NOTIFICATION_ID, notification)
    }
}
