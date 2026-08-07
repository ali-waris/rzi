package com.rzi.quotes.ui.onboarding

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.rzi.quotes.domain.model.PinError
import com.rzi.quotes.domain.repository.AdminRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class PinSetupViewModel @Inject constructor(
    private val adminRepository: AdminRepository,
) : ViewModel() {

    private val _state = MutableStateFlow(PinSetupUiState())
    val state: StateFlow<PinSetupUiState> = _state.asStateFlow()

    fun onPinChange(value: String) {
        _state.value = _state.value.copy(pin = digitFilter(value), error = null)
    }

    fun onConfirmChange(value: String) {
        _state.value = _state.value.copy(confirm = digitFilter(value), error = null)
    }

    fun createPin() {
        val current = _state.value
        if (current.pin != current.confirm) {
            _state.value = current.copy(error = "PINs don't match")
            return
        }
        viewModelScope.launch {
            _state.value = _state.value.copy(isSubmitting = true)
            val error = adminRepository.setPin(current.pin)
            _state.value = _state.value.copy(isSubmitting = false)
            if (error != null) {
                _state.value = _state.value.copy(
                    error = when (error) {
                        PinError.INVALID_DIGITS -> "PIN must be 4 digits"
                        else -> null
                    },
                )
                return@launch
            }
            adminRepository.unlock(current.pin)
        }
    }

    private fun digitFilter(value: String): String =
        value.filter { it in '0'..'9' }.take(4)
}
