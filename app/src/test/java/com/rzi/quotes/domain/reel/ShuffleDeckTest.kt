package com.rzi.quotes.domain.reel

import com.google.common.truth.Truth.assertThat
import org.junit.Test

class ShuffleDeckTest {

    private val ids = (1L..20L).toList()

    @Test
    fun `every id appears exactly once within a cycle`() {
        val deck = ShuffleDeck(ids, baseSeed = 42L)
        val cycle = (0 until 20).map { deck.idAt(it) }
        assertThat(cycle).containsExactlyElementsIn(ids)
    }

    @Test
    fun `the second cycle also contains every id exactly once`() {
        val deck = ShuffleDeck(ids, baseSeed = 42L)
        val cycle = (20 until 40).map { deck.idAt(it) }
        assertThat(cycle).containsExactlyElementsIn(ids)
    }

    @Test
    fun `consecutive cycles use different orders`() {
        val deck = ShuffleDeck(ids, baseSeed = 42L)
        val first = (0 until 20).map { deck.idAt(it) }
        val second = (20 until 40).map { deck.idAt(it) }
        assertThat(second).isNotEqualTo(first)
    }

    @Test
    fun `idAt is stable when revisited`() {
        val deck = ShuffleDeck(ids, baseSeed = 42L)
        val forward = (0 until 25).map { deck.idAt(it) }
        val backward = (24 downTo 0).map { deck.idAt(it) }
        assertThat(backward.reversed()).isEqualTo(forward)
    }

    @Test
    fun `the same seed produces the same order`() {
        val a = ShuffleDeck(ids, baseSeed = 7L)
        val b = ShuffleDeck(ids, baseSeed = 7L)
        assertThat((0 until 40).map { b.idAt(it) })
            .isEqualTo((0 until 40).map { a.idAt(it) })
    }

    @Test
    fun `different seeds produce different orders`() {
        val a = ShuffleDeck(ids, baseSeed = 7L)
        val b = ShuffleDeck(ids, baseSeed = 8L)
        assertThat((0 until 20).map { b.idAt(it) })
            .isNotEqualTo((0 until 20).map { a.idAt(it) })
    }

    @Test
    fun `negative indices walk backwards into earlier cycles`() {
        val deck = ShuffleDeck(ids, baseSeed = 42L)
        val previousCycle = (-20 until 0).map { deck.idAt(it) }
        assertThat(previousCycle).containsExactlyElementsIn(ids)
    }

    @Test
    fun `single id deck returns that id at every index`() {
        val deck = ShuffleDeck(listOf(99L), baseSeed = 1L)
        assertThat(deck.idAt(0)).isEqualTo(99L)
        assertThat(deck.idAt(37)).isEqualTo(99L)
        assertThat(deck.idAt(-4)).isEqualTo(99L)
    }

    @Test
    fun `empty deck reports size zero`() {
        assertThat(ShuffleDeck(emptyList(), baseSeed = 1L).size).isEqualTo(0)
    }

    @Test
    fun `indexOfId round trips through idAt`() {
        val deck = ShuffleDeck(ids, baseSeed = 42L)
        val id = deck.idAt(25)
        val found = deck.indexOfId(id, nearIndex = 25)
        assertThat(found).isEqualTo(25)
    }

    @Test
    fun `indexOfId returns null for an unknown id`() {
        val deck = ShuffleDeck(ids, baseSeed = 42L)
        assertThat(deck.indexOfId(999L, nearIndex = 0)).isNull()
    }
}
