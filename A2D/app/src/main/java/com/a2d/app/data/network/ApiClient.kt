package com.a2d.app.data.network

import android.content.Context
import okhttp3.OkHttpClient
import okhttp3.logging.HttpLoggingInterceptor
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory

class ApiClient(private val context: Context) {

    private val client = buildOkHttpClient()
    private val retrofit: Retrofit = Retrofit.Builder()
        .baseUrl("https://devapp8000.anisandbox.org")
        .client(client)
        .addConverterFactory(GsonConverterFactory.create())
        .build()

    val api: A2DSyncApi by lazy { retrofit.create(A2DSyncApi::class.java) }

    private fun buildOkHttpClient(): OkHttpClient {
        val logging = HttpLoggingInterceptor().apply { level = HttpLoggingInterceptor.Level.BODY }
        return OkHttpClient.Builder()
            .addInterceptor(logging)
            .addInterceptor { chain ->
                val token = TokenStore(context).getAccessToken()
                val requestWithToken = chain.request().newBuilder()
                    .addHeader("Authorization", "Bearer $token")
                    .build()
                chain.proceed(requestWithToken)
            }
            .build()
    }
}