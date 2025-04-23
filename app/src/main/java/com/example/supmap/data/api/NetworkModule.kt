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

    /** Doit être appelé une seule fois, idéalement dans l’Application */
    fun initialize(context: Context) {
        appContext = context.applicationContext
    }

    /** Intercepteur pour injecter automatiquement le token Bearer */
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

    /** Retrofit configuré avec ScalarsConverter en priorité, puis GsonConverter */
    val retrofit: Retrofit by lazy {
        Retrofit.Builder()
            .baseUrl("http://10.0.2.2:8080/api/")
            // 1) Pour gérer les réponses brutes (String) du POST /incidents
            .addConverterFactory(ScalarsConverterFactory.create())
            // 2) Pour parser les réponses JSON des autres endpoints
            .addConverterFactory(GsonConverterFactory.create(gson))
            .client(client)
            .build()
    }

    /** Crée un service Retrofit typé */
    inline fun <reified T> createService(): T = retrofit.create(T::class.java)
}
