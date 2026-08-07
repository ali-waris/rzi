package com.rzi.quotes.data.transfer

import android.content.Context
import android.database.sqlite.SQLiteDatabase
import com.rzi.quotes.data.local.dao.QuoteDao
import com.rzi.quotes.domain.model.ExportOutcome
import com.rzi.quotes.domain.model.TransferError
import dagger.hilt.android.qualifiers.ApplicationContext
import java.io.File
import java.time.Clock
import java.time.Instant
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter
import javax.inject.Inject

class DatabaseExporter @Inject constructor(
    @ApplicationContext private val context: Context,
    private val quoteDao: QuoteDao,
    private val clock: Clock,
) {

    suspend fun exportToFile(target: File): ExportOutcome {
        return try {
            val rows = quoteDao.searchRows(
                hasQuery = 0,
                ftsQuery = "",
                tagIds = emptyList(),
                tagCount = 0,
            )

            SQLiteDatabase.openOrCreateDatabase(target, null).use { sqlite ->
                sqlite.execSQL(
                    "CREATE TABLE IF NOT EXISTS \"references\" (" +
                        "id INTEGER PRIMARY KEY AUTOINCREMENT, " +
                        "text TEXT NOT NULL, " +
                        "book_name TEXT NOT NULL, " +
                        "page_number INTEGER, " +
                        "tags TEXT, " +
                        "created_at TEXT DEFAULT (datetime('now')))"
                )

                val stmt = sqlite.compileStatement(
                    "INSERT INTO \"references\" (text, book_name, page_number, tags, created_at) " +
                        "VALUES (?, ?, ?, ?, ?)"
                )

                sqlite.beginTransaction()
                try {
                    rows.forEach { row ->
                        stmt.clearBindings()
                        stmt.bindString(1, row.text)
                        stmt.bindString(2, row.bookName)
                        if (row.pageNumber != null) {
                            stmt.bindLong(3, row.pageNumber.toLong())
                        }
                        if (row.tagsCsv != null) {
                            stmt.bindString(4, row.tagsCsv)
                        }
                        val createdAt = Instant.ofEpochMilli(row.createdAt)
                            .atZone(ZoneOffset.UTC)
                            .format(DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ss"))
                        stmt.bindString(5, createdAt)
                        stmt.executeInsert()
                    }
                    sqlite.setTransactionSuccessful()
                } finally {
                    sqlite.endTransaction()
                }
            }

            ExportOutcome.Success
        } catch (_: Exception) {
            ExportOutcome.Failure(TransferError.WRITE_FAILED)
        }
    }
}
