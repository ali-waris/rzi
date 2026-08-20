package com.hc.rzi.domain.repository

import androidx.paging.PagingData
import com.hc.rzi.domain.model.Book
import com.hc.rzi.domain.model.Quote
import com.hc.rzi.domain.model.QuoteDraft
import com.hc.rzi.domain.model.ReelFilter
import com.hc.rzi.domain.model.ReelMode
import com.hc.rzi.domain.model.SaveQuoteResult
import com.hc.rzi.domain.model.TagFilter
import kotlinx.coroutines.flow.Flow

interface QuoteRepository {

    fun pagedQuotes(
        query: String,
        tagIds: List<Long>,
        bookIds: List<Long>,
    ): Flow<PagingData<Quote>>

    fun observeMatchCount(
        query: String,
        tagIds: List<Long>,
        bookIds: List<Long>,
    ): Flow<Int>

    fun observeReelIds(mode: ReelMode, filter: ReelFilter): Flow<List<Long>>

    suspend fun quoteById(id: Long): Quote?

    suspend fun saveValidated(draft: QuoteDraft, nowMillis: Long): SaveQuoteResult

    suspend fun delete(id: Long)

    suspend fun delete(ids: Set<Long>)

    fun bookSuggestions(prefix: String): Flow<List<String>>

    fun allTagNames(): Flow<List<String>>

    fun observeTagFilters(): Flow<List<TagFilter>>

    fun observeBooks(): Flow<List<Book>>

    fun observeQuoteCount(): Flow<Int>
}
