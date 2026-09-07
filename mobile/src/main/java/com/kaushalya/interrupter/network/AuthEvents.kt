package com.kaushalya.interrupter.network

import kotlinx.coroutines.flow.MutableStateFlow

/**
 * Cross-cutting signal for auth expiry detected on any background thread
 * (OkHttp interceptor, sync, etc.). The UI observes [sessionExpired] and
 * routes back to the login flow when the counter changes.
 *
 * A monotonic counter (not a boolean) so that two expirations in a row both
 * notify collectors even if the UI was not composed between them.
 */
object AuthEvents {

    val sessionExpired = MutableStateFlow(0L)

    fun notifySessionExpired() {
        sessionExpired.value += 1
    }
}