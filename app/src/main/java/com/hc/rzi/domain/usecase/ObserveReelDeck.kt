package com.hc.rzi.domain.usecase

import com.hc.rzi.domain.model.ReelFilter
import com.hc.rzi.domain.model.ReelMode
import com.hc.rzi.domain.reel.Deck
import com.hc.rzi.domain.reel.Decks
import com.hc.rzi.domain.repository.QuoteRepository
import com.hc.rzi.domain.repository.ReelStateStore
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.map
import javax.inject.Inject

data class ReelDeckState(
    val deck: Deck,
    val index: Int,
    val mode: ReelMode,
    val filter: ReelFilter,
)

class ObserveReelDeck @Inject constructor(
    private val repository: QuoteRepository,
    private val store: ReelStateStore,
) {

    @OptIn(ExperimentalCoroutinesApi::class)
    operator fun invoke(): Flow<ReelDeckState> = store.state
        .distinctUntilChanged()
        .flatMapLatest { persisted ->
            repository.observeReelIds(persisted.mode, persisted.filter).map { ids ->
                val deck = Decks.create(persisted.mode, ids, persisted.baseSeed)
                ReelDeckState(
                    deck = deck,
                    index = resolveIndex(deck, persisted.absoluteIndex, persisted.currentQuoteId),
                    mode = persisted.mode,
                    filter = persisted.filter,
                )
            }
        }

    private fun resolveIndex(deck: Deck, savedIndex: Int, savedQuoteId: Long?): Int {
        if (deck.size == 0 || savedQuoteId == null) return 0
        if (deck.idAt(savedIndex) == savedQuoteId) return savedIndex
        return deck.indexOfId(savedQuoteId, nearIndex = savedIndex) ?: 0
    }
}
