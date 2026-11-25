package com.example.devboxai.security

import android.content.Context
import android.content.Intent
import com.example.devboxai.data.BlockedApp
import com.example.devboxai.data.BlockedAppDao
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

class AppBlocker(private val context: Context, private val dao: BlockedAppDao) {
    suspend fun block(pkg: String, label: String, reason: String = "") = withContext(Dispatchers.IO) {
        dao.insert(BlockedApp(packageName = pkg, appLabel = label, reason = reason))
    }
    suspend fun unblock(pkg: String) = withContext(Dispatchers.IO) { dao.deleteByPkg(pkg) }
    suspend fun installedApps(): List<AppInfo> = withContext(Dispatchers.IO) {
        val pm = context.packageManager
        val intent = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER)
        pm.queryIntentActivities(intent, 0).map {
            AppInfo(it.activityInfo.packageName, it.loadLabel(pm).toString())
        }.distinctBy { it.pkg }.sortedBy { it.label.lowercase() }
    }
    data class AppInfo(val pkg: String, val label: String)
}
