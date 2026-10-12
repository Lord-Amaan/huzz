package com.example.huzz

import android.app.Application
import com.example.huzz.nostr.RelayDirectory
import com.example.huzz.ui.theme.ThemePreferenceManager
import com.example.huzz.net.ArtiTorManager

/**
 * Main application class for huzz Android
 */
class HuzzApplication : Application() {

    companion object {
        const val TAG = "HuzzApp"
        lateinit var instance: HuzzApplication
            private set
    }

    override fun onCreate() {
        super.onCreate()
        instance = this

        // Start the single process-wide power policy before transport components are constructed.
        try { com.example.huzz.mesh.PowerManager.getInstance(this).start() } catch (t: Throwable) { android.util.Log.e(TAG, "Failed to start PowerManager", t) }

        // Initialize Tor first so any early network goes over Tor
        try {
            val torProvider = ArtiTorManager.getInstance()
            torProvider.init(this)
        } catch (t: Throwable) {
            android.util.Log.e(TAG, "Failed to init Tor", t)
        }

        // Initialize relay directory (loads assets/nostr_relays.csv)
        try { RelayDirectory.initialize(this) } catch (t: Throwable) { android.util.Log.e(TAG, "Failed to init RelayDirectory", t) }

        // Initialize LocationNotesManager dependencies early so sheet subscriptions can start immediately
        try { com.example.huzz.nostr.LocationNotesInitializer.initialize(this) } catch (t: Throwable) { }

        // Initialize favorites persistence early so MessageRouter/NostrTransport can use it on startup
        try {
            com.example.huzz.favorites.FavoritesPersistenceService.initialize(this)
        } catch (t: Throwable) { }

        // Restore private conversations before background transports can deliver new messages.
        // AppStateStore merges any in-flight arrivals by message ID, so startup cannot replace
        // newer transport state with an older database snapshot.
        try {
            com.example.huzz.services.AppStateStore.initializeConversationPersistence(this)
        } catch (t: Throwable) { }

        // Warm up Nostr identity to ensure npub is available for favorite notifications
        try {
            com.example.huzz.nostr.NostrIdentityBridge.getCurrentNostrIdentity(this)
        } catch (t: Throwable) { }

        // Initialize theme preference
        try { ThemePreferenceManager.init(this) } catch (t: Throwable) { }

        // Initialize chat UI mode (matrix transcript vs bubbles)
        try { com.example.huzz.ui.theme.ChatUiModeManager.init(this) } catch (t: Throwable) { }

        // Initialize debug preference manager (persists debug toggles)
        try { com.example.huzz.ui.debug.DebugPreferenceManager.init(this) } catch (t: Throwable) { }

        // Initialize Wi‑Fi Aware controller with persisted default
        try {
            val enabled = com.example.huzz.ui.debug.DebugPreferenceManager.getWifiAwareEnabled(false)
            com.example.huzz.wifiaware.WifiAwareController.initialize(this, enabled)
        } catch (t: Throwable) { }

        // Initialize Geohash Registries for persistence
        try {
            com.example.huzz.nostr.GeohashAliasRegistry.initialize(this)
            com.example.huzz.nostr.GeohashConversationRegistry.initialize(this)
        } catch (t: Throwable) { }

        // Own relay connectivity, selected-channel subscriptions, and presence scheduling at the
        // process level so closing the Activity does not disconnect Nostr.
        try { com.example.huzz.nostr.NostrBackgroundRuntime.initialize(this) } catch (t: Throwable) { }

        // Initialize mesh service preferences
        try { com.example.huzz.service.MeshServicePreferences.init(this) } catch (t: Throwable) { }

        // Proactively start the foreground service to keep mesh alive
        try { com.example.huzz.service.MeshForegroundService.start(this) } catch (t: Throwable) { }

        // TorManager already initialized above
    }
}
