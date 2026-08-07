package com.rzi.quotes.domain.reel

import com.rzi.quotes.domain.model.ReelMode

interface Deck {

    val size: Int

    fun idAt(index: Int): Long

    fun indexOfId(id: Long, nearIndex: Int): Int?
}

object Decks {
    fun create(mode: ReelMode, ids: List<Long>, baseSeed: Long): Deck = when (mode) {
        ReelMode.LINEAR -> LinearDeck(ids)
        ReelMode.SHUFFLE -> ShuffleDeck(ids, baseSeed)
    }
}
