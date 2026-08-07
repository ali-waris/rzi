package com.rzi.quotes.data.local.entity

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "quotes",
    foreignKeys = [
        ForeignKey(
            entity = BookEntity::class,
            parentColumns = ["id"],
            childColumns = ["bookId"],
            onDelete = ForeignKey.RESTRICT,
        ),
    ],
    indices = [
        Index(value = ["bookId"]),
        Index(value = ["createdAt"]),
        Index(value = ["dedupeKey"], unique = true),
    ],
)
data class QuoteEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val text: String,
    val bookId: Long,
    val pageNumber: Int?,
    val dedupeKey: String,
    val createdAt: Long,
    val updatedAt: Long,
)
