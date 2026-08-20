package com.hc.rzi.data.repository

import androidx.paging.Pager
import androidx.paging.PagingConfig
import androidx.paging.PagingData
import androidx.paging.map
import androidx.room.withTransaction
import com.hc.rzi.data.local.RziDatabase
import com.hc.rzi.data.local.dao.BookDao
import com.hc.rzi.data.local.dao.QuoteDao
import com.hc.rzi.data.local.dao.QuoteFtsDao
import com.hc.rzi.data.local.dao.TagDao
import com.hc.rzi.data.local.entity.BookEntity
import com.hc.rzi.data.local.entity.QuoteEntity
import com.hc.rzi.data.local.entity.QuoteFtsEntity
import com.hc.rzi.data.local.entity.QuoteTagCrossRef
import com.hc.rzi.data.local.entity.TagEntity
import com.hc.rzi.data.mapper.toDomain
import com.hc.rzi.domain.model.Book
import com.hc.rzi.domain.model.Quote
import com.hc.rzi.domain.model.QuoteDraft
import com.hc.rzi.domain.model.ReelFilter
import com.hc.rzi.domain.model.ReelMode
import com.hc.rzi.domain.model.SaveQuoteResult
import com.hc.rzi.domain.model.TagFilter
import com.hc.rzi.domain.repository.QuoteRepository
import com.hc.rzi.domain.text.DedupeKey
import com.hc.rzi.domain.text.FtsQuery
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class QuoteRepositoryImpl @Inject constructor(
    private val db: RziDatabase,
    private val quoteDao: QuoteDao,
    private val bookDao: BookDao,
    private val tagDao: TagDao,
    private val ftsDao: QuoteFtsDao,
) : QuoteRepository {

    override fun pagedQuotes(
        query: String,
        tagIds: List<Long>,
        bookIds: List<Long>,
    ): Flow<PagingData<Quote>> {
        val fts = FtsQuery.sanitize(query)
        return Pager(
            config = PagingConfig(pageSize = 25, enablePlaceholders = false),
            pagingSourceFactory = {
                quoteDao.pagingSource(
                    hasQuery = if (fts == null) 0 else 1,
                    ftsQuery = fts.orEmpty(),
                    tagIds = tagIds,
                    tagCount = tagIds.size,
                    bookIds = bookIds,
                    bookCount = bookIds.size,
                )
            },
        ).flow.map { paging -> paging.map { row -> row.toDomain() } }
    }

    override fun observeMatchCount(
        query: String,
        tagIds: List<Long>,
        bookIds: List<Long>,
    ): Flow<Int> {
        val fts = FtsQuery.sanitize(query)
        return quoteDao.observeMatchCount(
            hasQuery = if (fts == null) 0 else 1,
            ftsQuery = fts.orEmpty(),
            tagIds = tagIds,
            tagCount = tagIds.size,
            bookIds = bookIds,
            bookCount = bookIds.size,
        )
    }

    override fun observeReelIds(mode: ReelMode, filter: ReelFilter): Flow<List<Long>> =
        when (mode) {
            ReelMode.SHUFFLE -> quoteDao.observeReelIdsForShuffle(
                filter.bookIds, filter.bookIds.size, filter.tagIds, filter.tagIds.size,
            )
            ReelMode.LINEAR -> quoteDao.observeReelIdsForLinear(
                filter.bookIds, filter.bookIds.size, filter.tagIds, filter.tagIds.size,
            )
        }

    override suspend fun quoteById(id: Long): Quote? = quoteDao.rowById(id)?.toDomain()

    override suspend fun saveValidated(draft: QuoteDraft, nowMillis: Long): SaveQuoteResult =
        db.withTransaction {
            val dedupeKey = DedupeKey.of(draft.text, draft.bookName, draft.pageNumber)
            val clash = quoteDao.entityByDedupeKey(dedupeKey)
            if (clash != null && clash.id != draft.id) return@withTransaction SaveQuoteResult.Duplicate

            val bookId = upsertBook(draft.bookName)
            val existing = draft.id?.let { quoteDao.entityById(it) }

            val quoteId = if (existing == null) {
                val inserted = quoteDao.insertIgnoring(
                    QuoteEntity(
                        text = draft.text,
                        bookId = bookId,
                        pageNumber = draft.pageNumber,
                        dedupeKey = dedupeKey,
                        createdAt = nowMillis,
                        updatedAt = nowMillis,
                    )
                )
                if (inserted == -1L) return@withTransaction SaveQuoteResult.Duplicate
                inserted
            } else {
                quoteDao.update(
                    existing.copy(
                        text = draft.text,
                        bookId = bookId,
                        pageNumber = draft.pageNumber,
                        dedupeKey = dedupeKey,
                        updatedAt = nowMillis,
                    )
                )
                existing.id
            }

            tagDao.unlinkAll(quoteId)
            draft.tags.forEach { name ->
                tagDao.insertIgnoring(TagEntity(name = name))
                val tagId = requireNotNull(tagDao.findByName(name)).id
                tagDao.link(QuoteTagCrossRef(quoteId = quoteId, tagId = tagId))
            }

            ftsDao.upsert(
                QuoteFtsEntity(
                    rowId = quoteId,
                    text = draft.text,
                    bookName = draft.bookName,
                    tagsFlat = draft.tags.joinToString(" "),
                )
            )

            bookDao.deleteOrphans()
            tagDao.deleteOrphans()
            SaveQuoteResult.Saved(quoteId)
        }

    override suspend fun delete(id: Long) {
        db.withTransaction {
            ftsDao.delete(id)
            quoteDao.deleteById(id)
            bookDao.deleteOrphans()
            tagDao.deleteOrphans()
        }
    }

    override suspend fun delete(ids: Set<Long>) {
        if (ids.isEmpty()) return
        db.withTransaction {
            ftsDao.deleteByIds(ids)
            quoteDao.deleteByIds(ids)
            bookDao.deleteOrphans()
            tagDao.deleteOrphans()
        }
    }

    override fun bookSuggestions(prefix: String): Flow<List<String>> = bookDao.suggest(prefix)

    override fun allTagNames(): Flow<List<String>> = tagDao.allNames()

    override fun observeTagFilters(): Flow<List<TagFilter>> =
        tagDao.observeFilters().map { rows ->
            rows.map { TagFilter(id = it.id, name = it.name, usageCount = it.usageCount) }
        }

    override fun observeBooks(): Flow<List<Book>> =
        bookDao.observeAll().map { books -> books.map { Book(id = it.id, name = it.name, quoteCount = it.quoteCount) } }

    override fun observeQuoteCount(): Flow<Int> = quoteDao.observeCount()

    private suspend fun upsertBook(name: String): Long {
        bookDao.insertIgnoring(BookEntity(name = name))
        return requireNotNull(bookDao.findByName(name)) { "Book row missing after upsert: $name" }.id
    }
}
