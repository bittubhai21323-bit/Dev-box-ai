package com.example.devboxai.security

import android.app.ActivityManager
import android.content.Context
import android.content.pm.ApplicationInfo
import android.content.pm.PackageManager
import com.example.devboxai.data.BlockedAppDao
import com.example.devboxai.data.SecurityLog
import com.example.devboxai.data.SecurityLogDao
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

class ProcessGuardian(
    private val context: Context,
    private val blockedDao: BlockedAppDao,
    private val logDao: SecurityLogDao
) {
    suspend fun onForegroundAppChanged(pkg: String): Boolean {
        if (pkg == context.packageName) return false
        val blocked = blockedDao.get(pkg) ?: return false
        killBackgroundProcess(pkg)
        logDao.insert(SecurityLog(
            event = "BLOCKED_APP", packageName = pkg,
            details = "Blocked foreground: ${blocked.appLabel}. Reason: ${blocked.reason.ifBlank { "user rule" }}",
            severity = "danger"
        ))
        return true
    }

    fun killBackgroundProcess(pkg: String) {
        try {
            context.getSystemService(ActivityManager::class.java).killBackgroundProcesses(pkg)
        } catch (_: Exception) {}
    }

    suspend fun scanRiskyApps(): List<RiskyApp> = withContext(Dispatchers.IO) {
        val pm = context.packageManager
        val risky = mutableListOf<RiskyApp>()
        val flags = PackageManager.GET_PERMISSIONS
        pm.getInstalledApplications(flags).forEach { app ->
            if (app.packageName == context.packageName) return@forEach
            if ((app.flags and ApplicationInfo.FLAG_SYSTEM) != 0) return@forEach
            val pkgInfo = pm.getPackageInfo(app.packageName, flags)
            val perms = pkgInfo.requestedPermissions ?: return@forEach
            val dangerList = mutableListOf<String>()
            for (p in perms) when (p) {
                "android.permission.SYSTEM_ALERT_WINDOW" -> dangerList.add("Overlay attack")
                "android.permission.BIND_ACCESSIBILITY_SERVICE" -> dangerList.add("Accessibility abuse")
                "android.permission.REQUEST_INSTALL_PACKAGES" -> dangerList.add("APK installer")
                "android.permission.READ_SMS" -> dangerList.add("Reads SMS (OTP theft)")
                "android.permission.RECEIVE_SMS" -> dangerList.add("Receives SMS")
                "android.permission.CALL_PHONE" -> dangerList.add("Auto-dials")
                "android.permission.PROCESS_OUTGOING_CALLS" -> dangerList.add("Call interception")
                "android.permission.RECORD_AUDIO" -> dangerList.add("Mic")
                "android.permission.CAMERA" -> dangerList.add("Camera")
                "android.permission.READ_CONTACTS" -> dangerList.add("Contacts")
                "android.permission.ACCESS_FINE_LOCATION" -> dangerList.add("Precise location")
            }
            if (dangerList.isNotEmpty()) {
                risky.add(RiskyApp(app.packageName,
                    pm.getApplicationLabel(app).toString(), dangerList.distinct()))
            }
        }
        risky.sortedByDescending { it.risks.size }
    }

    suspend fun log(event: String, pkg: String?, details: String, severity: String = "info") {
        logDao.insert(SecurityLog(event = event, packageName = pkg, details = details, severity = severity))
    }

    data class RiskyApp(val packageName: String, val label: String, val risks: List<String>)
}
