package com.lightningstudio.watchrss.ui.util

import android.content.Context
import android.os.Handler
import android.os.Looper
import android.widget.Toast
import java.util.concurrent.atomic.AtomicLong

private val toastHandler by lazy { Handler(Looper.getMainLooper()) }
private val currentAppToast = LatestToastSlot<Toast> { it.cancel() }
private val appToastRequestId = AtomicLong(0L)

fun showAppToast(
    context: Context,
    message: CharSequence?,
    duration: Int = Toast.LENGTH_SHORT
) {
    val safeMessage = normalizeUserFacingMessage(context, message) ?: return
    val appContext = context.applicationContext
    val requestId = appToastRequestId.incrementAndGet()
    val show = Runnable {
        if (requestId != appToastRequestId.get()) return@Runnable
        currentAppToast.replace {
            Toast.makeText(appContext, safeMessage, duration).also { it.show() }
        }
    }
    if (Looper.myLooper() == Looper.getMainLooper()) show.run() else toastHandler.post(show)
}
