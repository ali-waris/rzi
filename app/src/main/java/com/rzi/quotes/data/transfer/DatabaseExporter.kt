package com.rzi.quotes.data.transfer

import android.content.Context
import androidx.room.withTransaction
import com.rzi.quotes.data.local.RziDatabase
import com.rzi.quotes.domain.model.ExportOutcome
import com.rzi.quotes.domain.model.TransferError
import dagger.hilt.android.qualifiers.ApplicationContext
import java.io.File
import java.time.Clock
import java.time.format.DateTimeFormatter
import javax.inject.Inject

class DatabaseExporter @Inject constructor(
    @ApplicationContext private val context: Context,
    private val db: RziDatabase,
    private val clock: Clock,
) {

    fun suggestedFileName(): String {
        val date = DateTimeFormatter.ofPattern("yyyy-MM-dd").format(
            java.time.Instant.ofEpochMilli(clock.millis()).atZone(java.time.ZoneOffset.UTC)
        )
        return "rzi-quotes-$date.db"
    }

    suspend fun exportToFile(target: File): ExportOutcome {
        return try {
            db.withTransaction {
                db.openHelper.writableDatabase.execSQL("PRAGMA wal_checkpoint(TRUNCATE)")
            }
            val source = File(context.getDatabasePath(RziDatabase.NAME).absolutePath)
            source.copyTo(target, overwrite = true)
            ExportOutcome.Success
        } catch (e: Exception) {
            ExportOutcome.Failure(TransferError.WRITE_FAILED)
        }
    }
}
