package com.rzi.quotes.domain.reel

class LinearDeck(private val orderedIds: List<Long>) : Deck {

    override val size: Int get() = orderedIds.size

    override fun idAt(index: Int): Long {
        check(size > 0) { "Cannot read from an empty deck" }
        return orderedIds[index.mod(size)]
    }

    override fun indexOfId(id: Long, nearIndex: Int): Int? {
        if (size == 0) return null
        val position = orderedIds.indexOf(id)
        if (position < 0) return null
        val cycle = nearIndex.floorDiv(size)
        return cycle * size + position
    }
}
