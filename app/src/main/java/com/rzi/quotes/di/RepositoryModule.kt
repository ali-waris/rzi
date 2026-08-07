package com.rzi.quotes.di

import com.rzi.quotes.data.prefs.ReelStateStoreImpl
import com.rzi.quotes.data.repository.QuoteRepositoryImpl
import com.rzi.quotes.data.repository.TransferRepositoryImpl
import com.rzi.quotes.domain.repository.QuoteRepository
import com.rzi.quotes.domain.repository.ReelStateStore
import com.rzi.quotes.domain.repository.TransferRepository
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
interface RepositoryModule {

    @Binds @Singleton
    fun quoteRepository(impl: QuoteRepositoryImpl): QuoteRepository

    @Binds @Singleton
    fun reelStateStore(impl: ReelStateStoreImpl): ReelStateStore

    @Binds @Singleton
    fun transferRepository(impl: TransferRepositoryImpl): TransferRepository
}
