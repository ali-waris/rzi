package com.hc.rzi.domain.usecase

import com.hc.rzi.domain.model.ImportOutcome
import com.hc.rzi.domain.repository.TransferRepository
import javax.inject.Inject

class ImportDatabase @Inject constructor(private val repository: TransferRepository) {
    suspend operator fun invoke(uriString: String): ImportOutcome = repository.import(uriString)
}
