package com.rzi.quotes.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.rzi.quotes.data.local.entity.BookEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface BookDao {

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertIgnoring(book: BookEntity): Long

    @Query("SELECT * FROM books WHERE name = :name COLLATE NOCASE LIMIT 1")
    suspend fun findByName(name: String): BookEntity?

    @Query("SELECT id, name FROM books ORDER BY name COLLATE NOCASE")
    fun observeAll(): Flow<List<BookEntity>>

    @Query(
        """
        SELECT b.name FROM books b
        LEFT JOIN quotes q ON q.bookId = b.id
        WHERE b.name LIKE '%' || :prefix || '%'
        GROUP BY b.id
        ORDER BY COUNT(q.id) DESC, b.name COLLATE NOCASE
        LIMIT 10
        """
    )
    fun suggest(prefix: String): Flow<List<String>>

    @Query("DELETE FROM books WHERE id NOT IN (SELECT DISTINCT bookId FROM quotes)")
    suspend fun deleteOrphans(): Int
}
