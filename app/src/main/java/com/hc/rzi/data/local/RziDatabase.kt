package com.hc.rzi.data.local

import androidx.room.Database
import androidx.room.RoomDatabase
import com.hc.rzi.data.local.dao.BookDao
import com.hc.rzi.data.local.dao.QuoteDao
import com.hc.rzi.data.local.dao.QuoteFtsDao
import com.hc.rzi.data.local.dao.TagDao
import com.hc.rzi.data.local.entity.BookEntity
import com.hc.rzi.data.local.entity.QuoteEntity
import com.hc.rzi.data.local.entity.QuoteFtsEntity
import com.hc.rzi.data.local.entity.QuoteTagCrossRef
import com.hc.rzi.data.local.entity.TagEntity

@Database(
    entities = [
        QuoteEntity::class,
        BookEntity::class,
        TagEntity::class,
        QuoteTagCrossRef::class,
        QuoteFtsEntity::class,
    ],
    version = 1,
    exportSchema = true,
)
abstract class RziDatabase : RoomDatabase() {
    abstract fun quoteDao(): QuoteDao
    abstract fun bookDao(): BookDao
    abstract fun tagDao(): TagDao
    abstract fun quoteFtsDao(): QuoteFtsDao

    companion object {
        const val NAME = "rzi.db"
    }
}
