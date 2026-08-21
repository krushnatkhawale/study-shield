package com.kaushalya.interrupter.network

import com.kaushalya.interrupter.data.SessionManager
import kotlinx.serialization.json.Json
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.logging.HttpLoggingInterceptor
import retrofit2.Retrofit
import retrofit2.converter.kotlinx.serialization.asConverterFactory
import java.util.concurrent.TimeUnit

object RetrofitClient {

    private const val BASE_URL = "https://study-shield-backend-komv.onrender.com"

    private val json = Json {
        ignoreUnknownKeys = true
        isLenient = true
    }

    private var apiService: ApiService? = null
    internal var sessionManager: SessionManager? = null
        private set

    fun init(sm: SessionManager) {
        sessionManager = sm
    }

    fun getApiService(): ApiService {
        if (apiService == null) {
            val sm = sessionManager
                ?: throw IllegalStateException("RetrofitClient not initialized. Call init() first.")

            val logging = HttpLoggingInterceptor().apply {
                level = HttpLoggingInterceptor.Level.BODY
            }

            // TEMP: log every REST response's status code and body for debugging
            val responseLogger = okhttp3.Interceptor { chain ->
                val response = chain.proceed(chain.request())
                val body = response.peekBody(Long.MAX_VALUE).string()
                android.util.Log.i(
                    "RestCall",
                    "${response.request.method} ${response.request.url} -> HTTP ${response.code}\n$body"
                )
                response
            }

            val client = OkHttpClient.Builder()
                .addInterceptor(AuthInterceptor(sm))
                .addInterceptor(logging)
                .addInterceptor(responseLogger)
                .connectTimeout(10, TimeUnit.SECONDS)
                .readTimeout(30, TimeUnit.SECONDS)
                .writeTimeout(30, TimeUnit.SECONDS)
                .build()

            val contentType = "application/json".toMediaType()

            apiService = Retrofit.Builder()
                .baseUrl(BASE_URL)
                .client(client)
                .addConverterFactory(json.asConverterFactory(contentType))
                .build()
                .create(ApiService::class.java)
        }
        return apiService!!
    }

    fun reset() {
        apiService = null
    }
}
