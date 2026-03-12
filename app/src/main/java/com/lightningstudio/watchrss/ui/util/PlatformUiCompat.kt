package com.lightningstudio.watchrss.ui.util

import android.content.Context
import android.widget.Toast

fun showAppToast(
    context: Context,
    message: CharSequence?,
    duration: Int = Toast.LENGTH_SHORT
) {
    if (message.isNullOrEmpty()) return
    Toast.makeText(context, message, duration).show()
}
