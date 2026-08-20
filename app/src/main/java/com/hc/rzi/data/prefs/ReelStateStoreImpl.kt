package com.hc.rzi.data.prefs

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import com.hc.rzi.domain.model.ReelFilter
import com.hc.rzi.domain.model.ReelMode
import com.hc.rzi.domain.model.ReelPersistedState
import com.hc.rzi.domain.repository.ReelStateStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class ReelStateStoreImpl @Inject constructor(
    private val dataStore: DataStore<Preferences>,
) : ReelStateStore {

    override val state: Flow<ReelPersistedState> = dataStore.data.map { prefs ->
        ReelPersistedState(
            mode = prefs[MODE]?.let(ReelMode::valueOf) ?: ReelMode.SHUFFLE,
            baseSeed = prefs[BASE_SEED] ?: 0L,
            absoluteIndex = prefs[INDEX] ?: 0,
            currentQuoteId = prefs[QUOTE_ID]?.takeIf { prefs[HAS_QUOTE_ID] == true },
            filter = ReelFilter(
                bookIds = prefs[BOOK_IDS]
                    ?.split(',')
                    ?.filter { it.isNotBlank() }
                    ?.map { it.toLong() }
                    .orEmpty()
                    .ifEmpty {
                        // Migrate legacy single bookId
                        prefs[BOOK_ID]?.takeIf { prefs[HAS_BOOK_ID] == true }?.let { listOf(it) }.orEmpty()
                    },
                tagIds = prefs[TAG_IDS]
                    ?.split(',')
                    ?.filter { it.isNotBlank() }
                    ?.map { it.toLong() }
                    .orEmpty(),
            ),
        )
    }

    override suspend fun update(transform: (ReelPersistedState) -> ReelPersistedState) {
        val next = transform(state.first())
        dataStore.edit { prefs ->
            prefs[MODE] = next.mode.name
            prefs[BASE_SEED] = next.baseSeed
            prefs[INDEX] = next.absoluteIndex
            prefs[HAS_QUOTE_ID] = next.currentQuoteId != null
            prefs[QUOTE_ID] = next.currentQuoteId ?: 0L
            prefs[BOOK_IDS] = next.filter.bookIds.joinToString(",")
            // Clear legacy keys
            prefs.remove(BOOK_ID)
            prefs.remove(HAS_BOOK_ID)
            prefs[TAG_IDS] = next.filter.tagIds.joinToString(",")
        }
    }

    private companion object {
        val MODE = stringPreferencesKey("reel_mode")
        val BASE_SEED = longPreferencesKey("reel_base_seed")
        val INDEX = intPreferencesKey("reel_index")
        val QUOTE_ID = longPreferencesKey("reel_quote_id")
        val HAS_QUOTE_ID = booleanPreferencesKey("reel_has_quote_id")
        val BOOK_IDS = stringPreferencesKey("reel_book_ids")
        val BOOK_ID = longPreferencesKey("reel_book_id")
        val HAS_BOOK_ID = booleanPreferencesKey("reel_has_book_id")
        val TAG_IDS = stringPreferencesKey("reel_tag_ids")
    }
}
