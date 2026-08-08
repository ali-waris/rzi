package com.hc.rzi.ui.library

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.hc.rzi.domain.model.PinError
import com.hc.rzi.domain.repository.AdminRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

sealed interface AdminPinEvent {
    data object Unlocked : AdminPinEvent
    data object Changed : AdminPinEvent
}

@HiltViewModel
class AdminPinViewModel @Inject constructor(
    private val adminRepository: AdminRepository,
) : ViewModel() {

    private val _state = MutableStateFlow(AdminPinUiState())
    val state: StateFlow<AdminPinUiState> = _state.asStateFlow()

    private val _events = Channel<AdminPinEvent>(Channel.BUFFERED)
    val events = _events.receiveAsFlow()

    fun start(mode: AdminPinMode) {
        _state.value = AdminPinUiState(mode = mode)
    }

    fun onCurrentChange(value: String) {
        _state.value = _state.value.copy(currentPin = digitFilter(value), error = null)
    }

    fun onNewChange(value: String) {
        _state.value = _state.value.copy(newPin = digitFilter(value), error = null)
    }

    fun onConfirmChange(value: String) {
        _state.value = _state.value.copy(confirmPin = digitFilter(value), error = null)
    }

    fun submit() {
        when (_state.value.mode) {
            AdminPinMode.UNLOCK -> unlock()
            AdminPinMode.CHANGE -> change()
        }
    }

    private fun unlock() {
        val current = _state.value
        if (current.isSubmitting) return
        _state.value = current.copy(isSubmitting = true)
        viewModelScope.launch {
            val ok = adminRepository.unlock(current.currentPin)
            if (ok) {
                _state.value = AdminPinUiState(mode = current.mode)
                _events.send(AdminPinEvent.Unlocked)
            } else {
                _state.value = current.copy(isSubmitting = false, error = "Incorrect PIN", currentPin = "")
            }
        }
    }

    private fun change() {
        val current = _state.value
        if (current.newPin != current.confirmPin) {
            _state.value = current.copy(error = "PINs don't match")
            return
        }
        if (current.isSubmitting) return
        _state.value = current.copy(isSubmitting = true)
        viewModelScope.launch {
            val error = adminRepository.changePin(current.currentPin, current.newPin)
            _state.value = current.copy(isSubmitting = false)
            when (error) {
                null -> {
                    _state.value = AdminPinUiState(mode = current.mode)
                    _events.send(AdminPinEvent.Changed)
                }
                PinError.INVALID_DIGITS -> _state.value = current.copy(error = "PIN must be 4 digits")
                PinError.INCORRECT -> _state.value = current.copy(error = "Incorrect PIN", currentPin = "")
                PinError.MISMATCH -> Unit
            }
        }
    }

    private fun digitFilter(value: String): String =
        value.filter { it in '0'..'9' }.take(4)
}
