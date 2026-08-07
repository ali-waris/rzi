package com.rzi.quotes.data.mapper

import com.rzi.quotes.data.local.row.QuoteRow
import com.rzi.quotes.domain.model.Quote

fun QuoteRow.toDomain(): Quote = Quote(
    id = id,
    text = text,
    bookName = bookName,
    pageNumber = pageNumber,
    tags = tagsCsv?.split(',')?.filter { it.isNotBlank() }?.sorted().orEmpty(),
    createdAt = createdAt,
    updatedAt = updatedAt,
)
