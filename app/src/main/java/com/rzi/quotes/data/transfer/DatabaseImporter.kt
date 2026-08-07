package com.rzi.quotes.data.transfer

import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteException
import androidx.room.withTransaction
import com.rzi.quotes.data.local.RziDatabase
import com.rzi.quotes.data.local.dao.BookDao
import com.rzi.quotes.data.local.dao.QuoteDao
import com.rzi.quotes.data.local.dao.QuoteFtsDao
import com.rzi.quotes.data.local.dao.TagDao
import com.rzi.quotes.data.local.entity.BookEntity
import com.rzi.quotes.data.local.entity.QuoteEntity
import com.rzi.quotes.data.local.entity.QuoteFtsEntity
import com.rzi.quotes.data.local.entity.QuoteTagCrossRef
import com.rzi.quotes.data.local.entity.TagEntity
import com.rzi.quotes.domain.model.ImportOutcome
import com.rzi.quotes.domain.model.ImportResult
import com.rzi.quotes.domain.model.TransferError
import com.rzi.quotes.domain.text.DedupeKey
import java.io.File
import java.time.Clock
import javax.inject.Inject

private data class SourceQuote(
    val text: String,
    val bookName: String,
    val pageNumber: Int?,
    val tags: List<String>,
)

class DatabaseImporter @Inject constructor(
    private val db: RziDatabase,
    private val quoteDao: QuoteDao,
    private val bookDao: BookDao,
    private val tagDao: TagDao,
    private val ftsDao: QuoteFtsDao,
    private val clock: Clock,
) {

    suspend fun import(source: File): ImportOutcome {
        if (!source.exists() || !source.canRead() || source.length() == 0L) {
            return ImportOutcome.Failure(TransferError.UNREADABLE_FILE)
        }

        val quotes = try {
            SQLiteDatabase.openDatabase(source.absolutePath, null, SQLiteDatabase.OPEN_READONLY)
                .use { sqlite ->
                    when {
                        hasNormalizedSchema(sqlite) -> readNormalizedSchema(sqlite)
                        hasLegacyReferencesSchema(sqlite) -> readLegacyReferencesSchema(sqlite)
                        else -> return ImportOutcome.Failure(TransferError.SCHEMA_MISMATCH)
                    }
                }
        } catch (e: SQLiteException) {
            return ImportOutcome.Failure(TransferError.NOT_A_DATABASE)
        }

        if (quotes.isEmpty()) return ImportOutcome.Failure(TransferError.NO_QUOTES_FOUND)

        val valid = quotes.filter { it.text.isNotBlank() && it.bookName.isNotBlank() }
        val skippedInvalid = quotes.size - valid.size
        val importedAt = clock.millis()

        return try {
            var added = 0
            var duplicates = 0
            db.withTransaction {
                valid.forEach { quote ->
                    if (insert(quote, importedAt)) added++ else duplicates++
                }
            }
            ImportOutcome.Success(
                ImportResult(
                    added = added,
                    skippedDuplicates = duplicates,
                    skippedInvalid = skippedInvalid,
                )
            )
        } catch (e: SQLiteException) {
            ImportOutcome.Failure(TransferError.WRITE_FAILED)
        }
    }

    private suspend fun insert(quote: SourceQuote, importedAt: Long): Boolean {
        val text = quote.text.trim()
        val bookName = quote.bookName.trim()
        val dedupeKey = DedupeKey.of(text, bookName, quote.pageNumber)
        if (quoteDao.entityByDedupeKey(dedupeKey) != null) return false

        bookDao.insertIgnoring(BookEntity(name = bookName))
        val bookId = requireNotNull(bookDao.findByName(bookName)).id

        val quoteId = quoteDao.insertIgnoring(
            QuoteEntity(
                text = text,
                bookId = bookId,
                pageNumber = quote.pageNumber?.takeIf { it >= 1 },
                dedupeKey = dedupeKey,
                createdAt = importedAt,
                updatedAt = importedAt,
            )
        )
        if (quoteId == -1L) return false

        quote.tags.forEach { name ->
            val tagName = name.replace(",", "").trim()
            if (tagName.isEmpty()) return@forEach
            tagDao.insertIgnoring(TagEntity(name = tagName))
            val tagId = requireNotNull(tagDao.findByName(tagName)).id
            tagDao.link(QuoteTagCrossRef(quoteId = quoteId, tagId = tagId))
        }

        ftsDao.upsert(
            QuoteFtsEntity(
                rowId = quoteId,
                text = text,
                bookName = bookName,
                tagsFlat = quote.tags.joinToString(" "),
            )
        )
        return true
    }

    private fun hasNormalizedSchema(sqlite: SQLiteDatabase): Boolean =
        NORMALIZED_REQUIRED_COLUMNS.all { (table, columns) ->
            val present = try {
                sqlite.rawQuery("PRAGMA table_info($table)", null).use { cursor ->
                    val names = mutableSetOf<String>()
                    val nameIndex = cursor.getColumnIndexOrThrow("name")
                    while (cursor.moveToNext()) names += cursor.getString(nameIndex)
                    names
                }
            } catch (e: SQLiteException) {
                return false
            }
            present.isNotEmpty() && columns.all { it in present }
        }

    private fun hasLegacyReferencesSchema(sqlite: SQLiteDatabase): Boolean {
        return try {
            sqlite.rawQuery("PRAGMA table_info(\"references\")", null).use { cursor ->
                val names = mutableSetOf<String>()
                val nameIndex = cursor.getColumnIndexOrThrow("name")
                while (cursor.moveToNext()) names += cursor.getString(nameIndex)
                names.containsAll(setOf("id", "text", "book_name"))
            }
        } catch (e: SQLiteException) {
            false
        }
    }

    private fun readNormalizedSchema(sqlite: SQLiteDatabase): List<SourceQuote> {
        val tagsByQuote = mutableMapOf<Long, MutableList<String>>()
        sqlite.rawQuery(
            "SELECT qt.quoteId, t.name FROM quote_tags qt JOIN tags t ON t.id = qt.tagId",
            null,
        ).use { cursor ->
            while (cursor.moveToNext()) {
                tagsByQuote.getOrPut(cursor.getLong(0)) { mutableListOf() } += cursor.getString(1)
            }
        }

        return sqlite.rawQuery(
            "SELECT q.id, q.text, b.name, q.pageNumber " +
                "FROM quotes q LEFT JOIN books b ON b.id = q.bookId",
            null,
        ).use { cursor ->
            buildList {
                while (cursor.moveToNext()) {
                    val id = cursor.getLong(0)
                    add(
                        SourceQuote(
                            text = if (cursor.isNull(1)) "" else cursor.getString(1),
                            bookName = if (cursor.isNull(2)) "" else cursor.getString(2),
                            pageNumber = if (cursor.isNull(3)) null else cursor.getInt(3),
                            tags = tagsByQuote[id]?.distinct().orEmpty(),
                        )
                    )
                }
            }
        }
    }

    private fun readLegacyReferencesSchema(sqlite: SQLiteDatabase): List<SourceQuote> {
        return sqlite.rawQuery(
            "SELECT id, text, book_name, page_number, tags FROM \"references\"",
            null,
        ).use { cursor ->
            val textIndex = cursor.getColumnIndexOrThrow("text")
            val bookNameIndex = cursor.getColumnIndexOrThrow("book_name")
            val pageNumberIndex = cursor.getColumnIndex("page_number")
            val tagsIndex = cursor.getColumnIndex("tags")
            buildList {
                while (cursor.moveToNext()) {
                    val rawTags = if (tagsIndex >= 0 && !cursor.isNull(tagsIndex)) {
                        cursor.getString(tagsIndex)
                            .split(",")
                            .map { it.trim() }
                            .filter { it.isNotEmpty() }
                    } else {
                        emptyList()
                    }
                    add(
                        SourceQuote(
                            text = if (cursor.isNull(textIndex)) "" else cursor.getString(textIndex),
                            bookName = if (cursor.isNull(bookNameIndex)) "" else cursor.getString(bookNameIndex),
                            pageNumber = if (pageNumberIndex >= 0 && !cursor.isNull(pageNumberIndex)) {
                                cursor.getInt(pageNumberIndex)
                            } else {
                                null
                            },
                            tags = rawTags,
                        )
                    )
                }
            }
        }
    }

    private companion object {
        val NORMALIZED_REQUIRED_COLUMNS = mapOf(
            "quotes" to listOf("id", "text", "bookId", "pageNumber"),
            "books" to listOf("id", "name"),
            "tags" to listOf("id", "name"),
            "quote_tags" to listOf("quoteId", "tagId"),
        )
    }
}
