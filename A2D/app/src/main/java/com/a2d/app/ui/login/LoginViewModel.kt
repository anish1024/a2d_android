package com.a2d.app.ui.login

import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.a2d.app.data.network.ApiClient
import com.a2d.app.data.network.TokenStore
import com.a2d.app.data.network.HealthResponse
import com.a2d.app.data.network.SyncResult
import com.a2d.app.data.sync.SyncEngine
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import retrofit2.Response
import javax.inject.Inject

data class LoginUiState(
    val email: String = "",
    val password: String = "",
    val isContinueEnabled: Boolean = false,
    val emailError: String? = null,
    val passwordError: String? = null,
    val isLoading: Boolean = false,
    val loginError: String? = null,
    val loginSucceeded: Boolean = false
)

@HiltViewModel
class LoginViewModel @Inject constructor(
    private val apiClient: ApiClient,
    private val tokenStore: TokenStore,
    private val syncEngine: SyncEngine
) : ViewModel() {

    private val _uiState = MutableStateFlow(LoginUiState())
    val uiState = _uiState.asStateFlow()

    // Server health status
    private val _serverHealthy = MutableStateFlow(true)
    val serverHealthy: StateFlow<Boolean> = _serverHealthy.asStateFlow()

    init {
        // Check server health on initialization
        viewModelScope.launch(Dispatchers.IO) {
            checkServerHealth()
        }
    }

    private fun checkServerHealth() {
        try {
            val response: Response<HealthResponse> = apiClient.api.healthCheck().execute()
            _serverHealthy.value = response.isSuccessful && response.body()?.status == "ok"
        } catch (e: Exception) {
            _serverHealthy.value = false
        }
    }

    private val emailRegex = """^[a-zA-Z0-9._%+-]+@[a-zA-Z0-9.-]+\.[a-zA-Z]{2,}$""".toRegex()

    fun onEmailChanged(email: String) {
        val valid = email.isNotEmpty() && email.matches(emailRegex)
        _uiState.value = _uiState.value.copy(
            email = email,
            emailError = if (email.isNotEmpty() && !valid) "Invalid email address" else null,
            isContinueEnabled = valid && _uiState.value.password.isNotBlank()
        )
    }

    fun onPasswordChanged(password: String) {
        _uiState.value = _uiState.value.copy(
            password = password,
            passwordError = null,
            isContinueEnabled = password.isNotBlank() && _uiState.value.email.matches(emailRegex)
        )
    }

    fun onContinueClicked() {
        if (!_uiState.value.isContinueEnabled || _uiState.value.isLoading) return

        _uiState.value = _uiState.value.copy(
            isLoading = true,
            loginError = null
        )

        viewModelScope.launch(Dispatchers.IO) {
            try {
                val response: Response<com.a2d.app.data.network.AuthResponse> =
                    apiClient.api.login(
                        email = _uiState.value.email,
                        password = _uiState.value.password
                    ).execute()

                if (response.isSuccessful) {
                    val body = response.body()
                    if (body != null) {
                        tokenStore.saveTokens(
                            accessToken = body.accessToken,
                            refreshToken = body.refreshToken,
                            expiresIn = body.expiresIn,
                            userId = body.userId,
                            email = _uiState.value.email
                        )
                        // Auto-sync after login to converge with cloud (downloads web-imported data)
                        val syncResult = syncEngine.sync()
                        Log.d("LoginViewModel", "Post-login sync: $syncResult")
                        _uiState.value = _uiState.value.copy(
                            isLoading = false,
                            loginSucceeded = true
                        )
                    } else {
                        _uiState.value = _uiState.value.copy(
                            isLoading = false,
                            loginError = "Empty response from server."
                        )
                    }
                } else {
                    _uiState.value = _uiState.value.copy(
                        isLoading = false,
                        loginError = "Login failed: ${response.code()} ${response.message()}"
                    )
                }
            } catch (e: Exception) {
                Log.e("LoginViewModel", "Login request failed", e)
                _uiState.value = _uiState.value.copy(
                    isLoading = false,
                    loginError = "Unable to connect. Check your internet and try again."
                )
            }
        }
    }
}