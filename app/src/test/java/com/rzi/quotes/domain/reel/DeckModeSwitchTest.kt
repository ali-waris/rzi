package com.rzi.quotes.domain.reel

import com.google.common.truth.Truth.assertThat
import com.rzi.quotes.domain.model.ReelMode
import org.junit.Test

class DeckModeSwitchTest {

    private val ids = (1L..12L).toList()

    @Test
    fun `shuffle to linear keeps the current quote`() {
        val shuffle = Decks.create(ReelMode.SHUFFLE, ids, baseSeed = 3L)
        val currentIndex = 17
        val currentId = shuffle.idAt(currentIndex)

        val linear = Decks.create(ReelMode.LINEAR, ids, baseSeed = 3L)
        val newIndex = linear.indexOfId(currentId, nearIndex = currentIndex)

        assertThat(newIndex).isNotNull()
        assertThat(linear.idAt(newIndex!!)).isEqualTo(currentId)
    }

    @Test
    fun `linear to shuffle keeps the current quote`() {
        val linear = Decks.create(ReelMode.LINEAR, ids, baseSeed = 3L)
        val currentIndex = 5
        val currentId = linear.idAt(currentIndex)

        val shuffle = Decks.create(ReelMode.SHUFFLE, ids, baseSeed = 3L)
        val newIndex = shuffle.indexOfId(currentId, nearIndex = currentIndex)

        assertThat(newIndex).isNotNull()
        assertThat(shuffle.idAt(newIndex!!)).isEqualTo(currentId)
    }

    @Test
    fun `factory returns the deck matching the mode`() {
        assertThat(Decks.create(ReelMode.LINEAR, ids, 0L)).isInstanceOf(LinearDeck::class.java)
        assertThat(Decks.create(ReelMode.SHUFFLE, ids, 0L)).isInstanceOf(ShuffleDeck::class.java)
    }
}
