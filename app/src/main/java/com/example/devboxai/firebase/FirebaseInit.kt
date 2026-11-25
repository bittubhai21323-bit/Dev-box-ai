package com.example.devboxai.firebase

import com.google.firebase.FirebaseApp
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.messaging.FirebaseMessaging

object FirebaseInit {

    private fun app(): FirebaseApp? = FirebaseBootstrap.app()

    val auth: FirebaseAuth?
        get() = app()?.let { FirebaseAuth.getInstance(it) }
    val firestore: FirebaseFirestore?
        get() = app()?.let { FirebaseFirestore.getInstance(it) }
    val messaging: FirebaseMessaging?
        get() = app()?.let { FirebaseMessaging.getInstance(it) }

    const val DOC_CONFIG = "devbox/config"
    const val COLL_COMMANDS = "devbox_commands"
    const val COLL_TELEMETRY = "devbox_telemetry"
    const val COLL_DEVICES = "devbox_devices"

    fun registerFcmToken(onToken: (String?) -> Unit) {
        val m = messaging ?: run { onToken(null); return }
        m.token.addOnCompleteListener { task ->
            onToken(if (task.isSuccessful) task.result else null)
        }
    }
}
