package com.a2d.app.ui.auth

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.a2d.app.data.network.ApiClient
import com.a2d.app.data.network.TokenStore
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

sealed class LogoutState {
    object Idle : LogoutState()
    object Success : LogoutState()
    data class Error(val message: String) : LogoutState()
}

@HiltViewModel
class AuthViewModel @Inject constructor(
    private val apiClient: ApiClient,
    private val tokenStore: TokenStore
) : ViewModel() {

    private val _logoutState = MutableStateFlow<LogoutState>(LogoutState.Idle)
    val logoutState: StateFlow<LogoutState> = _logoutState.asStateFlow()

    private val _isAuthenticated = MutableStateFlow(tokenStore.isAuthenticated())
    val isAuthenticated: StateFlow<Boolean> = _isAuthenticated.asStateFlow()

    /** Email of the currently logged-in user, or empty string if none. */
    val userEmail: String get() = tokenStore.getUserEmail()

    init {
        _isAuthenticated.value = tokenStore.isAuthenticated()
    }

    fun logout() {
        viewModelScope.launch(Dispatchers.IO) {
            try {
                apiClient.api.logout().execute()
                // Clear local tokens and mark as not authenticated
                tokenStore.clearTokens()
                _isAuthenticated.value = false
                _logoutState.value = LogoutState.Success
            } catch (_: Exception) {
                // Ignore network errors; clear local tokens regardless.
                tokenStore.clearTokens()
                _isAuthenticated.value = false
                _logoutState.value = LogoutState.Error("Logout failed")
            }
        }
    }

    fun resetLogoutState() {
        _logoutState.value = LogoutState.Idle
    }

    fun refreshAuthState() {
        _isAuthenticated.value = tokenStore.isAuthenticated()
    }
}
