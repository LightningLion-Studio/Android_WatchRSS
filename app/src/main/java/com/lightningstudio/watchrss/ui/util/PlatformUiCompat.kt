package com.lightningstudio.watchrss.ui.util

import android.content.Context
import android.widget.Toast

fun showAppToast(
    context: Context,
    message: CharSequence?,
    duration: Int = Toast.LENGTH_SHORT
) {
    val safeMessage = normalizeUserFacingMessage(context, message) ?: return
    Toast.makeText(context, safeMessage, duration).show()
}
