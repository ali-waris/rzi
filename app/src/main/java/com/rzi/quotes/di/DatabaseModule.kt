package com.rzi.quotes.di

import android.content.Context
import androidx.room.Room
import com.rzi.quotes.data.local.RziDatabase
import com.rzi.quotes.data.local.dao.BookDao
import com.rzi.quotes.data.local.dao.QuoteDao
import com.rzi.quotes.data.local.dao.QuoteFtsDao
import com.rzi.quotes.data.local.dao.TagDao
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object DatabaseModule {

    @Provides
    @Singleton
    fun database(@ApplicationContext context: Context): RziDatabase =
        Room.databaseBuilder(context, RziDatabase::class.java, RziDatabase.NAME).build()

    @Provides fun quoteDao(db: RziDatabase): QuoteDao = db.quoteDao()
    @Provides fun bookDao(db: RziDatabase): BookDao = db.bookDao()
    @Provides fun tagDao(db: RziDatabase): TagDao = db.tagDao()
    @Provides fun quoteFtsDao(db: RziDatabase): QuoteFtsDao = db.quoteFtsDao()
}
