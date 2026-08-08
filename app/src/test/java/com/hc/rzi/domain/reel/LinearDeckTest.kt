package com.hc.rzi.domain.reel

import com.google.common.truth.Truth.assertThat
import org.junit.Test

class LinearDeckTest {

    private val deck = LinearDeck(listOf(10L, 20L, 30L))

    @Test
    fun `returns ids in order`() {
        assertThat(listOf(deck.idAt(0), deck.idAt(1), deck.idAt(2)))
            .containsExactly(10L, 20L, 30L)
            .inOrder()
    }

    @Test
    fun `wraps forward past the end`() {
        assertThat(deck.idAt(3)).isEqualTo(10L)
        assertThat(deck.idAt(4)).isEqualTo(20L)
        assertThat(deck.idAt(7)).isEqualTo(20L)
    }

    @Test
    fun `wraps backward past zero`() {
        assertThat(deck.idAt(-1)).isEqualTo(30L)
        assertThat(deck.idAt(-2)).isEqualTo(20L)
        assertThat(deck.idAt(-4)).isEqualTo(30L)
    }

    @Test
    fun `size reflects the id list`() {
        assertThat(deck.size).isEqualTo(3)
    }

    @Test
    fun `single id deck returns that id at every index`() {
        val single = LinearDeck(listOf(99L))
        assertThat(single.idAt(0)).isEqualTo(99L)
        assertThat(single.idAt(5)).isEqualTo(99L)
        assertThat(single.idAt(-5)).isEqualTo(99L)
    }

    @Test
    fun `empty deck reports size zero`() {
        assertThat(LinearDeck(emptyList()).size).isEqualTo(0)
    }

    @Test
    fun `indexOfId finds an id in the same cycle as the reference index`() {
        assertThat(deck.indexOfId(30L, nearIndex = 4)).isEqualTo(5)
    }

    @Test
    fun `indexOfId returns null for an unknown id`() {
        assertThat(deck.indexOfId(999L, nearIndex = 0)).isNull()
    }
}
