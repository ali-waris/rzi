package com.hc.rzi.ui.navigation

import kotlinx.serialization.Serializable

sealed interface Destination {
    @Serializable data object Reel : Destination
    @Serializable data object Library : Destination
    @Serializable data class QuoteDetail(val quoteId: Long?) : Destination
}
