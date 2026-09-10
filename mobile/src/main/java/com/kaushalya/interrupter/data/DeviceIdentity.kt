package com.kaushalya.interrupter.data

import android.content.Context
import java.util.UUID

/**
 * Stable per-install identifier. Used to tie guest sessions (and quiz bundle
 * issuance) to one backend account on a device.
 */
object DeviceIdentity {
    private const val PREFS = "device"
    private const val KEY_DEVICE_ID = "device_id"

    fun deviceId(context: Context): String {
        val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        return prefs.getString(KEY_DEVICE_ID, null) ?: UUID.randomUUID().toString().also {
            prefs.edit().putString(KEY_DEVICE_ID, it).apply()
        }
    }
}