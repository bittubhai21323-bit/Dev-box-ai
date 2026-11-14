package com.example.devboxai

import android.content.Context
import android.content.Intent

object AppManager {

    fun installedApps(context: Context): List<String> {
        val pm = context.packageManager
        val intent = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER)
        return pm.queryIntentActivities(intent, 0)
            .map { it.loadLabel(pm).toString() }
            .distinct()
            .sorted()
    }
}
