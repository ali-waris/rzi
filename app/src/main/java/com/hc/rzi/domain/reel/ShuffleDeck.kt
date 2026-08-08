package com.hc.rzi.domain.reel

import kotlin.random.Random

class ShuffleDeck(
    private val ids: List<Long>,
    private val baseSeed: Long,
) : Deck {

    override val size: Int get() = ids.size

    private var cachedCycle: Int? = null
    private var cachedPermutation: List<Long> = emptyList()

    override fun idAt(index: Int): Long {
        check(size > 0) { "Cannot read from an empty deck" }
        return permutationFor(index.floorDiv(size))[index.mod(size)]
    }

    override fun indexOfId(id: Long, nearIndex: Int): Int? {
        if (size == 0) return null
        val cycle = nearIndex.floorDiv(size)
        val position = permutationFor(cycle).indexOf(id)
        if (position < 0) return null
        return cycle * size + position
    }

    private fun permutationFor(cycle: Int): List<Long> {
        if (cachedCycle != cycle) {
            cachedPermutation = ids.shuffled(Random(baseSeed + cycle))
            cachedCycle = cycle
        }
        return cachedPermutation
    }
}
