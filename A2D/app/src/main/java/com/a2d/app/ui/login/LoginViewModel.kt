package com.a2d.app.ui.login

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

data class LoginUiState(
    val email: String = "",
    val isContinueEnabled: Boolean = false,
    val emailError: String? = null,
    val isLoading: Boolean = false,
    val loginError: String? = null,
    val loginSucceeded: Boolean = false
)

@HiltViewModel
class LoginViewModel @Inject constructor() : ViewModel() {
    private val _uiState = MutableStateFlow(LoginUiState())
    val uiState = _uiState.asStateFlow()

    private val emailRegex = """^[a-zA-Z0-9._%+-]+@[a-zA-Z0-9.-]+\.[a-zA-Z]{2,}$""".toRegex()

    fun onEmailChanged(email: String) {
        val valid = email.isNotEmpty() && email.matches(emailRegex)
        _uiState.value = _uiState.value.copy(
            email = email,
            isContinueEnabled = valid,
            emailError = if (email.isNotEmpty() && !valid) "Invalid email address" else null
        )
    }

    fun onContinueClicked() {
        if (!_uiState.value.isContinueEnabled || _uiState.value.isLoading) return

        _uiState.value = _uiState.value.copy(
            isLoading = true,
            loginError = null
        )

        viewModelScope.launch(Dispatchers.Default) {
            // TODO: replace with real backend auth call (invite-only create/login) when SSO /
            // email+password verification is implemented.
            try {
                kotlinx.coroutines.delay(800)
                _uiState.value = _uiState.value.copy(
                    isLoading = false,
                    loginSucceeded = true
                )
            } catch (e: Exception) {
                _uiState.value = _uiState.value.copy(
                    isLoading = false,
                    loginError = "Unable to continue. Try again."
                )
            }
        }
    }
}