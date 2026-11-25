package com.example.devboxai.service

import android.app.Notification
import android.app.Service
import android.content.Intent
import android.os.IBinder
import com.example.devboxai.NotificationHelper

class SyncService : Service() {
    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        NotificationHelper.createChannel(this)
        val notif: Notification = Notification.Builder(this, NotificationHelper.CHANNEL)
            .setSmallIcon(android.R.drawable.ic_dialog_info)
            .setContentTitle("DevBox AI").setContentText("Background service running")
            .setOngoing(true).build()
        startForeground(100, notif)
        return START_STICKY
    }
    override fun onBind(intent: Intent?): IBinder? = null
}
