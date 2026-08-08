package com.hc.rzi.domain.usecase

import com.hc.rzi.domain.model.QuoteDraft
import com.hc.rzi.domain.model.SaveQuoteResult
import com.hc.rzi.domain.model.ValidationErrors
import com.hc.rzi.domain.repository.QuoteRepository
import java.time.Clock
import javax.inject.Inject

class SaveQuote @Inject constructor(
    private val repository: QuoteRepository,
    private val clock: Clock,
) {

    suspend operator fun invoke(draft: QuoteDraft): SaveQuoteResult {
        val text = draft.text.trim()
        val bookName = draft.bookName.trim()

        val errors = ValidationErrors(
            text = if (text.isEmpty()) "Quote text can't be empty" else null,
            bookName = if (bookName.isEmpty()) "Book name can't be empty" else null,
            pageNumber = draft.pageNumber
                ?.takeIf { it < 1 }
                ?.let { "Page number must be 1 or higher" },
        )
        if (errors.hasErrors) return SaveQuoteResult.Invalid(errors)

        val tags = draft.tags
            .map { it.replace(",", "").trim() }
            .filter { it.isNotEmpty() }
            .distinctBy { it.lowercase() }

        return repository.saveValidated(
            draft = draft.copy(text = text, bookName = bookName, tags = tags),
            nowMillis = clock.millis(),
        )
    }
}
