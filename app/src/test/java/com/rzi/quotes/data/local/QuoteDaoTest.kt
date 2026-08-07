package com.rzi.quotes.data.local

import com.google.common.truth.Truth.assertThat
import com.rzi.quotes.data.local.entity.QuoteTagCrossRef
import com.rzi.quotes.testutil.DbFixtures
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
class QuoteDaoTest {

    private lateinit var db: RziDatabase

    @Before fun setUp() { db = DbFixtures.inMemoryDatabase() }
    @After fun tearDown() { db.close() }

    @Test
    fun `fts table is usable in this test environment`() = runTest {
        DbFixtures.insertQuote(db, "solitude is a resource", "Deep Work")
        assertThat(db.quoteFtsDao().count()).isEqualTo(1)
    }

    @Test
    fun `inserted quote reads back through the row projection`() = runTest {
        val id = DbFixtures.insertQuote(
            db, "the quote", "Deep Work", pageNumber = 42, tags = listOf("focus", "work"),
        )

        val row = db.quoteDao().rowById(id)

        assertThat(row).isNotNull()
        assertThat(row!!.text).isEqualTo("the quote")
        assertThat(row.bookName).isEqualTo("Deep Work")
        assertThat(row.pageNumber).isEqualTo(42)
        assertThat(row.tagsCsv!!.split(",")).containsExactly("focus", "work")
    }

    @Test
    fun `page number survives as null`() = runTest {
        val id = DbFixtures.insertQuote(db, "unpaged", "Deep Work", pageNumber = null)
        assertThat(db.quoteDao().rowById(id)!!.pageNumber).isNull()
    }

    @Test
    fun `quote with no tags has null tagsCsv`() = runTest {
        val id = DbFixtures.insertQuote(db, "untagged", "Deep Work")
        assertThat(db.quoteDao().rowById(id)!!.tagsCsv).isNull()
    }

    @Test
    fun `duplicate dedupeKey is ignored rather than throwing`() = runTest {
        DbFixtures.insertQuote(db, "same text", "Same Book", pageNumber = 5)

        val entity = db.quoteDao().entityById(1L)!!
        val secondInsert = db.quoteDao().insertIgnoring(entity.copy(id = 0))

        assertThat(secondInsert).isEqualTo(-1L)
        assertThat(db.quoteDao().observeCount().first()).isEqualTo(1)
    }

    @Test
    fun `deleting a quote cascades its tag links`() = runTest {
        val id = DbFixtures.insertQuote(db, "the quote", "Deep Work", tags = listOf("focus"))
        assertThat(db.tagDao().namesForQuote(id)).containsExactly("focus")

        db.quoteDao().deleteById(id)

        assertThat(db.tagDao().namesForQuote(id)).isEmpty()
    }

    @Test
    fun `orphan books and tags are removed by cleanup`() = runTest {
        val id = DbFixtures.insertQuote(db, "the quote", "Only Book", tags = listOf("lonely"))
        db.quoteDao().deleteById(id)

        assertThat(db.bookDao().deleteOrphans()).isEqualTo(1)
        assertThat(db.tagDao().deleteOrphans()).isEqualTo(1)
        assertThat(db.bookDao().observeAll().first()).isEmpty()
    }

    @Test
    fun `cleanup keeps books and tags that are still referenced`() = runTest {
        DbFixtures.insertQuote(db, "first", "Shared Book", tags = listOf("kept"))
        val second = DbFixtures.insertQuote(db, "second", "Shared Book", tags = listOf("kept"))

        db.quoteDao().deleteById(second)

        assertThat(db.bookDao().deleteOrphans()).isEqualTo(0)
        assertThat(db.tagDao().deleteOrphans()).isEqualTo(0)
    }

    @Test
    fun `book insert is case insensitively unique`() = runTest {
        DbFixtures.insertQuote(db, "first", "Deep Work")
        DbFixtures.insertQuote(db, "second", "deep work")

        assertThat(db.bookDao().observeAll().first()).hasSize(1)
    }

    @Test
    fun `linking the same tag twice is idempotent`() = runTest {
        val id = DbFixtures.insertQuote(db, "the quote", "Deep Work", tags = listOf("focus"))
        val tagId = db.tagDao().findByName("focus")!!.id

        db.tagDao().link(QuoteTagCrossRef(quoteId = id, tagId = tagId))

        assertThat(db.tagDao().namesForQuote(id)).containsExactly("focus")
    }

    @Test
    fun `tag filters carry usage counts ordered by popularity`() = runTest {
        DbFixtures.insertQuote(db, "a", "Book", tags = listOf("common", "rare"))
        DbFixtures.insertQuote(db, "b", "Book", tags = listOf("common"))

        val filters = db.tagDao().observeFilters().first()

        assertThat(filters.map { it.name }).containsExactly("common", "rare").inOrder()
        assertThat(filters.first().usageCount).isEqualTo(2)
    }
}
