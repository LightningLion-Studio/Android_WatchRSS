package com.lightningstudio.watchrss.ui.util

import android.content.Context
import android.content.pm.PackageManager
import android.os.Build

private const val WEAR_OS_SHARED_LIBRARY = "com.google.android.wearable"
private val WEAR_OS_PACKAGE_MARKERS = arrayOf(
    "com.google.android.wearable.app",
    "com.google.android.wearable.frameworks"
)

fun isSystemShareSettingSupported(context: Context): Boolean {
    return Build.VERSION.SDK_INT >= Build.VERSION_CODES.S && !isWearOsDevice(context)
}

private fun isWearOsDevice(context: Context): Boolean {
    val packageManager = context.packageManager
    // Wear OS 设备通常会暴露该共享库；少数变体再退回到系统包名判断。
    if (packageManager.systemSharedLibraryNames.orEmpty().contains(WEAR_OS_SHARED_LIBRARY)) {
        return true
    }
    return WEAR_OS_PACKAGE_MARKERS.any { packageName ->
        hasInstalledPackage(packageManager, packageName)
    }
}

private fun hasInstalledPackage(
    packageManager: PackageManager,
    packageName: String
): Boolean {
    return runCatching {
        @Suppress("DEPRECATION")
        packageManager.getPackageInfo(packageName, 0)
    }.isSuccess
}
