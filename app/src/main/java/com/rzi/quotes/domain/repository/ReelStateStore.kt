package com.rzi.quotes.domain.repository

import com.rzi.quotes.domain.model.ReelPersistedState
import kotlinx.coroutines.flow.Flow

interface ReelStateStore {
    val state: Flow<ReelPersistedState>
    suspend fun update(transform: (ReelPersistedState) -> ReelPersistedState)
}
