package com.example.devboxai.update

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.pm.PackageInstaller
import com.example.devboxai.NotificationHelper

class InstallResultReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val status = intent.getIntExtra(PackageInstaller.EXTRA_STATUS, PackageInstaller.STATUS_FAILURE)
        val msg = when (status) {
            PackageInstaller.STATUS_SUCCESS -> "✓ Update installed"
            PackageInstaller.STATUS_FAILURE -> "✗ Install failed"
            PackageInstaller.STATUS_PENDING_USER_ACTION -> "User action needed"
            else -> "Install status: $status"
        }
        NotificationHelper.show(context, "Update", msg)
    }
}
