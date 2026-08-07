package com.rzi.quotes.ui.library

enum class AdminPinMode { UNLOCK, CHANGE }

data class AdminPinUiState(
    val mode: AdminPinMode = AdminPinMode.UNLOCK,
    val currentPin: String = "",
    val newPin: String = "",
    val confirmPin: String = "",
    val error: String? = null,
    val isSubmitting: Boolean = false,
) {
    val canSubmit: Boolean get() = when (mode) {
        AdminPinMode.UNLOCK -> currentPin.length == 4 && !isSubmitting
        AdminPinMode.CHANGE -> currentPin.length == 4 && newPin.length == 4 && confirmPin.length == 4 && !isSubmitting
    }
}
