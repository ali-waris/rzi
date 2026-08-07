package com.rzi.quotes.domain.model

enum class ReelMode { SHUFFLE, LINEAR }

data class ReelFilter(
    val bookId: Long? = null,
    val tagIds: List<Long> = emptyList(),
) {
    val isActive: Boolean get() = bookId != null || tagIds.isNotEmpty()
}

data class ReelPersistedState(
    val mode: ReelMode = ReelMode.SHUFFLE,
    val baseSeed: Long = 0L,
    val absoluteIndex: Int = 0,
    val currentQuoteId: Long? = null,
    val filter: ReelFilter = ReelFilter(),
)
