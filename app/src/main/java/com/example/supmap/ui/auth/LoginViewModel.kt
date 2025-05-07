package com.example.supmap.ui.auth

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.example.supmap.data.repository.AuthService
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch

class LoginViewModel(private val authService: AuthService) : ViewModel() {

    private val _googleSignInState = MutableStateFlow<LoginState>(LoginState.Idle)
    val googleSignInState: StateFlow<LoginState> = _googleSignInState
    private val _loginState = MutableStateFlow<LoginState>(LoginState.Idle)
    val loginState: StateFlow<LoginState> = _loginState

    fun login(email: String, password: String) {
        _loginState.value = LoginState.Loading

        viewModelScope.launch {
            authService.login(email, password)
                .onSuccess {
                    _loginState.value = LoginState.Success
                }
                .onFailure { error ->
                    _loginState.value = LoginState.Error(error.message ?: "Erreur de connexion")
                }
        }
    }

    fun loginWithGoogle(idToken: String) {
        _googleSignInState.value = LoginState.Loading

        viewModelScope.launch {
            authService.loginWithGoogle(idToken)
                .onSuccess {
                    _googleSignInState.value = LoginState.Success
                }
                .onFailure { error ->
                    _googleSignInState.value =
                        LoginState.Error(error.message ?: "Erreur d'authentification Google")
                }
        }
    }

    fun resetState() {
        _loginState.value = LoginState.Idle
    }

    class Factory(private val context: Context) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T {
            if (modelClass.isAssignableFrom(LoginViewModel::class.java)) {
                return LoginViewModel(AuthService(context)) as T
            }
            throw IllegalArgumentException("Unknown ViewModel class")
        }
    }
}

sealed class LoginState {
    object Idle : LoginState()
    object Loading : LoginState()
    object Success : LoginState()
    data class Error(val message: String) : LoginState()
}

