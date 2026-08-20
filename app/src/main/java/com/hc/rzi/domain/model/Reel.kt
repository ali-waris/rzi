package com.hc.rzi.domain.model

enum class ReelMode { SHUFFLE, LINEAR }

data class ReelFilter(
    val bookIds: List<Long> = emptyList(),
    val tagIds: List<Long> = emptyList(),
) {
    val isActive: Boolean get() = bookIds.isNotEmpty() || tagIds.isNotEmpty()
}

data class ReelPersistedState(
    val mode: ReelMode = ReelMode.SHUFFLE,
    val baseSeed: Long = 0L,
    val absoluteIndex: Int = 0,
    val currentQuoteId: Long? = null,
    val filter: ReelFilter = ReelFilter(),
)
