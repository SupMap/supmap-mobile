package com.example.supmap.data.api

import android.content.Context
import com.google.gson.GsonBuilder
import okhttp3.Interceptor
import okhttp3.OkHttpClient
import retrofit2.Retrofit
import retrofit2.converter.scalars.ScalarsConverterFactory
import retrofit2.converter.gson.GsonConverterFactory

object NetworkModule {

    private lateinit var appContext: Context

    fun initialize(context: Context) {
        appContext = context.applicationContext
    }

    private val authInterceptor = Interceptor { chain ->
        val request = chain.request()
        val token = appContext
            .getSharedPreferences("user_prefs", Context.MODE_PRIVATE)
            .getString("auth_token", "")
            .orEmpty()

        val authRequest = if (token.isNotBlank()) {
            val headerValue =
                if (token.startsWith("Bearer", ignoreCase = true)) token else "Bearer $token"
            request.newBuilder()
                .header("Authorization", headerValue)
                .build()
        } else {
            request
        }
        chain.proceed(authRequest)
    }

    private val client by lazy {
        OkHttpClient.Builder()
            .addInterceptor(authInterceptor)
            .build()
    }

    private val gson by lazy {
        GsonBuilder()
            .registerTypeAdapter(DirectionsResponse::class.java, DirectionsResponseDeserializer())
            .create()
    }

    val retrofit: Retrofit by lazy {
        Retrofit.Builder()
            .baseUrl(ApiConfig.BASE_URL)
            .addConverterFactory(ScalarsConverterFactory.create())
            .addConverterFactory(GsonConverterFactory.create(gson))
            .client(client)
            .build()
    }

    inline fun <reified T> createService(): T = retrofit.create(T::class.java)

}
