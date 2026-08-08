package com.hc.rzi.ui.onboarding

data class PinSetupUiState(
    val pin: String = "",
    val confirm: String = "",
    val error: String? = null,
    val isSubmitting: Boolean = false,
) {
    val canSubmit: Boolean get() = pin.length == 4 && confirm.length == 4 && !isSubmitting
}
