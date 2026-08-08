package com.hc.rzi.domain.usecase

import com.hc.rzi.domain.repository.QuoteRepository
import kotlinx.coroutines.flow.Flow
import javax.inject.Inject

class BookSuggestions @Inject constructor(private val repository: QuoteRepository) {
    operator fun invoke(prefix: String): Flow<List<String>> = repository.bookSuggestions(prefix)
}
