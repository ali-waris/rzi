package com.rzi.quotes.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.rzi.quotes.data.local.entity.QuoteFtsEntity

@Dao
interface QuoteFtsDao {

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(row: QuoteFtsEntity)

    @Query("DELETE FROM quote_fts WHERE rowid = :quoteId")
    suspend fun delete(quoteId: Long)

    @Query("SELECT COUNT(*) FROM quote_fts")
    suspend fun count(): Int
}
