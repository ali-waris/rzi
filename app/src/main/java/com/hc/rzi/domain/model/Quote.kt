package com.hc.rzi.domain.model

data class Quote(
    val id: Long,
    val text: String,
    val bookName: String,
    val pageNumber: Int?,
    val tags: List<String>,
    val createdAt: Long,
    val updatedAt: Long,
)

data class QuoteDraft(
    val id: Long? = null,
    val text: String,
    val bookName: String,
    val pageNumber: Int?,
    val tags: List<String>,
)

data class Book(val id: Long, val name: String)

data class TagFilter(val id: Long, val name: String, val usageCount: Int)
