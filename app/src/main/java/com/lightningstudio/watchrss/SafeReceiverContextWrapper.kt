package com.lightningstudio.watchrss

import android.content.BroadcastReceiver
import android.content.Context
import android.content.ContextWrapper
import android.content.Intent
import android.content.IntentFilter
import android.os.Build
import android.os.linearmotorvibrator.LinearmotorVibrator

class SafeReceiverContextWrapper(base: Context) : ContextWrapper(base) {
    override fun registerReceiver(receiver: BroadcastReceiver?, filter: IntentFilter?): Intent? {
        return if (Build.VERSION.SDK_INT >= 33) {
            super.registerReceiver(receiver, filter, Context.RECEIVER_NOT_EXPORTED)
        } else {
            super.registerReceiver(receiver, filter)
        }
    }

    override fun getSystemService(name: String): Any? {
        val service = super.getSystemService(name)
        if (service != null) {
            return service
        }
        return if (name == LINEAR_MOTOR_SERVICE) {
            LinearmotorVibrator()
        } else {
            null
        }
    }

    private companion object {
        const val LINEAR_MOTOR_SERVICE = "linearmotor"
    }
}
