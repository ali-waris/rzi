package com.hc.rzi.data.mapper

import com.hc.rzi.data.local.row.QuoteRow
import com.hc.rzi.domain.model.Quote

fun QuoteRow.toDomain(): Quote = Quote(
    id = id,
    text = text,
    bookName = bookName,
    pageNumber = pageNumber,
    tags = tagsCsv?.split(',')?.filter { it.isNotBlank() }?.sorted().orEmpty(),
    createdAt = createdAt,
    updatedAt = updatedAt,
)
