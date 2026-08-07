package com.rzi.quotes.domain.usecase

import com.rzi.quotes.domain.repository.QuoteRepository
import kotlinx.coroutines.flow.Flow
import javax.inject.Inject

class TagSuggestions @Inject constructor(private val repository: QuoteRepository) {
    operator fun invoke(prefix: String): Flow<List<String>> = repository.tagSuggestions(prefix)
}
