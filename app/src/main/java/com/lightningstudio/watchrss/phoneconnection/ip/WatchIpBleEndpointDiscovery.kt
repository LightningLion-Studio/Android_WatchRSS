package com.lightningstudio.watchrss.phoneconnection.ip

import android.Manifest
import android.annotation.SuppressLint
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothGatt
import android.bluetooth.BluetoothGattCallback
import android.bluetooth.BluetoothGattCharacteristic
import android.bluetooth.BluetoothGattService
import android.bluetooth.BluetoothManager
import android.bluetooth.BluetoothProfile
import android.bluetooth.le.ScanCallback
import android.bluetooth.le.ScanFilter
import android.bluetooth.le.ScanResult
import android.bluetooth.le.ScanSettings
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import android.os.ParcelUuid
import androidx.core.content.ContextCompat
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import org.json.JSONObject

internal class WatchIpBleEndpointDiscovery(context: Context) {
    private val appContext = context.applicationContext
    private val adapter = appContext.getSystemService(BluetoothManager::class.java)?.adapter

    @SuppressLint("MissingPermission")
    suspend fun discover(): IpEndpointDescriptor = withContext(Dispatchers.IO) {
        requirePermissions()
        val bluetoothAdapter = requireNotNull(adapter) { "手表没有蓝牙适配器" }
        require(bluetoothAdapter.isEnabled) { "手表蓝牙未开启" }
        val scanner = requireNotNull(bluetoothAdapter.bluetoothLeScanner) { "BLE 扫描不可用" }
        val deviceDeferred = CompletableDeferred<BluetoothDevice>()
        val scanCallback = object : ScanCallback() {
            override fun onScanResult(callbackType: Int, result: ScanResult) {
                val device = result.device ?: return
                if (device.bondState == BluetoothDevice.BOND_BONDED) {
                    deviceDeferred.complete(device)
                }
            }

            override fun onScanFailed(errorCode: Int) {
                deviceDeferred.completeExceptionally(
                    IllegalStateException("BLE 端点扫描失败：$errorCode")
                )
            }
        }
        val filter = ScanFilter.Builder()
            .setServiceUuid(ParcelUuid(IpSyncProtocol.ADVERTISED_BLE_SERVICE_UUID))
            .build()
        scanner.startScan(
            listOf(filter),
            ScanSettings.Builder().setScanMode(ScanSettings.SCAN_MODE_LOW_LATENCY).build(),
            scanCallback
        )
        val device = try {
            withTimeout(SCAN_TIMEOUT_MS) { deviceDeferred.await() }
        } finally {
            runCatching { scanner.stopScan(scanCallback) }
        }
        readDescriptor(device)
    }

    @SuppressLint("MissingPermission")
    private suspend fun readDescriptor(device: BluetoothDevice): IpEndpointDescriptor {
        val result = CompletableDeferred<IpEndpointDescriptor>()
        var gatt: BluetoothGatt? = null
        val callback = object : BluetoothGattCallback() {
            override fun onConnectionStateChange(gatt: BluetoothGatt, status: Int, newState: Int) {
                if (status == BluetoothGatt.GATT_SUCCESS && newState == BluetoothProfile.STATE_CONNECTED) {
                    if (!gatt.discoverServices()) {
                        result.completeExceptionally(IllegalStateException("无法发现手机 GATT 服务"))
                    }
                } else if (newState == BluetoothProfile.STATE_DISCONNECTED && !result.isCompleted) {
                    result.completeExceptionally(
                        IllegalStateException("手机 GATT 已断开：status=$status")
                    )
                }
            }

            override fun onServicesDiscovered(gatt: BluetoothGatt, status: Int) {
                if (status != BluetoothGatt.GATT_SUCCESS) {
                    result.completeExceptionally(IllegalStateException("手机 GATT 服务发现失败：$status"))
                    return
                }
                val characteristic = gatt
                    .getService(IpSyncProtocol.BLE_DISCOVERY_SERVICE_UUID)
                    ?.getCharacteristic(IpSyncProtocol.BLE_ENDPOINT_CHARACTERISTIC_UUID)
                if (characteristic == null) {
                    result.completeExceptionally(IllegalStateException("手机缺少 IP 端点描述服务"))
                    return
                }
                @Suppress("DEPRECATION")
                val accepted = gatt.readCharacteristic(characteristic)
                if (!accepted) {
                    result.completeExceptionally(IllegalStateException("手机 IP 端点读取请求被拒绝"))
                }
            }

            @Deprecated("Deprecated in Android 13")
            override fun onCharacteristicRead(
                gatt: BluetoothGatt,
                characteristic: BluetoothGattCharacteristic,
                status: Int
            ) {
                @Suppress("DEPRECATION")
                completeRead(characteristic, characteristic.value, status)
            }

            override fun onCharacteristicRead(
                gatt: BluetoothGatt,
                characteristic: BluetoothGattCharacteristic,
                value: ByteArray,
                status: Int
            ) {
                completeRead(characteristic, value, status)
            }

            private fun completeRead(
                characteristic: BluetoothGattCharacteristic,
                value: ByteArray?,
                status: Int
            ) {
                if (
                    characteristic.uuid != IpSyncProtocol.BLE_ENDPOINT_CHARACTERISTIC_UUID ||
                    result.isCompleted
                ) return
                val bytes = value
                if (status != BluetoothGatt.GATT_SUCCESS || bytes == null || bytes.isEmpty()) {
                    result.completeExceptionally(
                        IllegalStateException("手机 IP 端点读取失败：$status")
                    )
                    return
                }
                runCatching {
                    IpEndpointDescriptor.fromJson(JSONObject(bytes.toString(Charsets.UTF_8))).also {
                        require(it.verify()) { "手机 IP 端点描述校验失败或已过期" }
                    }
                }.onSuccess(result::complete)
                    .onFailure(result::completeExceptionally)
            }
        }
        try {
            gatt = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                device.connectGatt(appContext, false, callback, BluetoothDevice.TRANSPORT_LE)
            } else {
                device.connectGatt(appContext, false, callback)
            }
            return withTimeout(GATT_TIMEOUT_MS) { result.await() }
        } finally {
            runCatching { gatt?.disconnect() }
            runCatching { gatt?.close() }
        }
    }

    private fun requirePermissions() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.S) return
        require(
            ContextCompat.checkSelfPermission(appContext, Manifest.permission.BLUETOOTH_SCAN) ==
                PackageManager.PERMISSION_GRANTED &&
                ContextCompat.checkSelfPermission(appContext, Manifest.permission.BLUETOOTH_CONNECT) ==
                PackageManager.PERMISSION_GRANTED
        ) { "缺少附近设备权限，暂时保留蓝牙同步" }
    }

    companion object {
        private const val SCAN_TIMEOUT_MS = 8_000L
        private const val GATT_TIMEOUT_MS = 12_000L
    }
}
