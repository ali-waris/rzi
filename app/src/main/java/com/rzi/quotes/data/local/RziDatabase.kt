package com.rzi.quotes.data.local

import androidx.room.Database
import androidx.room.RoomDatabase
import com.rzi.quotes.data.local.dao.BookDao
import com.rzi.quotes.data.local.dao.QuoteDao
import com.rzi.quotes.data.local.dao.QuoteFtsDao
import com.rzi.quotes.data.local.dao.TagDao
import com.rzi.quotes.data.local.entity.BookEntity
import com.rzi.quotes.data.local.entity.QuoteEntity
import com.rzi.quotes.data.local.entity.QuoteFtsEntity
import com.rzi.quotes.data.local.entity.QuoteTagCrossRef
import com.rzi.quotes.data.local.entity.TagEntity

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
