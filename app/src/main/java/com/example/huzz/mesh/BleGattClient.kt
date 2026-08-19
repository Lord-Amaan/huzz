package com.example.huzz.mesh

import android.annotation.SuppressLint
import android.bluetooth.*
import android.bluetooth.le.*
import android.content.Context
import android.os.ParcelUuid
import android.util.Log
import kotlinx.coroutines.*

/**
 * Manages the BLE GATT Client side of the mesh:
 * - Scans for nearby devices advertising our Service UUID
 * - Connects to discovered peers via GATT
 * - Writes packets to peer's characteristic
 *
 * Uses duty-cycled scanning to avoid Android OS throttling.
 * Adapted from BitChat's BluetoothGattClientManager.
 */
@SuppressLint("MissingPermission")
class BleGattClient(
    private val context: Context,
    private val scope: CoroutineScope,
    private val myPeerID: String,
    private val onPeerDiscovered: (deviceAddress: String, peerID: String, rssi: Int) -> Unit,
    private val onPacketReceived: (HuzzPacket, BluetoothDevice) -> Unit,
    private val onDeviceConnected: (BluetoothDevice, String) -> Unit,
    private val onDeviceDisconnected: (BluetoothDevice) -> Unit
) {
    companion object {
        private const val TAG = "BleGattClient"
    }

    private val bluetoothManager =
        context.getSystemService(Context.BLUETOOTH_SERVICE) as BluetoothManager
    private val bluetoothAdapter: BluetoothAdapter? = bluetoothManager.adapter
    private val bleScanner: BluetoothLeScanner? = bluetoothAdapter?.bluetoothLeScanner

    private var isActive = false
    private var scanDutyCycleJob: Job? = null

    // Track active GATT connections (device address -> GATT + characteristic)
    data class GattConnection(
        val gatt: BluetoothGatt,
        val characteristic: BluetoothGattCharacteristic? = null,
        val peerID: String = ""
    )

    private val connections = mutableMapOf<String, GattConnection>()
    private val pendingConnections = mutableSetOf<String>() // Addresses currently connecting
    private val recentlyConnected = mutableMapOf<String, Long>() // Prevent rapid reconnects

    private var scanCallback: ScanCallback? = null

    /**
     * Start scanning for peers with duty cycling.
     */
    fun start() {
        if (isActive) return
        isActive = true
        startDutyCycledScanning()
    }

    /**
     * Stop scanning and disconnect all.
     */
    fun stop() {
        isActive = false
        scanDutyCycleJob?.cancel()
        stopScanning()

        // Close all GATT connections
        connections.values.forEach { conn ->
            try {
                conn.gatt.disconnect()
                conn.gatt.close()
            } catch (e: Exception) {
                Log.w(TAG, "Error closing GATT: ${e.message}")
            }
        }
        connections.clear()
        pendingConnections.clear()
    }

    /**
     * Write a packet to a specific connected peer.
     */
    fun writeToDevice(deviceAddress: String, data: ByteArray): Boolean {
        val conn = connections[deviceAddress] ?: return false
        val char = conn.characteristic ?: return false

        return try {
            char.value = data
            char.writeType = BluetoothGattCharacteristic.WRITE_TYPE_NO_RESPONSE
            conn.gatt.writeCharacteristic(char)
        } catch (e: Exception) {
            Log.w(TAG, "Failed to write to $deviceAddress: ${e.message}")
            false
        }
    }

    /**
     * Write a packet to ALL connected client-side peers.
     */
    fun broadcastToAllConnected(data: ByteArray) {
        connections.keys.toList().forEach { address ->
            writeToDevice(address, data)
        }
    }

    fun getConnectedDeviceAddresses(): Set<String> = connections.keys.toSet()

    fun getPeerIDForAddress(address: String): String? = connections[address]?.peerID

    // --- Private: Scanning ---

    private fun startDutyCycledScanning() {
        scanDutyCycleJob?.cancel()
        scanDutyCycleJob = scope.launch {
            while (isActive) {
                startScanning()
                delay(MeshConstants.SCAN_ON_DURATION_MS)
                if (!isActive) break
                stopScanning()
                delay(MeshConstants.SCAN_OFF_DURATION_MS)
            }
        }
    }

    private fun startScanning() {
        if (bleScanner == null) {
            Log.w(TAG, "BLE scanner not available")
            return
        }

        // Use ScanFilter to only discover devices advertising our service UUID
        val filter = ScanFilter.Builder()
            .setServiceUuid(ParcelUuid(MeshConstants.SERVICE_UUID))
            .build()

        val settings = ScanSettings.Builder()
            .setScanMode(ScanSettings.SCAN_MODE_LOW_LATENCY)
            .build()

        scanCallback = object : ScanCallback() {
            override fun onScanResult(callbackType: Int, result: ScanResult) {
                handleScanResult(result)
            }

            override fun onBatchScanResults(results: MutableList<ScanResult>) {
                results.forEach { handleScanResult(it) }
            }

            override fun onScanFailed(errorCode: Int) {
                Log.e(TAG, "Scan failed with error code: $errorCode")
            }
        }

        try {
            bleScanner.startScan(listOf(filter), settings, scanCallback)
        } catch (e: Exception) {
            Log.e(TAG, "Failed to start scan: ${e.message}")
        }
    }

    private fun stopScanning() {
        try {
            scanCallback?.let { bleScanner?.stopScan(it) }
        } catch (e: Exception) {
            Log.w(TAG, "Error stopping scan: ${e.message}")
        }
    }

    private fun handleScanResult(result: ScanResult) {
        val device = result.device
        val address = device.address

        // Extract peerID from scan response service data
        val serviceData = result.scanRecord?.getServiceData(ParcelUuid(MeshConstants.SERVICE_UUID))
        val peerID = serviceData?.toHexString() ?: ""

        // Skip if it's our own advertisement
        if (peerID == myPeerID) return

        // Notify about discovery
        onPeerDiscovered(address, peerID, result.rssi)

        // Auto-connect if not already connected or pending
        if (!connections.containsKey(address) && !pendingConnections.contains(address)) {
            // Check cooldown
            val lastConnect = recentlyConnected[address] ?: 0
            if (System.currentTimeMillis() - lastConnect < MeshConstants.CONNECTION_RETRY_DELAY_MS) return

            // Check connection limit
            if (connections.size >= MeshConstants.MAX_CONNECTIONS) return

            connectToDevice(device, peerID, result.rssi)
        }
    }

    // --- Private: Connection ---

    private fun connectToDevice(device: BluetoothDevice, peerID: String, rssi: Int) {
        val address = device.address
        pendingConnections.add(address)
        recentlyConnected[address] = System.currentTimeMillis()

        Log.d(TAG, "Connecting to $address (peerID: $peerID)")

        val gattCallback = object : BluetoothGattCallback() {
            override fun onConnectionStateChange(gatt: BluetoothGatt, status: Int, newState: Int) {
                when (newState) {
                    BluetoothGatt.STATE_CONNECTED -> {
                        Log.i(TAG, "Connected to $address, requesting MTU")
                        gatt.requestMtu(MeshConstants.MTU_SIZE)
                    }
                    BluetoothGatt.STATE_DISCONNECTED -> {
                        Log.i(TAG, "Disconnected from $address")
                        pendingConnections.remove(address)
                        connections.remove(address)
                        scope.launch {
                            delay(500)
                            try { gatt.close() } catch (_: Exception) { }
                        }
                        onDeviceDisconnected(device)
                    }
                }
            }

            override fun onMtuChanged(gatt: BluetoothGatt, mtu: Int, status: Int) {
                if (status == BluetoothGatt.GATT_SUCCESS) {
                    Log.d(TAG, "MTU set to $mtu for $address, discovering services")
                    gatt.discoverServices()
                } else {
                    Log.w(TAG, "MTU negotiation failed for $address")
                    gatt.disconnect()
                }
            }

            override fun onServicesDiscovered(gatt: BluetoothGatt, status: Int) {
                if (status != BluetoothGatt.GATT_SUCCESS) {
                    Log.e(TAG, "Service discovery failed for $address")
                    gatt.disconnect()
                    return
                }

                val service = gatt.getService(MeshConstants.SERVICE_UUID)
                if (service == null) {
                    Log.e(TAG, "Service not found on $address")
                    gatt.disconnect()
                    return
                }

                val char = service.getCharacteristic(MeshConstants.CHARACTERISTIC_UUID)
                if (char == null) {
                    Log.e(TAG, "Characteristic not found on $address")
                    gatt.disconnect()
                    return
                }

                // Enable notifications
                gatt.setCharacteristicNotification(char, true)
                val descriptor = char.getDescriptor(MeshConstants.DESCRIPTOR_UUID)
                if (descriptor != null) {
                    descriptor.value = BluetoothGattDescriptor.ENABLE_NOTIFICATION_VALUE
                    gatt.writeDescriptor(descriptor)
                }

                // Store the connection
                pendingConnections.remove(address)
                connections[address] = GattConnection(gatt, char, peerID)

                Log.i(TAG, "Fully connected to $address (client)")
                scope.launch {
                    delay(200)
                    if (isActive) onDeviceConnected(device, peerID)
                }
            }

            @Suppress("DEPRECATION")
            override fun onCharacteristicChanged(
                gatt: BluetoothGatt,
                characteristic: BluetoothGattCharacteristic
            ) {
                // Received a notification from the server (a packet)
                val value = characteristic.value ?: return
                val packet = HuzzPacket.fromBinaryData(value)
                if (packet != null) {
                    onPacketReceived(packet, device)
                }
            }
        }

        try {
            device.connectGatt(context, false, gattCallback, BluetoothDevice.TRANSPORT_LE)
        } catch (e: Exception) {
            Log.e(TAG, "Failed to connect to $address: ${e.message}")
            pendingConnections.remove(address)
        }
    }
}
