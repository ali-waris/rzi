package com.hc.rzi.domain.usecase

import com.hc.rzi.domain.model.ExportOutcome
import com.hc.rzi.domain.repository.TransferRepository
import javax.inject.Inject

class ExportDatabase @Inject constructor(private val repository: TransferRepository) {
    suspend operator fun invoke(uriString: String): ExportOutcome = repository.export(uriString)
}
