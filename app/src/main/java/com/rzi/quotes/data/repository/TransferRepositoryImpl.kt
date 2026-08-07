package com.rzi.quotes.data.repository

import android.content.Context
import android.net.Uri
import com.rzi.quotes.data.transfer.DatabaseExporter
import com.rzi.quotes.data.transfer.DatabaseImporter
import com.rzi.quotes.domain.model.ExportOutcome
import com.rzi.quotes.domain.model.ImportOutcome
import com.rzi.quotes.domain.repository.TransferRepository
import dagger.hilt.android.qualifiers.ApplicationContext
import java.io.File
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class TransferRepositoryImpl @Inject constructor(
    @ApplicationContext private val context: Context,
    private val importer: DatabaseImporter,
    private val exporter: DatabaseExporter,
) : TransferRepository {

    override suspend fun import(uriString: String): ImportOutcome {
        val cacheFile = File(context.cacheDir, "import_source.db")
        return try {
            context.contentResolver.openInputStream(Uri.parse(uriString))?.use { input ->
                cacheFile.outputStream().use { output -> input.copyTo(output) }
            } ?: return ImportOutcome.Failure(
                com.rzi.quotes.domain.model.TransferError.UNREADABLE_FILE
            )
            importer.import(cacheFile)
        } finally {
            cacheFile.delete()
        }
    }

    override suspend fun export(uriString: String): ExportOutcome {
        val target = File(context.cacheDir, "export_target.db")
        return try {
            val result = exporter.exportToFile(target)
            if (result is ExportOutcome.Success) {
                context.contentResolver.openOutputStream(Uri.parse(uriString))?.use { output ->
                    target.inputStream().use { input -> input.copyTo(output) }
                } ?: return ExportOutcome.Failure(
                    com.rzi.quotes.domain.model.TransferError.WRITE_FAILED
                )
            }
            result
        } finally {
            target.delete()
        }
    }
}
