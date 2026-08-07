package com.rzi.quotes.data.repository

import com.google.common.truth.Truth.assertThat
import com.rzi.quotes.data.local.RziDatabase
import com.rzi.quotes.domain.model.QuoteDraft
import com.rzi.quotes.domain.model.ReelFilter
import com.rzi.quotes.domain.model.ReelMode
import com.rzi.quotes.domain.model.SaveQuoteResult
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
class QuoteRepositoryImplTest {

    private lateinit var db: RziDatabase
    private lateinit var repository: QuoteRepositoryImpl

    @Before
    fun setUp() {
        db = DbFixtures.inMemoryDatabase()
        repository = QuoteRepositoryImpl(
            db = db,
            quoteDao = db.quoteDao(),
            bookDao = db.bookDao(),
            tagDao = db.tagDao(),
            ftsDao = db.quoteFtsDao(),
        )
    }

    @After fun tearDown() { db.close() }

    private fun draft(
        id: Long? = null,
        text: String = "Some text",
        bookName: String = "A Book",
        pageNumber: Int? = 12,
        tags: List<String> = listOf("focus"),
    ) = QuoteDraft(id, text, bookName, pageNumber, tags)

    @Test
    fun `saving creates the quote its book and its tags`() = runTest {
        val result = repository.saveValidated(draft(), nowMillis = 100L)

        val id = (result as SaveQuoteResult.Saved).id
        val quote = repository.quoteById(id)!!
        assertThat(quote.text).isEqualTo("Some text")
        assertThat(quote.bookName).isEqualTo("A Book")
        assertThat(quote.pageNumber).isEqualTo(12)
        assertThat(quote.tags).containsExactly("focus")
        assertThat(quote.createdAt).isEqualTo(100L)
    }

    @Test
    fun `saving reuses an existing book case insensitively`() = runTest {
        repository.saveValidated(draft(text = "first", bookName = "Deep Work"), 100L)
        repository.saveValidated(draft(text = "second", bookName = "deep work"), 200L)

        assertThat(repository.observeBooks().first()).hasSize(1)
    }

    @Test
    fun `saving a duplicate returns Duplicate and does not insert`() = runTest {
        repository.saveValidated(draft(), 100L)
        val second = repository.saveValidated(draft(), 200L)

        assertThat(second).isEqualTo(SaveQuoteResult.Duplicate)
        assertThat(repository.observeQuoteCount().first()).isEqualTo(1)
    }

    @Test
    fun `saving writes a searchable fts row`() = runTest {
        val id = (repository.saveValidated(
            draft(text = "solitude matters", bookName = "Deep Work", tags = listOf("focus")),
            100L,
        ) as SaveQuoteResult.Saved).id

        assertThat(repository.observeMatchCount("solitu", emptyList()).first()).isEqualTo(1)
        assertThat(repository.observeMatchCount("focus", emptyList()).first()).isEqualTo(1)
        assertThat(repository.quoteById(id)).isNotNull()
    }

    @Test
    fun `editing updates text tags and the fts row`() = runTest {
        val id = (repository.saveValidated(
            draft(text = "old text", tags = listOf("old")), 100L,
        ) as SaveQuoteResult.Saved).id

        repository.saveValidated(
            draft(id = id, text = "new text", tags = listOf("new")), 200L,
        )

        val quote = repository.quoteById(id)!!
        assertThat(quote.text).isEqualTo("new text")
        assertThat(quote.tags).containsExactly("new")
        assertThat(quote.updatedAt).isEqualTo(200L)
        assertThat(repository.observeMatchCount("new", emptyList()).first()).isEqualTo(1)
        assertThat(repository.observeMatchCount("old", emptyList()).first()).isEqualTo(0)
    }

    @Test
    fun `editing into an existing dedupe key returns Duplicate`() = runTest {
        repository.saveValidated(draft(text = "first", pageNumber = 1), 100L)
        val secondId = (repository.saveValidated(
            draft(text = "second", pageNumber = 2), 200L,
        ) as SaveQuoteResult.Saved).id

        val result = repository.saveValidated(
            draft(id = secondId, text = "first", pageNumber = 1), 300L,
        )

        assertThat(result).isEqualTo(SaveQuoteResult.Duplicate)
        assertThat(repository.quoteById(secondId)!!.text).isEqualTo("second")
    }

    @Test
    fun `editing a quote to keep its own dedupe key succeeds`() = runTest {
        val id = (repository.saveValidated(draft(tags = listOf("a")), 100L)
            as SaveQuoteResult.Saved).id

        val result = repository.saveValidated(draft(id = id, tags = listOf("b")), 200L)

        assertThat(result).isInstanceOf(SaveQuoteResult.Saved::class.java)
        assertThat(repository.quoteById(id)!!.tags).containsExactly("b")
    }

    @Test
    fun `deleting removes the quote its fts row and orphan book and tag`() = runTest {
        val id = (repository.saveValidated(draft(), 100L) as SaveQuoteResult.Saved).id

        repository.delete(id)

        assertThat(repository.quoteById(id)).isNull()
        assertThat(db.quoteFtsDao().count()).isEqualTo(0)
        assertThat(repository.observeBooks().first()).isEmpty()
        assertThat(repository.observeTagFilters().first()).isEmpty()
    }

    @Test
    fun `deleting keeps a book that still has quotes`() = runTest {
        repository.saveValidated(draft(text = "keeper", bookName = "Shared"), 100L)
        val id = (repository.saveValidated(draft(text = "goner", bookName = "Shared"), 200L)
            as SaveQuoteResult.Saved).id

        repository.delete(id)

        assertThat(repository.observeBooks().first()).hasSize(1)
    }

    @Test
    fun `reel ids follow the requested mode`() = runTest {
        repository.saveValidated(draft(text = "b quote", bookName = "B Book", pageNumber = 1), 100L)
        repository.saveValidated(draft(text = "a quote", bookName = "A Book", pageNumber = 1), 200L)

        val linear = repository.observeReelIds(ReelMode.LINEAR, ReelFilter()).first()
        val firstBook = repository.quoteById(linear.first())!!.bookName

        assertThat(firstBook).isEqualTo("A Book")
        assertThat(repository.observeReelIds(ReelMode.SHUFFLE, ReelFilter()).first()).hasSize(2)
    }

    @Test
    fun `reel ids honour a tag filter`() = runTest {
        repository.saveValidated(draft(text = "tagged", tags = listOf("keep")), 100L)
        repository.saveValidated(draft(text = "other", tags = listOf("skip")), 200L)
        val keepId = repository.observeTagFilters().first().first { it.name == "keep" }.id

        val ids = repository.observeReelIds(
            ReelMode.SHUFFLE, ReelFilter(tagIds = listOf(keepId)),
        ).first()

        assertThat(ids).hasSize(1)
    }

    @Test
    fun `quote with no tags round trips as an empty list`() = runTest {
        val id = (repository.saveValidated(draft(tags = emptyList()), 100L)
            as SaveQuoteResult.Saved).id
        assertThat(repository.quoteById(id)!!.tags).isEmpty()
    }
}
