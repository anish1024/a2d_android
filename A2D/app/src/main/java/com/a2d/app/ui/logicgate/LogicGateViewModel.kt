package com.a2d.app.ui.logicgate

import androidx.lifecycle.ViewModel
import com.a2d.app.domain.engine.SerialAndGateProcessor
import com.a2d.app.domain.model.PulseResult
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import javax.inject.Inject

@HiltViewModel
class LogicGateViewModel @Inject constructor(
    private val processor: SerialAndGateProcessor
) : ViewModel() {

    private val _lastResult = MutableStateFlow<PulseResult?>(null)
    val lastResult: StateFlow<PulseResult?> = _lastResult.asStateFlow()

    private val _errorMessage = MutableStateFlow<String?>(null)
    val errorMessage: StateFlow<String?> = _errorMessage.asStateFlow()

    fun sendPulse(pin: String, pulseType: String) {
        try {
            _errorMessage.value = null
            _lastResult.value = processor.processPulse(pin, pulseType)
        } catch (e: Exception) {
            _errorMessage.value = e.message
        }
    }
}
