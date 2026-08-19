package com.example.huzz.mesh

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.IBinder
import android.util.Log
import androidx.core.app.NotificationCompat
import com.example.huzz.R

/**
 * Foreground service that keeps the BLE mesh running in the background.
 * Android requires a foreground service for continuous BLE operations.
 *
 * Adapted from BitChat's MeshForegroundService.
 */
class MeshForegroundService : Service() {

    companion object {
        private const val TAG = "MeshForegroundService"

        const val ACTION_START = "com.example.huzz.mesh.START"
        const val ACTION_STOP = "com.example.huzz.mesh.STOP"
        const val EXTRA_NICKNAME = "nickname"

        // StateFlow reference to the mesh manager for the UI to access
        private val _meshManagerFlow = kotlinx.coroutines.flow.MutableStateFlow<BleMeshManager?>(null)
        val meshManagerFlow: kotlinx.coroutines.flow.StateFlow<BleMeshManager?> = _meshManagerFlow

        var meshManager: BleMeshManager?
            get() = _meshManagerFlow.value
            private set(value) {
                _meshManagerFlow.value = value
            }
    }

    override fun onCreate() {
        super.onCreate()
        createNotificationChannel()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_START -> {
                val nickname = intent.getStringExtra(EXTRA_NICKNAME) ?: "User"
                startMesh(nickname)
            }
            ACTION_STOP -> {
                stopMesh()
            }
        }
        return START_STICKY
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onDestroy() {
        stopMesh()
        super.onDestroy()
    }

    private fun startMesh(nickname: String) {
        Log.i(TAG, "Starting mesh foreground service with nickname: $nickname")

        val notification = buildNotification("Huzz Mesh is running...")

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            startForeground(
                MeshConstants.NOTIFICATION_ID,
                notification,
                ServiceInfo.FOREGROUND_SERVICE_TYPE_CONNECTED_DEVICE
            )
        } else {
            startForeground(MeshConstants.NOTIFICATION_ID, notification)
        }

        // Create and start the mesh manager
        if (meshManager == null) {
            meshManager = BleMeshManager(applicationContext)
        }
        meshManager?.start(nickname)

        updateNotification("Connected to mesh as $nickname")
    }

    private fun stopMesh() {
        Log.i(TAG, "Stopping mesh foreground service")
        meshManager?.stop()
        meshManager = null
        stopForeground(STOP_FOREGROUND_REMOVE)
        stopSelf()
    }

    private fun createNotificationChannel() {
        val channel = NotificationChannel(
            MeshConstants.NOTIFICATION_CHANNEL_ID,
            "Huzz Mesh Network",
            NotificationManager.IMPORTANCE_LOW
        ).apply {
            description = "Keeps the BLE mesh network running"
            setShowBadge(false)
        }
        val manager = getSystemService(NotificationManager::class.java)
        manager.createNotificationChannel(channel)
    }

    private fun buildNotification(text: String): Notification {
        return NotificationCompat.Builder(this, MeshConstants.NOTIFICATION_CHANNEL_ID)
            .setContentTitle("Huzz Mesh")
            .setContentText(text)
            .setSmallIcon(R.mipmap.ic_launcher)
            .setOngoing(true)
            .setSilent(true)
            .build()
    }

    private fun updateNotification(text: String) {
        val notification = buildNotification(text)
        val manager = getSystemService(NotificationManager::class.java)
        manager.notify(MeshConstants.NOTIFICATION_ID, notification)
    }
}
