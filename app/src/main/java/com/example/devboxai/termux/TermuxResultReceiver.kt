package com.example.devboxai.termux

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.util.Log

class TermuxResultReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val bundle: Bundle = intent.getBundleExtra("result") ?: Bundle()
        val stdout = bundle.getString("stdout") ?: ""
        val stderr = bundle.getString("stderr") ?: ""
        val exit = bundle.getInt("exitCode", -1)
        val reqId = intent.getIntExtra(EXTRA_REQUEST_ID, -1)
        Log.d("TermuxResultReceiver", "req=$reqId exit=$exit stdoutLen=${stdout.length}")
        TermuxResultBus.publish(TermuxResultBus.Result(reqId, stdout, stderr, exit))
    }

    companion object {
        const val ACTION = "com.example.devboxai.TERMUX_RESULT"
        const val EXTRA_REQUEST_ID = "request_id"
    }
}
