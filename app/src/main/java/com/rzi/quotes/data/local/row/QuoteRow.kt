package com.rzi.quotes.data.local.row

data class QuoteRow(
    val id: Long,
    val text: String,
    val bookName: String,
    val pageNumber: Int?,
    val tagsCsv: String?,
    val createdAt: Long,
    val updatedAt: Long,
)
