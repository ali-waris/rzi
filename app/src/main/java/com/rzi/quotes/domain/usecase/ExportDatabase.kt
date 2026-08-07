package com.rzi.quotes.domain.usecase

import com.rzi.quotes.domain.model.ExportOutcome
import com.rzi.quotes.domain.repository.TransferRepository
import javax.inject.Inject

class ExportDatabase @Inject constructor(private val repository: TransferRepository) {
    suspend operator fun invoke(uriString: String): ExportOutcome = repository.export(uriString)
}
