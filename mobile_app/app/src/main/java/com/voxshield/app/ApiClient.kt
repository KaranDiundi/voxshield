package com.voxshield.app

import okhttp3.MultipartBody
import okhttp3.OkHttpClient
import okhttp3.logging.HttpLoggingInterceptor
import retrofit2.Call
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import retrofit2.http.Multipart
import retrofit2.http.POST
import retrofit2.http.Part
import java.util.concurrent.TimeUnit

/**
 * VoxShield prediction response from the backend.
 */
data class PredictionResponse(
    val fake_probability: Float,
    val real_probability: Float,
    val risk_level: String
)

/**
 * Retrofit API interface for communication with the VoxShield backend.
 */
interface VoxShieldApi {
    @Multipart
    @POST("/predict")
    fun predict(@Part file: MultipartBody.Part): Call<PredictionResponse>
}

/**
 * Singleton API client factory.
 */
object ApiClient {

    private var retrofit: Retrofit? = null
    private var currentBaseUrl: String = ""

    /**
     * Get or create a Retrofit-backed VoxShieldApi instance.
     * @param baseUrl  The server root URL, e.g. "http://10.0.2.2:8000"
     */
    fun getApi(baseUrl: String): VoxShieldApi {
        val url = if (baseUrl.endsWith("/")) baseUrl else "$baseUrl/"

        if (retrofit == null || currentBaseUrl != url) {
            val logging = HttpLoggingInterceptor().apply {
                level = HttpLoggingInterceptor.Level.BODY
            }

            val client = OkHttpClient.Builder()
                .addInterceptor(logging)
                .connectTimeout(30, TimeUnit.SECONDS)
                .readTimeout(30, TimeUnit.SECONDS)
                .writeTimeout(30, TimeUnit.SECONDS)
                .build()

            retrofit = Retrofit.Builder()
                .baseUrl(url)
                .client(client)
                .addConverterFactory(GsonConverterFactory.create())
                .build()

            currentBaseUrl = url
        }

        return retrofit!!.create(VoxShieldApi::class.java)
    }
}
