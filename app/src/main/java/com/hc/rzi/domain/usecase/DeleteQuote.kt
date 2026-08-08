package com.hc.rzi.domain.usecase

import com.hc.rzi.domain.repository.QuoteRepository
import javax.inject.Inject

class DeleteQuote @Inject constructor(private val repository: QuoteRepository) {
    suspend operator fun invoke(id: Long) = repository.delete(id)
}
