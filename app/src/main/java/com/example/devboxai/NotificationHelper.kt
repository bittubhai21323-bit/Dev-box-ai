package com.example.devboxai

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.graphics.BitmapFactory
import androidx.core.app.NotificationManagerCompat

object NotificationHelper {

    const val CHANNEL = "devbox_channel"

    fun createChannel(context: Context) {
        val mgr = context.getSystemService(NotificationManager::class.java)
        if (mgr.getNotificationChannel(CHANNEL) == null) {
            mgr.createNotificationChannel(
                NotificationChannel(CHANNEL, "DevBox", NotificationManager.IMPORTANCE_DEFAULT)
            )
        }
    }

    fun show(context: Context, title: String, text: String) {
        createChannel(context)
        val largeIcon = BitmapFactory.decodeResource(context.resources, R.drawable.avatar)
        val notif = Notification.Builder(context, CHANNEL)
            .setSmallIcon(android.R.drawable.ic_dialog_info)
            .setLargeIcon(largeIcon)
            .setContentTitle(title)
            .setContentText(text)
            .setAutoCancel(true)
            .build()
        try {
            NotificationManagerCompat.from(context)
                .notify((System.currentTimeMillis() % 100000).toInt(), notif)
        } catch (_: SecurityException) {}
    }
}
