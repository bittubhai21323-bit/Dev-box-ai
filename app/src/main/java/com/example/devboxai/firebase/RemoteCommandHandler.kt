package com.example.devboxai.firebase

import android.content.Context
import android.os.Build
import android.util.Log
import com.example.devboxai.DevBoxApp
import com.example.devboxai.NotificationHelper
import com.example.devboxai.data.BlockedApp
import com.example.devboxai.data.SecurityLog
import com.example.devboxai.security.ProcessGuardian
import com.example.devboxai.security.RootProcessManager
import com.example.devboxai.termux.TermuxHelper
import com.google.firebase.firestore.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withContext

class RemoteCommandHandler(
    private val context: Context,
    private val deviceId: String
) {
    private val app = context.applicationContext as DevBoxApp
    private var listener: ListenerRegistration? = null

    fun startListening() {
        listener?.remove()
        val db = FirebaseInit.firestore ?: return
        listener = db.collection(FirebaseInit.COLL_COMMANDS)
            .whereEqualTo("status", "pending")
            .addSnapshotListener { snaps, err ->
                if (err != null) { Log.e("RCH", "listen err", err); return@addSnapshotListener }
                snaps?.documents?.forEach { doc ->
                    val target = doc.getString("targetDeviceId") ?: "*"
                    if (target != "*" && target != deviceId) return@forEach
                    processCommand(doc)
                }
            }
    }

    fun stopListening() { listener?.remove(); listener = null }

    private fun processCommand(doc: DocumentSnapshot) {
        val db = FirebaseInit.firestore ?: return
        val action = doc.getString("action") ?: return
        val pkg = doc.getString("packageName") ?: ""
        val reason = doc.getString("reason") ?: ""
        val shellCmd = doc.getString("command") ?: ""

        kotlinx.coroutines.CoroutineScope(Dispatchers.IO).launch {
            val (ok, msg) = when (action) {
                "block_app" -> blockApp(pkg, reason)
                "unblock_app" -> unblockApp(pkg)
                "kill_app" -> killApp(pkg)
                "freeze_app" -> freezeApp(pkg)
                "unfreeze_app" -> unfreezeApp(pkg)
                "scan" -> scanRisky()
                "notify" -> notify(reason)
                "wipe" -> wipeLocal()
                "shell" -> runShell(shellCmd)
                else -> false to "unknown: $action"
            }
            logRemote(action, pkg, msg, ok)
            try {
                db.collection(FirebaseInit.COLL_COMMANDS).document(doc.id)
                    .update(mapOf(
                        "status" to if (ok) "done" else "failed",
                        "result" to msg,
                        "completedAt" to FieldValue.serverTimestamp(),
                        "completedBy" to deviceId,
                        "deviceModel" to "${Build.MANUFACTURER} ${Build.MODEL}"
                    )).await()
            } catch (_: Exception) {}
            NotificationHelper.show(context,
                if (ok) "✅ Remote: $action" else "❌ Remote failed: $action", msg)
        }
    }

    private suspend fun blockApp(pkg: String, reason: String): Pair<Boolean, String> {
        if (pkg.isBlank()) return false to "no package"
        app.db.blockedAppDao().insert(BlockedApp(packageName = pkg, appLabel = pkg, reason = reason))
        return true to "Blocked $pkg"
    }
    private suspend fun unblockApp(pkg: String): Pair<Boolean, String> {
        app.db.blockedAppDao().deleteByPkg(pkg); return true to "Unblocked $pkg"
    }
    private suspend fun killApp(pkg: String): Pair<Boolean, String> {
        val ok = RootProcessManager.killApp(context, pkg)
        return ok to if (ok) "kill -9 $pkg" else "root needed"
    }
    private suspend fun freezeApp(pkg: String): Pair<Boolean, String> {
        val ok = RootProcessManager.freezeApp(context, pkg)
        return ok to if (ok) "SIGSTOP $pkg" else "root needed"
    }
    private suspend fun unfreezeApp(pkg: String): Pair<Boolean, String> {
        val ok = RootProcessManager.unfreezeApp(context, pkg)
        return ok to if (ok) "SIGCONT $pkg" else "root needed"
    }
    private suspend fun scanRisky(): Pair<Boolean, String> = withContext(Dispatchers.IO) {
        return@withContext try {
            val guardian = ProcessGuardian(context, app.db.blockedAppDao(), app.db.securityLogDao())
            val list = guardian.scanRiskyApps()
            val db = FirebaseInit.firestore
            if (db != null) {
                val batch = db.batch()
                list.take(30).forEach { r ->
                    val ref = db.collection(FirebaseInit.COLL_TELEMETRY).document()
                    batch.set(ref, mapOf(
                        "deviceId" to deviceId, "type" to "risky_app",
                        "label" to r.label, "packageName" to r.packageName,
                        "risks" to r.risks, "timestamp" to FieldValue.serverTimestamp()
                    ))
                }
                batch.commit().await()
            }
            true to "Scanned ${list.size} risky apps"
        } catch (e: Exception) { false to "scan err: ${e.message}" }
    }
    private fun notify(msg: String): Pair<Boolean, String> {
        NotificationHelper.show(context, "📩 Admin", msg.ifBlank { "hello" })
        return true to "notified"
    }
    private suspend fun wipeLocal(): Pair<Boolean, String> = withContext(Dispatchers.IO) {
        return@withContext try {
            app.db.securityLogDao().clear(); true to "Local logs cleared"
        } catch (e: Exception) { false to "wipe err: ${e.message}" }
    }
    private suspend fun runShell(cmd: String): Pair<Boolean, String> {
        if (cmd.isBlank()) return false to "no command"
        val ok = TermuxHelper.run(context, cmd)
        return ok to "shell sent"
    }
    private suspend fun logRemote(action: String, pkg: String, msg: String, ok: Boolean) {
        app.db.securityLogDao().insert(SecurityLog(
            event = "REMOTE_${action.uppercase()}",
            packageName = pkg.ifBlank { null },
            details = "$msg [${if (ok) "OK" else "FAIL"}]",
            severity = if (ok) "info" else "warn"
        ))
        try {
            FirebaseInit.firestore?.collection(FirebaseInit.COLL_TELEMETRY)?.add(
                mapOf(
                    "deviceId" to deviceId, "type" to "remote_action",
                    "action" to action, "packageName" to pkg,
                    "result" to msg, "ok" to ok,
                    "timestamp" to FieldValue.serverTimestamp()
                )
            )?.await()
        } catch (_: Exception) {}
    }
}
