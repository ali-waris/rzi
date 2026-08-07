package com.rzi.quotes.domain.repository

import com.rzi.quotes.domain.model.ExportOutcome
import com.rzi.quotes.domain.model.ImportOutcome

interface TransferRepository {
    suspend fun import(uriString: String): ImportOutcome
    suspend fun export(uriString: String): ExportOutcome
}
