package com.example.devboxai.update

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.pm.PackageInstaller
import com.example.devboxai.NotificationHelper

class InstallResultReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val status = intent.getIntExtra(PackageInstaller.EXTRA_STATUS, PackageInstaller.STATUS_FAILURE)
        if (status == PackageInstaller.STATUS_PENDING_USER_ACTION) {
            // Must launch the confirmation intent, otherwise install stalls silently
            // EXTRA_INTENT constant is not available in this compileSdk; use the literal key
            @Suppress("DEPRECATION")
            val confirm = intent.getParcelableExtra<Intent>("android.content.pm.extra.INTENT")
            if (confirm != null) {
                confirm.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                try { context.startActivity(confirm) } catch (_: Exception) {}
            }
            return
        }
        val msg = when (status) {
            PackageInstaller.STATUS_SUCCESS -> "✓ Update installed"
            PackageInstaller.STATUS_FAILURE -> "✗ Install failed"
            else -> "Install status: $status"
        }
        NotificationHelper.show(context, "Update", msg)
    }
}
