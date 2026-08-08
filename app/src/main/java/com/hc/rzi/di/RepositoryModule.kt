package com.hc.rzi.di

import com.hc.rzi.data.prefs.ReelStateStoreImpl
import com.hc.rzi.data.repository.AdminRepositoryImpl
import com.hc.rzi.data.repository.QuoteRepositoryImpl
import com.hc.rzi.data.repository.TransferRepositoryImpl
import com.hc.rzi.domain.repository.AdminRepository
import com.hc.rzi.domain.repository.QuoteRepository
import com.hc.rzi.domain.repository.ReelStateStore
import com.hc.rzi.domain.repository.TransferRepository
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

    @Binds @Singleton
    fun adminRepository(impl: AdminRepositoryImpl): AdminRepository
}
