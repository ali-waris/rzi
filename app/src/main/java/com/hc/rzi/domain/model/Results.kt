package com.hc.rzi.domain.model

data class ValidationErrors(
    val text: String? = null,
    val bookName: String? = null,
    val pageNumber: String? = null,
) {
    val hasErrors: Boolean get() = text != null || bookName != null || pageNumber != null
}

sealed interface SaveQuoteResult {
    data class Saved(val id: Long) : SaveQuoteResult
    data object Duplicate : SaveQuoteResult
    data class Invalid(val errors: ValidationErrors) : SaveQuoteResult
}

data class ImportResult(
    val added: Int,
    val skippedDuplicates: Int,
    val skippedInvalid: Int,
)

enum class PinError {
    INVALID_DIGITS,
    INCORRECT,
    MISMATCH,
}

enum class TransferError {
    UNREADABLE_FILE,
    NOT_A_DATABASE,
    SCHEMA_MISMATCH,
    NO_QUOTES_FOUND,
    WRITE_FAILED,
}

sealed interface ImportOutcome {
    data class Success(val result: ImportResult) : ImportOutcome
    data class Failure(val reason: TransferError) : ImportOutcome
}

sealed interface ExportOutcome {
    data object Success : ExportOutcome
    data class Failure(val reason: TransferError) : ExportOutcome
}
