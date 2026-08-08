package com.hc.rzi.data.local.dao

import androidx.paging.PagingSource
import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import com.hc.rzi.data.local.entity.QuoteEntity
import com.hc.rzi.data.local.row.QuoteRow
import kotlinx.coroutines.flow.Flow

@Dao
interface QuoteDao {

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertIgnoring(quote: QuoteEntity): Long

    @Update
    suspend fun update(quote: QuoteEntity)

    @Query("DELETE FROM quotes WHERE id = :id")
    suspend fun deleteById(id: Long)

    @Query("SELECT * FROM quotes WHERE id = :id")
    suspend fun entityById(id: Long): QuoteEntity?

    @Query("SELECT * FROM quotes WHERE dedupeKey = :dedupeKey LIMIT 1")
    suspend fun entityByDedupeKey(dedupeKey: String): QuoteEntity?

    @Query("SELECT COUNT(*) FROM quotes")
    fun observeCount(): Flow<Int>

    @Query("$ROW_SELECT WHERE q.id = :id")
    suspend fun rowById(id: Long): QuoteRow?

    @Query("$ROW_SELECT WHERE $MATCH_PREDICATE ORDER BY q.createdAt DESC, q.id DESC")
    fun pagingSource(
        hasQuery: Int,
        ftsQuery: String,
        tagIds: List<Long>,
        tagCount: Int,
    ): PagingSource<Int, QuoteRow>

    @Query("$ROW_SELECT WHERE $MATCH_PREDICATE ORDER BY q.createdAt DESC, q.id DESC")
    suspend fun searchRows(
        hasQuery: Int,
        ftsQuery: String,
        tagIds: List<Long>,
        tagCount: Int,
    ): List<QuoteRow>

    @Query(
        """
        SELECT COUNT(*) FROM quotes q JOIN books b ON b.id = q.bookId
        WHERE $MATCH_PREDICATE
        """
    )
    fun observeMatchCount(
        hasQuery: Int,
        ftsQuery: String,
        tagIds: List<Long>,
        tagCount: Int,
    ): Flow<Int>

    @Query(
        """
        SELECT q.id FROM quotes q JOIN books b ON b.id = q.bookId
        WHERE $FILTER_PREDICATE
        ORDER BY q.id
        """
    )
    fun observeReelIdsForShuffle(
        bookId: Long?,
        tagIds: List<Long>,
        tagCount: Int,
    ): Flow<List<Long>>

    @Query(
        """
        SELECT q.id FROM quotes q JOIN books b ON b.id = q.bookId
        WHERE $FILTER_PREDICATE
        ORDER BY b.name COLLATE NOCASE, (q.pageNumber IS NULL), q.pageNumber, q.id
        """
    )
    fun observeReelIdsForLinear(
        bookId: Long?,
        tagIds: List<Long>,
        tagCount: Int,
    ): Flow<List<Long>>

    companion object {
        const val ROW_SELECT = """
            SELECT q.id AS id, q.text AS text, b.name AS bookName, q.pageNumber AS pageNumber,
                   (SELECT group_concat(t.name, ',') FROM quote_tags qt
                     JOIN tags t ON t.id = qt.tagId WHERE qt.quoteId = q.id) AS tagsCsv,
                   q.createdAt AS createdAt, q.updatedAt AS updatedAt
            FROM quotes q JOIN books b ON b.id = q.bookId
        """

        const val TAG_PREDICATE = """
            (:tagCount = 0 OR (SELECT COUNT(*) FROM quote_tags qt
                                WHERE qt.quoteId = q.id AND qt.tagId IN (:tagIds)) = :tagCount)
        """

        const val MATCH_PREDICATE = """
            (:hasQuery = 0
              OR q.id IN (SELECT rowid FROM quote_fts WHERE quote_fts MATCH :ftsQuery))
            AND $TAG_PREDICATE
        """

        const val FILTER_PREDICATE = """
            (:bookId IS NULL OR q.bookId = :bookId)
            AND $TAG_PREDICATE
        """
    }
}
