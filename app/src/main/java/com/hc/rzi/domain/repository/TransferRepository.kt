package com.hc.rzi.domain.repository

import com.hc.rzi.domain.model.ExportOutcome
import com.hc.rzi.domain.model.ImportOutcome

interface TransferRepository {
    suspend fun import(uriString: String): ImportOutcome
    suspend fun export(uriString: String): ExportOutcome
}
