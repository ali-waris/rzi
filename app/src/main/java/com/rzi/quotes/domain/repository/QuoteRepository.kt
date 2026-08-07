package com.rzi.quotes.domain.repository

import androidx.paging.PagingData
import com.rzi.quotes.domain.model.Book
import com.rzi.quotes.domain.model.Quote
import com.rzi.quotes.domain.model.QuoteDraft
import com.rzi.quotes.domain.model.ReelFilter
import com.rzi.quotes.domain.model.ReelMode
import com.rzi.quotes.domain.model.SaveQuoteResult
import com.rzi.quotes.domain.model.TagFilter
import kotlinx.coroutines.flow.Flow

interface QuoteRepository {

    fun pagedQuotes(query: String, tagIds: List<Long>): Flow<PagingData<Quote>>

    fun observeMatchCount(query: String, tagIds: List<Long>): Flow<Int>

    fun observeReelIds(mode: ReelMode, filter: ReelFilter): Flow<List<Long>>

    suspend fun quoteById(id: Long): Quote?

    suspend fun saveValidated(draft: QuoteDraft, nowMillis: Long): SaveQuoteResult

    suspend fun delete(id: Long)

    fun bookSuggestions(prefix: String): Flow<List<String>>

    fun tagSuggestions(prefix: String): Flow<List<String>>

    fun observeTagFilters(): Flow<List<TagFilter>>

    fun observeBooks(): Flow<List<Book>>

    fun observeQuoteCount(): Flow<Int>
}
