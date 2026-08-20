package com.hc.rzi.data.local

import com.google.common.truth.Truth.assertThat
import com.hc.rzi.domain.text.FtsQuery
import com.hc.rzi.testutil.DbFixtures
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class SearchQueryTest {

    private lateinit var db: RziDatabase

    @Before
    fun setUp() = runTest {
        db = DbFixtures.inMemoryDatabase()
        DbFixtures.insertQuote(
            db, "Solitude is a resource to be cultivated", "Deep Work",
            pageNumber = 42, tags = listOf("focus", "solitude"), createdAt = 3_000L,
        )
        DbFixtures.insertQuote(
            db, "Attention is the rarest form of generosity", "The Weil Reader",
            pageNumber = 11, tags = listOf("attention"), createdAt = 2_000L,
        )
        DbFixtures.insertQuote(
            db, "Craft beats passion every single time", "So Good They Can't Ignore You",
            pageNumber = null, tags = listOf("focus", "work"), createdAt = 1_000L,
        )
    }

    @After fun tearDown() { db.close() }

    private suspend fun search(raw: String = "", tagIds: List<Long> = emptyList()) =
        db.quoteDao().searchRows(
            hasQuery = if (FtsQuery.sanitize(raw) == null) 0 else 1,
            ftsQuery = FtsQuery.sanitize(raw).orEmpty(),
            tagIds = tagIds,
            tagCount = tagIds.size,
        )

    @Test
    fun `blank query returns everything newest first`() = runTest {
        assertThat(search().map { it.text.substringBefore(' ') })
            .containsExactly("Solitude", "Attention", "Craft").inOrder()
    }

    @Test
    fun `matches quote text`() = runTest {
        assertThat(search("generosity").map { it.bookName }).containsExactly("The Weil Reader")
    }

    @Test
    fun `matches on a word prefix`() = runTest {
        assertThat(search("solit").map { it.bookName }).containsExactly("Deep Work")
    }

    @Test
    fun `matches book name`() = runTest {
        assertThat(search("weil").map { it.pageNumber }).containsExactly(11)
    }

    @Test
    fun `matches tag name`() = runTest {
        assertThat(search("attention")).hasSize(1)
    }

    @Test
    fun `all query tokens must match`() = runTest {
        assertThat(search("solitude generosity")).isEmpty()
    }

    @Test
    fun `no match returns empty`() = runTest {
        assertThat(search("zzzznotpresent")).isEmpty()
    }

    @Test
    fun `single tag filter narrows results`() = runTest {
        val focusId = db.tagDao().findByName("focus")!!.id
        assertThat(search(tagIds = listOf(focusId))).hasSize(2)
    }

    @Test
    fun `multiple tags are ORed`() = runTest {
        val focusId = db.tagDao().findByName("focus")!!.id
        val workId = db.tagDao().findByName("work")!!.id

        val result = search(tagIds = listOf(focusId, workId))

        assertThat(result).hasSize(2)
    }

    @Test
    fun `query and tag filter combine`() = runTest {
        val focusId = db.tagDao().findByName("focus")!!.id
        assertThat(search("solitude", tagIds = listOf(focusId))).hasSize(1)
        assertThat(search("generosity", tagIds = listOf(focusId))).isEmpty()
    }

    @Test
    fun `match count matches the row count`() = runTest {
        val count = db.quoteDao().observeMatchCount(0, "", emptyList(), 0, emptyList(), 0).first()
        assertThat(count).isEqualTo(3)
    }

    @Test
    fun `book filter narrows results`() = runTest {
        val deepWorkId = db.bookDao().findByName("Deep Work")!!.id
        val count = db.quoteDao()
            .observeMatchCount(0, "", emptyList(), 0, listOf(deepWorkId), 1).first()
        assertThat(count).isEqualTo(1)
    }

    @Test
    fun `multiple books are ORed`() = runTest {
        val deepWorkId = db.bookDao().findByName("Deep Work")!!.id
        val weilId = db.bookDao().findByName("The Weil Reader")!!.id
        val count = db.quoteDao()
            .observeMatchCount(0, "", emptyList(), 0, listOf(deepWorkId, weilId), 2).first()
        assertThat(count).isEqualTo(2)
    }

    @Test
    fun `book and tag filters combine`() = runTest {
        val deepWorkId = db.bookDao().findByName("Deep Work")!!.id
        val focusId = db.tagDao().findByName("focus")!!.id
        val workId = db.tagDao().findByName("work")!!.id

        val match = db.quoteDao().observeMatchCount(
            0, "", listOf(focusId, workId), 2, listOf(deepWorkId), 1,
        ).first()
        assertThat(match).isEqualTo(1)

        val noBook = db.quoteDao().observeMatchCount(
            0, "", listOf(focusId, workId), 2, emptyList(), 0,
        ).first()
        assertThat(noBook).isEqualTo(2)
    }

    @Test
    fun `multiple library tags are ORed`() = runTest {
        val focusId = db.tagDao().findByName("focus")!!.id
        val workId = db.tagDao().findByName("work")!!.id

        val both = db.quoteDao().observeMatchCount(
            0, "", listOf(focusId, workId), 2, emptyList(), 0,
        ).first()
        assertThat(both).isEqualTo(2)

        val single = db.quoteDao().observeMatchCount(
            0, "", listOf(focusId), 1, emptyList(), 0,
        ).first()
        assertThat(single).isEqualTo(2)
    }

    @Test
    fun `query and book filter combine`() = runTest {
        val deepWorkId = db.bookDao().findByName("Deep Work")!!.id
        val weilId = db.bookDao().findByName("The Weil Reader")!!.id

        val match = db.quoteDao().observeMatchCount(
            1, "solitude", emptyList(), 0, listOf(deepWorkId), 1,
        ).first()
        assertThat(match).isEqualTo(1)

        val noMatch = db.quoteDao().observeMatchCount(
            1, "solitude", emptyList(), 0, listOf(weilId), 1,
        ).first()
        assertThat(noMatch).isEqualTo(0)
    }

    @Test
    fun `shuffle reel ids are unfiltered by default`() = runTest {
        assertThat(db.quoteDao().observeReelIdsForShuffle(emptyList(), 0, emptyList(), 0).first()).hasSize(3)
    }

    @Test
    fun `linear reel ids are ordered by book then page with unpaged last`() = runTest {
        DbFixtures.insertQuote(db, "second page of deep work", "Deep Work", pageNumber = 7)
        DbFixtures.insertQuote(db, "unpaged deep work note", "Deep Work", pageNumber = null)

        val ids = db.quoteDao().observeReelIdsForLinear(emptyList(), 0, emptyList(), 0).first()
        val rows = ids.map { db.quoteDao().rowById(it)!! }

        assertThat(rows.map { it.bookName }.distinct())
            .containsExactly("Deep Work", "So Good They Can't Ignore You", "The Weil Reader")
            .inOrder()
        val deepWork = rows.filter { it.bookName == "Deep Work" }
        assertThat(deepWork.map { it.pageNumber }).containsExactly(7, 42, null).inOrder()
    }

    @Test
    fun `reel ids honour a book filter`() = runTest {
        val bookId = db.bookDao().findByName("Deep Work")!!.id
        val ids = db.quoteDao().observeReelIdsForShuffle(listOf(bookId), 1, emptyList(), 0).first()
        assertThat(ids).hasSize(1)
    }

    @Test
    fun `reel ids honour ORed tag filters`() = runTest {
        val focusId = db.tagDao().findByName("focus")!!.id
        val workId = db.tagDao().findByName("work")!!.id
        val ids = db.quoteDao()
            .observeReelIdsForShuffle(emptyList(), 0, listOf(focusId, workId), 2)
            .first()
        assertThat(ids).hasSize(2)
    }
}
