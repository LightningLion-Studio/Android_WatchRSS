package com.lightningstudio.watchrss.testutil

import android.Manifest
import android.os.Build
import androidx.test.rule.GrantPermissionRule

object ScreenshotTestPermissions {

    /**
     * 返回截图测试需要的权限 grant rule。
     *
     * 在 Android 12+ 上，App 启动后若触发蓝牙/附近设备相关功能会检查
     * [Manifest.permission.BLUETOOTH_CONNECT]；Android 10+ 的附近 WiFi 设备
     * 也需要 [Manifest.permission.NEARBY_WIFI_DEVICES]；录音与定位是旧连接
     * 流程可能申请的权限。预先授权可避免权限弹窗阻塞测试与截图。
     */
    fun grantRule(): GrantPermissionRule {
        val permissions = buildList {
            add(Manifest.permission.RECORD_AUDIO)
            add(Manifest.permission.ACCESS_FINE_LOCATION)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                add(Manifest.permission.BLUETOOTH_CONNECT)
            }
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                add(Manifest.permission.NEARBY_WIFI_DEVICES)
            }
        }
        return GrantPermissionRule.grant(*permissions.toTypedArray())
    }
}
