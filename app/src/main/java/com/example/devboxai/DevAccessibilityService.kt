package com.example.devboxai

import android.accessibilityservice.AccessibilityService
import android.accessibilityservice.GestureDescription
import android.graphics.Path
import android.os.Bundle
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityNodeInfo
import com.example.devboxai.security.ProcessGuardian
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

class DevAccessibilityService : AccessibilityService() {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private var guardian: ProcessGuardian? = null

    override fun onServiceConnected() {
        super.onServiceConnected()
        instance = this
        val app = application as DevBoxApp
        guardian = ProcessGuardian(this, app.db.blockedAppDao(), app.db.securityLogDao())
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        if (event?.eventType != AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED) return
        val pkg = event.packageName?.toString() ?: return
        if (pkg == packageName) return

        scope.launch {
            val blocked = guardian?.onForegroundAppChanged(pkg) ?: false
            if (blocked) {
                val label = try {
                    val pm = packageManager
                    pm.getApplicationLabel(pm.getApplicationInfo(pkg, 0)).toString()
                } catch (_: Exception) { pkg }

                scope.launch(Dispatchers.Main) {
                    globalHome()
                    NotificationHelper.show(
                        this@DevAccessibilityService,
                        "🚫 Blocked",
                        "$label is on your block list"
                    )
                }
            }
        }
    }

    override fun onInterrupt() {}
    override fun onDestroy() { instance = null; super.onDestroy() }

    fun root(): AccessibilityNodeInfo? = rootInActiveWindow
    fun currentPackage(): String? = rootInActiveWindow?.packageName?.toString()
    fun findText(text: String): AccessibilityNodeInfo? =
        rootInActiveWindow?.findAccessibilityNodeInfosByText(text)?.firstOrNull()

    fun findViewId(id: String): AccessibilityNodeInfo? =
        rootInActiveWindow?.findAccessibilityNodeInfosByViewId(id)?.firstOrNull()

    fun clickNode(node: AccessibilityNodeInfo?): Boolean {
        var n = node ?: return false
        while (true) {
            if (n.isClickable) return n.performAction(AccessibilityNodeInfo.ACTION_CLICK)
            n = n.parent ?: return false
        }
    }

    fun setTextOnFocused(text: String): Boolean {
        val root = rootInActiveWindow ?: return false
        val focused = root.findFocus(AccessibilityNodeInfo.FOCUS_INPUT) ?: return false
        if (!focused.isEditable) return false
        val args = Bundle().apply {
            putCharSequence(AccessibilityNodeInfo.ACTION_ARGUMENT_SET_TEXT_CHARSEQUENCE, text)
        }
        return focused.performAction(AccessibilityNodeInfo.ACTION_SET_TEXT, args)
    }

    fun tap(x: Float, y: Float): Boolean {
        val path = Path().apply { moveTo(x, y); lineTo(x, y) }
        return dispatchGesture(
            GestureDescription.Builder()
                .addStroke(GestureDescription.StrokeDescription(path, 0, 60)).build(),
            null, null
        )
    }

    fun swipe(x1: Float, y1: Float, x2: Float, y2: Float, durationMs: Long = 300): Boolean {
        val path = Path().apply { moveTo(x1, y1); lineTo(x2, y2) }
        return dispatchGesture(
            GestureDescription.Builder()
                .addStroke(GestureDescription.StrokeDescription(path, 0, durationMs)).build(),
            null, null
        )
    }

    fun globalBack() = performGlobalAction(GLOBAL_ACTION_BACK)
    fun globalHome() = performGlobalAction(GLOBAL_ACTION_HOME)
    fun globalRecents() = performGlobalAction(GLOBAL_ACTION_RECENTS)
    fun globalNotifications() = performGlobalAction(GLOBAL_ACTION_NOTIFICATIONS)
    fun globalQuickSettings() = performGlobalAction(GLOBAL_ACTION_QUICK_SETTINGS)
    fun globalPowerDialog() = performGlobalAction(GLOBAL_ACTION_POWER_DIALOG)
    fun globalLockScreen() = performGlobalAction(GLOBAL_ACTION_LOCK_SCREEN)
    fun globalScreenshot(): Boolean =
        if (android.os.Build.VERSION.SDK_INT >= 30) performGlobalAction(GLOBAL_ACTION_TAKE_SCREENSHOT)
        else false

    fun readScreenText(): String {
        val root = rootInActiveWindow ?: return ""
        val sb = StringBuilder()
        walk(root, sb)
        return sb.toString().trim()
    }

    private fun walk(node: AccessibilityNodeInfo?, sb: StringBuilder) {
        if (node == null) return
        node.text?.toString()?.takeIf { it.isNotBlank() }?.let { sb.append(it).append("\n") }
        node.contentDescription?.toString()?.takeIf { it.isNotBlank() }?.let { sb.append(it).append("\n") }
        for (i in 0 until node.childCount) walk(node.getChild(i), sb)
    }

    companion object {
        @Volatile var instance: DevAccessibilityService? = null
            private set
        fun isRunning(): Boolean = instance != null
    }
}
