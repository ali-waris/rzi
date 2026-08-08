package com.hc.rzi.testutil

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.hc.rzi.data.local.RziDatabase
import com.hc.rzi.data.local.entity.BookEntity
import com.hc.rzi.data.local.entity.QuoteEntity
import com.hc.rzi.data.local.entity.QuoteFtsEntity
import com.hc.rzi.data.local.entity.QuoteTagCrossRef
import com.hc.rzi.data.local.entity.TagEntity
import com.hc.rzi.domain.text.DedupeKey

object DbFixtures {

    fun inMemoryDatabase(): RziDatabase {
        val context = ApplicationProvider.getApplicationContext<Context>()
        return Room.inMemoryDatabaseBuilder(context, RziDatabase::class.java)
            .allowMainThreadQueries()
            .build()
    }

    suspend fun insertQuote(
        db: RziDatabase,
        text: String,
        bookName: String,
        pageNumber: Int? = null,
        tags: List<String> = emptyList(),
        createdAt: Long = 1_000L,
    ): Long {
        val bookDao = db.bookDao()
        val tagDao = db.tagDao()
        bookDao.insertIgnoring(BookEntity(name = bookName))
        val bookId = requireNotNull(bookDao.findByName(bookName)).id

        val quoteId = db.quoteDao().insertIgnoring(
            QuoteEntity(
                text = text,
                bookId = bookId,
                pageNumber = pageNumber,
                dedupeKey = DedupeKey.of(text, bookName, pageNumber),
                createdAt = createdAt,
                updatedAt = createdAt,
            )
        )
        require(quoteId != -1L) { "Fixture quote was a duplicate: $text" }

        tags.forEach { name ->
            tagDao.insertIgnoring(TagEntity(name = name))
            val tagId = requireNotNull(tagDao.findByName(name)).id
            tagDao.link(QuoteTagCrossRef(quoteId = quoteId, tagId = tagId))
        }

        db.quoteFtsDao().upsert(
            QuoteFtsEntity(
                rowId = quoteId,
                text = text,
                bookName = bookName,
                tagsFlat = tags.joinToString(" "),
            )
        )
        return quoteId
    }
}
