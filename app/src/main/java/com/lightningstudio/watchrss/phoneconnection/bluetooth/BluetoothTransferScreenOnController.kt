package com.lightningstudio.watchrss.phoneconnection.bluetooth

import android.app.Activity
import android.os.Handler
import android.os.Looper
import android.view.WindowManager
import java.lang.ref.WeakReference
import java.util.concurrent.atomic.AtomicInteger

internal class BluetoothTransferScreenOnController {
    private val mainHandler = Handler(Looper.getMainLooper())
    private val transferGeneration = AtomicInteger()
    private var currentActivityRef: WeakReference<Activity>? = null
    private var appliedActivityRef: WeakReference<Activity>? = null
    private var hadKeepScreenOnBeforeApply = false
    private var transferInProgress = false

    fun setCurrentActivity(activity: Activity?) {
        runOnMain {
            val currentActivity = currentActivityRef?.get()
            if (currentActivity === activity) return@runOnMain
            clearAppliedFlag()
            currentActivityRef = activity?.let(::WeakReference)
            if (transferInProgress) {
                applyTo(activity)
            }
        }
    }

    fun clearActivity(activity: Activity) {
        runOnMain {
            if (currentActivityRef?.get() === activity) {
                currentActivityRef = null
            }
            if (appliedActivityRef?.get() === activity) {
                clearAppliedFlag()
            }
        }
    }

    fun setTransferInProgress(inProgress: Boolean) {
        val generation = transferGeneration.incrementAndGet()
        runOnMain {
            if (generation != transferGeneration.get()) return@runOnMain
            if (transferInProgress == inProgress) return@runOnMain
            transferInProgress = inProgress
            if (inProgress) {
                applyTo(currentActivityRef?.get())
            } else {
                clearAppliedFlag()
            }
        }
    }

    private fun applyTo(activity: Activity?) {
        if (activity == null || activity.isFinishing || activity.isDestroyed) return
        val window = activity.window ?: return
        if (appliedActivityRef?.get() === activity) return
        clearAppliedFlag()
        hadKeepScreenOnBeforeApply = (window.attributes.flags and WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON) != 0
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        appliedActivityRef = WeakReference(activity)
    }

    private fun clearAppliedFlag() {
        val activity = appliedActivityRef?.get()
        if (activity != null && !hadKeepScreenOnBeforeApply) {
            activity.window?.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        }
        appliedActivityRef = null
        hadKeepScreenOnBeforeApply = false
    }

    private fun runOnMain(block: () -> Unit) {
        if (Looper.myLooper() == Looper.getMainLooper()) {
            block()
        } else {
            mainHandler.post(block)
        }
    }
}
