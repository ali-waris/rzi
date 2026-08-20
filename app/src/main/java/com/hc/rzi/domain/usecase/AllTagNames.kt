package com.hc.rzi.domain.usecase

import com.hc.rzi.domain.repository.QuoteRepository
import kotlinx.coroutines.flow.Flow
import javax.inject.Inject

class AllTagNames @Inject constructor(private val repository: QuoteRepository) {
    operator fun invoke(): Flow<List<String>> = repository.allTagNames()
}
