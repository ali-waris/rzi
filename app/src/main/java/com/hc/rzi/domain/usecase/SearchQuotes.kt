package com.hc.rzi.domain.usecase

import androidx.paging.PagingData
import com.hc.rzi.domain.model.Quote
import com.hc.rzi.domain.repository.QuoteRepository
import kotlinx.coroutines.flow.Flow
import javax.inject.Inject

class SearchQuotes @Inject constructor(private val repository: QuoteRepository) {
    operator fun invoke(query: String, tagIds: List<Long>): Flow<PagingData<Quote>> =
        repository.pagedQuotes(query, tagIds)
}
