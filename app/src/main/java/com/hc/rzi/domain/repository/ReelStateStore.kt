package com.hc.rzi.domain.repository

import com.hc.rzi.domain.model.ReelPersistedState
import kotlinx.coroutines.flow.Flow

interface ReelStateStore {
    val state: Flow<ReelPersistedState>
    suspend fun update(transform: (ReelPersistedState) -> ReelPersistedState)
}
