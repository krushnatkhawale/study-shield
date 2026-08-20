package com.kaushalya.interrupter.data

import android.content.Context
import android.widget.Toast

object ToastHelper {
    private var appContext: Context? = null

    fun init(context: Context) {
        appContext = context.applicationContext
    }

    fun show(message: String) {
        val ctx = appContext ?: return
        android.os.Handler(android.os.Looper.getMainLooper()).post {
            Toast.makeText(ctx, message, Toast.LENGTH_SHORT).show()
        }
    }
}
