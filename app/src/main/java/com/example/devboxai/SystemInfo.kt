package com.example.devboxai

import android.app.ActivityManager
import android.content.Context
import android.os.BatteryManager
import android.os.Build

object SystemInfo {

    fun summary(context: Context): String {
        val activityManager = context.getSystemService(ActivityManager::class.java)
        val memoryInfo = ActivityManager.MemoryInfo()
        activityManager.getMemoryInfo(memoryInfo)

        val batteryManager = context.getSystemService(BatteryManager::class.java)
        val battery = batteryManager.getIntProperty(BatteryManager.BATTERY_PROPERTY_CAPACITY)

        return """
            Device: ${Build.MANUFACTURER} ${Build.MODEL}
            Android: ${Build.VERSION.RELEASE}
            SDK: ${Build.VERSION.SDK_INT}
            RAM: ${memoryInfo.totalMem / 1024 / 1024} MB
            Available RAM: ${memoryInfo.availMem / 1024 / 1024} MB
            Battery: $battery%
        """.trimIndent()
    }
}
