package com.rzi.quotes.domain.usecase

import com.rzi.quotes.domain.repository.QuoteRepository
import javax.inject.Inject

class DeleteQuote @Inject constructor(private val repository: QuoteRepository) {
    suspend operator fun invoke(id: Long) = repository.delete(id)
}
