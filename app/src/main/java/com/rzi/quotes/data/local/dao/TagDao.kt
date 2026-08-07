package com.rzi.quotes.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.rzi.quotes.data.local.entity.QuoteTagCrossRef
import com.rzi.quotes.data.local.entity.TagEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface TagDao {

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertIgnoring(tag: TagEntity): Long

    @Query("SELECT * FROM tags WHERE name = :name COLLATE NOCASE LIMIT 1")
    suspend fun findByName(name: String): TagEntity?

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun link(ref: QuoteTagCrossRef)

    @Query("DELETE FROM quote_tags WHERE quoteId = :quoteId")
    suspend fun unlinkAll(quoteId: Long)

    @Query(
        """
        SELECT t.id AS id, t.name AS name, COUNT(qt.quoteId) AS usageCount
        FROM tags t
        LEFT JOIN quote_tags qt ON qt.tagId = t.id
        GROUP BY t.id
        ORDER BY usageCount DESC, t.name COLLATE NOCASE
        """
    )
    fun observeFilters(): Flow<List<TagFilterRow>>

    @Query(
        """
        SELECT t.name FROM tags t
        LEFT JOIN quote_tags qt ON qt.tagId = t.id
        WHERE t.name LIKE '%' || :prefix || '%'
        GROUP BY t.id
        ORDER BY COUNT(qt.quoteId) DESC, t.name COLLATE NOCASE
        LIMIT 10
        """
    )
    fun suggest(prefix: String): Flow<List<String>>

    @Query("SELECT name FROM tags t JOIN quote_tags qt ON qt.tagId = t.id WHERE qt.quoteId = :quoteId ORDER BY t.name COLLATE NOCASE")
    suspend fun namesForQuote(quoteId: Long): List<String>

    @Query("DELETE FROM tags WHERE id NOT IN (SELECT DISTINCT tagId FROM quote_tags)")
    suspend fun deleteOrphans(): Int
}

data class TagFilterRow(val id: Long, val name: String, val usageCount: Int)
