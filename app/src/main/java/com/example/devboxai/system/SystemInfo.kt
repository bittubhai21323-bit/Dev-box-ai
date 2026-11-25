package com.example.devboxai.system

import android.app.ActivityManager
import android.content.Context
import android.os.BatteryManager
import android.os.Build

object SystemInfo {
    fun summary(context: Context): String {
        val am = context.getSystemService(ActivityManager::class.java)
        val mem = ActivityManager.MemoryInfo()
        am.getMemoryInfo(mem)
        val bm = context.getSystemService(BatteryManager::class.java)
        val battery = bm.getIntProperty(BatteryManager.BATTERY_PROPERTY_CAPACITY)
        return """
            Device: ${Build.MANUFACTURER} ${Build.MODEL}
            Android: ${Build.VERSION.RELEASE} (SDK ${Build.VERSION.SDK_INT})
            RAM: ${mem.totalMem / 1024 / 1024} MB
            Available: ${mem.availMem / 1024 / 1024} MB
            Battery: $battery%
        """.trimIndent()
    }
}
