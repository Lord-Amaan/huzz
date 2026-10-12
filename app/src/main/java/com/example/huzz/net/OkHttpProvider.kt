package com.example.huzz.net

import okhttp3.OkHttpClient
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicReference

/**
 * Centralized OkHttp provider for network and WebSocket traffic.
 */
object OkHttpProvider {
    enum class Route {
        DIRECT,
        TOR
    }

    data class RoutedClient(
        val client: OkHttpClient,
        val route: Route
    )

    private val httpClientRef = AtomicReference<RoutedClient?>(null)
    private val wsClientRef = AtomicReference<OkHttpClient?>(null)
    private val clientLock = Any()

    fun reset() {
        synchronized(clientLock) {
            httpClientRef.set(null)
            wsClientRef.set(null)
        }
    }

    fun httpClient(): OkHttpClient = routedHttpClient().client

    fun routedHttpClient(): RoutedClient {
        httpClientRef.get()?.let { return it }
        return synchronized(clientLock) {
            httpClientRef.get() ?: run {
                val client = OkHttpClient.Builder()
                    .callTimeout(15, TimeUnit.SECONDS)
                    .connectTimeout(10, TimeUnit.SECONDS)
                    .readTimeout(15, TimeUnit.SECONDS)
                    .build()
                RoutedClient(client, Route.DIRECT).also(httpClientRef::set)
            }
        }
    }

    fun webSocketClient(): OkHttpClient {
        wsClientRef.get()?.let { return it }
        return synchronized(clientLock) {
            wsClientRef.get() ?: OkHttpClient.Builder()
                .connectTimeout(10, TimeUnit.SECONDS)
                .readTimeout(0, TimeUnit.SECONDS)
                .writeTimeout(10, TimeUnit.SECONDS)
                .build()
                .also(wsClientRef::set)
        }
    }
}
