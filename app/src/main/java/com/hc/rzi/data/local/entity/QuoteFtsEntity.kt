package com.hc.rzi.data.local.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.Fts4
import androidx.room.PrimaryKey

@Fts4
@Entity(tableName = "quote_fts")
data class QuoteFtsEntity(
    @PrimaryKey @ColumnInfo(name = "rowid") val rowId: Long,
    val text: String,
    val bookName: String,
    val tagsFlat: String,
)
