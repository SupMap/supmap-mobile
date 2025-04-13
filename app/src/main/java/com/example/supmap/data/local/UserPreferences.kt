package com.example.supmap.data.local

import android.content.Context
import android.content.SharedPreferences
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

class UserPreferences(context: Context) {
    private val prefs: SharedPreferences =
        context.getSharedPreferences("user_prefs", Context.MODE_PRIVATE)

    private val _authToken = MutableStateFlow<String?>(prefs.getString("auth_token", null))
    val authToken: StateFlow<String?> = _authToken

    fun saveAuthToken(token: String) {
        prefs.edit().putString("auth_token", token).apply()
        _authToken.value = token
    }

    fun clearAuthToken() {
        prefs.edit().remove("auth_token").apply()
        _authToken.value = null
    }

    fun isLoggedIn(): Boolean {
        return !_authToken.value.isNullOrEmpty()
    }
}