package com.example.huzz.mesh

import android.annotation.SuppressLint
import android.bluetooth.*
import android.bluetooth.le.*
import android.content.Context
import android.os.ParcelUuid
import android.util.Log
import kotlinx.coroutines.*

/**
 * Manages the BLE GATT Server side of the mesh:
 * - Advertises our presence with a custom Service UUID
 * - Accepts incoming GATT connections from scanning peers
 * - Receives packets written to our characteristic by connected clients
 *
 * Adapted from BitChat's BluetoothGattServerManager.
 */
@SuppressLint("MissingPermission")
class BleGattServer(
    private val context: Context,
    private val scope: CoroutineScope,
    private val myPeerID: String,
    private val onPacketReceived: (HuzzPacket, BluetoothDevice) -> Unit,
    private val onDeviceConnected: (BluetoothDevice) -> Unit,
    private val onDeviceDisconnected: (BluetoothDevice) -> Unit
) {
    companion object {
        private const val TAG = "BleGattServer"
    }

    private val bluetoothManager =
        context.getSystemService(Context.BLUETOOTH_SERVICE) as BluetoothManager
    private val bluetoothAdapter: BluetoothAdapter? = bluetoothManager.adapter
    private val bleAdvertiser: BluetoothLeAdvertiser? = bluetoothAdapter?.bluetoothLeAdvertiser

    private var gattServer: BluetoothGattServer? = null
    private var characteristic: BluetoothGattCharacteristic? = null
    private var advertiseCallback: AdvertiseCallback? = null
    private var isActive = false

    // Track connected devices on the server side
    private val connectedDevices = mutableSetOf<String>()

    /**
     * Start the GATT server and BLE advertising.
     */
    fun start() {
        if (isActive) return
        isActive = true

        setupGattServer()
        startAdvertising()
    }

    /**
     * Stop everything.
     */
    fun stop() {
        isActive = false
        stopAdvertising()
        try {
            gattServer?.close()
        } catch (e: Exception) {
            Log.w(TAG, "Error closing GATT server: ${e.message}")
        }
        gattServer = null
        connectedDevices.clear()
    }

    /**
     * Send a packet to all connected server-side devices via GATT notification.
     */
    fun broadcastToConnectedDevices(data: ByteArray) {
        val server = gattServer ?: return
        val char = characteristic ?: return

        connectedDevices.toList().forEach { address ->
            try {
                val device = bluetoothAdapter?.getRemoteDevice(address) ?: return@forEach
                char.value = data
                server.notifyCharacteristicChanged(device, char, false)
            } catch (e: Exception) {
                Log.w(TAG, "Failed to notify $address: ${e.message}")
            }
        }
    }

    fun getConnectedDeviceAddresses(): Set<String> = connectedDevices.toSet()

    // --- Private Setup ---

    private fun setupGattServer() {
        val serverCallback = object : BluetoothGattServerCallback() {
            override fun onConnectionStateChange(device: BluetoothDevice, status: Int, newState: Int) {
                when (newState) {
                    BluetoothGatt.STATE_CONNECTED -> {
                        connectedDevices.add(device.address)
                        Log.i(TAG, "Device connected (server): ${device.address}")
                        scope.launch {
                            delay(100) // Small delay for stability
                            if (isActive) onDeviceConnected(device)
                        }
                    }
                    BluetoothGatt.STATE_DISCONNECTED -> {
                        connectedDevices.remove(device.address)
                        Log.i(TAG, "Device disconnected (server): ${device.address}")
                        onDeviceDisconnected(device)
                    }
                }
            }

            override fun onCharacteristicWriteRequest(
                device: BluetoothDevice,
                requestId: Int,
                characteristic: BluetoothGattCharacteristic,
                preparedWrite: Boolean,
                responseNeeded: Boolean,
                offset: Int,
                value: ByteArray?
            ) {
                if (characteristic.uuid == MeshConstants.CHARACTERISTIC_UUID && value != null) {
                    val packet = HuzzPacket.fromBinaryData(value)
                    if (packet != null) {
                        onPacketReceived(packet, device)
                    } else {
                        Log.d(TAG, "Failed to parse packet from ${device.address}, size: ${value.size}")
                    }
                }

                if (responseNeeded) {
                    gattServer?.sendResponse(device, requestId, BluetoothGatt.GATT_SUCCESS, 0, null)
                }
            }

            override fun onDescriptorWriteRequest(
                device: BluetoothDevice,
                requestId: Int,
                descriptor: BluetoothGattDescriptor,
                preparedWrite: Boolean,
                responseNeeded: Boolean,
                offset: Int,
                value: ByteArray?
            ) {
                if (responseNeeded) {
                    gattServer?.sendResponse(device, requestId, BluetoothGatt.GATT_SUCCESS, 0, null)
                }
            }

            override fun onNotificationSent(device: BluetoothDevice, status: Int) {
                if (status != BluetoothGatt.GATT_SUCCESS) {
                    Log.w(TAG, "Notification failed for ${device.address}, status: $status")
                }
            }
        }

        // Close any existing server
        gattServer?.let {
            try { it.close() } catch (_: Exception) { }
        }

        gattServer = bluetoothManager.openGattServer(context, serverCallback)

        // Create the characteristic with read/write/notify
        characteristic = BluetoothGattCharacteristic(
            MeshConstants.CHARACTERISTIC_UUID,
            BluetoothGattCharacteristic.PROPERTY_READ or
                    BluetoothGattCharacteristic.PROPERTY_WRITE or
                    BluetoothGattCharacteristic.PROPERTY_WRITE_NO_RESPONSE or
                    BluetoothGattCharacteristic.PROPERTY_NOTIFY,
            BluetoothGattCharacteristic.PERMISSION_READ or
                    BluetoothGattCharacteristic.PERMISSION_WRITE
        )

        // Add CCCD descriptor for notifications
        val descriptor = BluetoothGattDescriptor(
            MeshConstants.DESCRIPTOR_UUID,
            BluetoothGattDescriptor.PERMISSION_READ or BluetoothGattDescriptor.PERMISSION_WRITE
        )
        characteristic!!.addDescriptor(descriptor)

        // Create and add the service
        val service = BluetoothGattService(
            MeshConstants.SERVICE_UUID,
            BluetoothGattService.SERVICE_TYPE_PRIMARY
        )
        service.addCharacteristic(characteristic)
        gattServer?.addService(service)

        Log.i(TAG, "GATT server setup complete")
    }

    private fun startAdvertising() {
        if (bleAdvertiser == null) {
            Log.w(TAG, "BLE advertiser not available")
            return
        }

        val settings = AdvertiseSettings.Builder()
            .setAdvertiseMode(AdvertiseSettings.ADVERTISE_MODE_LOW_LATENCY)
            .setConnectable(true)
            .setTimeout(0) // Advertise indefinitely
            .setTxPowerLevel(AdvertiseSettings.ADVERTISE_TX_POWER_MEDIUM)
            .build()

        // Main advertising data with our Service UUID
        val data = AdvertiseData.Builder()
            .addServiceUuid(ParcelUuid(MeshConstants.SERVICE_UUID))
            .setIncludeTxPowerLevel(false)
            .setIncludeDeviceName(false)
            .build()

        // Scan response includes our peer ID (first 8 bytes) for identification
        val peerIDBytes = try {
            myPeerID.chunked(2).map { it.toInt(16).toByte() }.toByteArray().take(8).toByteArray()
        } catch (_: Exception) {
            ByteArray(0)
        }

        val scanResponse = AdvertiseData.Builder()
            .addServiceData(ParcelUuid(MeshConstants.SERVICE_UUID), peerIDBytes)
            .setIncludeTxPowerLevel(false)
            .setIncludeDeviceName(false)
            .build()

        advertiseCallback = object : AdvertiseCallback() {
            override fun onStartSuccess(settingsInEffect: AdvertiseSettings) {
                Log.i(TAG, "Advertising started successfully")
            }

            override fun onStartFailure(errorCode: Int) {
                Log.e(TAG, "Advertising failed with error code: $errorCode")
                // Retry on transient errors
                if (errorCode == ADVERTISE_FAILED_INTERNAL_ERROR ||
                    errorCode == ADVERTISE_FAILED_TOO_MANY_ADVERTISERS
                ) {
                    scope.launch {
                        delay(5000)
                        if (isActive) startAdvertising()
                    }
                }
            }
        }

        try {
            bleAdvertiser.startAdvertising(settings, data, scanResponse, advertiseCallback)
        } catch (e: SecurityException) {
            Log.e(TAG, "SecurityException starting advertising: ${e.message}")
        } catch (e: Exception) {
            Log.e(TAG, "Exception starting advertising: ${e.message}")
        }
    }

    private fun stopAdvertising() {
        try {
            advertiseCallback?.let { bleAdvertiser?.stopAdvertising(it) }
        } catch (e: Exception) {
            Log.w(TAG, "Error stopping advertising: ${e.message}")
        }
    }
}
