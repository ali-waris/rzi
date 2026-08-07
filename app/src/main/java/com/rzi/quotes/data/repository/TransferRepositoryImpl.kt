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
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

@Singleton
class TransferRepositoryImpl @Inject constructor(
    @ApplicationContext private val context: Context,
    private val importer: DatabaseImporter,
    private val exporter: DatabaseExporter,
) : TransferRepository {

    override suspend fun import(uriString: String): ImportOutcome = withContext(Dispatchers.IO) {
        val cacheFile = File(context.cacheDir, "import_source.db")
        try {
            context.contentResolver.openInputStream(Uri.parse(uriString))?.use { input ->
                cacheFile.outputStream().use { output -> input.copyTo(output) }
            } ?: return@withContext ImportOutcome.Failure(
                com.rzi.quotes.domain.model.TransferError.UNREADABLE_FILE
            )
            importer.import(cacheFile)
        } finally {
            cacheFile.delete()
        }
    }

    override suspend fun export(uriString: String): ExportOutcome = withContext(Dispatchers.IO) {
        val target = File(context.cacheDir, "export_target.db")
        try {
            val result = exporter.exportToFile(target)
            if (result is ExportOutcome.Success) {
                try {
                    context.contentResolver.openOutputStream(Uri.parse(uriString))?.use { output ->
                        target.inputStream().use { input -> input.copyTo(output) }
                        output.flush()
                    } ?: return@withContext ExportOutcome.Failure(
                        com.rzi.quotes.domain.model.TransferError.WRITE_FAILED
                    )
                } catch (e: Exception) {
                    return@withContext ExportOutcome.Failure(
                        com.rzi.quotes.domain.model.TransferError.WRITE_FAILED
                    )
                }
            }
            result
        } finally {
            target.delete()
        }
    }
}
