package com.rzi.quotes.domain.usecase

import com.rzi.quotes.domain.model.ImportOutcome
import com.rzi.quotes.domain.repository.TransferRepository
import javax.inject.Inject

class ImportDatabase @Inject constructor(private val repository: TransferRepository) {
    suspend operator fun invoke(uriString: String): ImportOutcome = repository.import(uriString)
}
