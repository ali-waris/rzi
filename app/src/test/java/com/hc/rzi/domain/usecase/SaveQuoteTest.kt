package com.hc.rzi.domain.usecase

import androidx.paging.PagingData
import com.google.common.truth.Truth.assertThat
import com.hc.rzi.domain.model.Book
import com.hc.rzi.domain.model.Quote
import com.hc.rzi.domain.model.QuoteDraft
import com.hc.rzi.domain.model.ReelFilter
import com.hc.rzi.domain.model.ReelMode
import com.hc.rzi.domain.model.SaveQuoteResult
import com.hc.rzi.domain.model.TagFilter
import com.hc.rzi.domain.repository.QuoteRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest
import org.junit.Test
import java.time.Clock
import java.time.Instant
import java.time.ZoneOffset

class SaveQuoteTest {

    private class RecordingRepository(
        private val result: SaveQuoteResult = SaveQuoteResult.Saved(1L),
    ) : QuoteRepository {
        var lastDraft: QuoteDraft? = null
        var lastNow: Long? = null

        override suspend fun saveValidated(draft: QuoteDraft, nowMillis: Long): SaveQuoteResult {
            lastDraft = draft
            lastNow = nowMillis
            return result
        }

        override fun pagedQuotes(
            query: String,
            tagIds: List<Long>,
            bookIds: List<Long>,
        ): Flow<PagingData<Quote>> = flowOf(PagingData.empty())

        override fun observeMatchCount(
            query: String,
            tagIds: List<Long>,
            bookIds: List<Long>,
        ): Flow<Int> = flowOf(0)
        override fun observeReelIds(mode: ReelMode, filter: ReelFilter): Flow<List<Long>> =
            flowOf(emptyList())
        override suspend fun quoteById(id: Long): Quote? = null
        override suspend fun delete(id: Long) = Unit
        override suspend fun delete(ids: Set<Long>) = Unit
        override fun bookSuggestions(prefix: String): Flow<List<String>> = flowOf(emptyList())
        override fun allTagNames(): Flow<List<String>> = flowOf(emptyList())
        override fun observeTagFilters(): Flow<List<TagFilter>> = flowOf(emptyList())
        override fun observeBooks(): Flow<List<Book>> = flowOf(emptyList())
        override fun observeQuoteCount(): Flow<Int> = flowOf(0)
    }

    private val clock = Clock.fixed(Instant.ofEpochMilli(5_000L), ZoneOffset.UTC)

    private fun draft(
        text: String = "Some text",
        bookName: String = "A Book",
        pageNumber: Int? = 12,
        tags: List<String> = emptyList(),
    ) = QuoteDraft(text = text, bookName = bookName, pageNumber = pageNumber, tags = tags)

    @Test
    fun `valid draft is saved`() = runTest {
        val repo = RecordingRepository()
        val result = SaveQuote(repo, clock)(draft())

        assertThat(result).isInstanceOf(SaveQuoteResult.Saved::class.java)
        assertThat(repo.lastNow).isEqualTo(5_000L)
    }

    @Test
    fun `blank text is rejected`() = runTest {
        val result = SaveQuote(RecordingRepository(), clock)(draft(text = ""))
        val errors = (result as SaveQuoteResult.Invalid).errors
        assertThat(errors.text).isEqualTo("Quote text can't be empty")
    }

    @Test
    fun `whitespace only text is rejected`() = runTest {
        val result = SaveQuote(RecordingRepository(), clock)(draft(text = "   \n  "))
        assertThat((result as SaveQuoteResult.Invalid).errors.text)
            .isEqualTo("Quote text can't be empty")
    }

    @Test
    fun `blank book name is rejected`() = runTest {
        val result = SaveQuote(RecordingRepository(), clock)(draft(bookName = "  "))
        assertThat((result as SaveQuoteResult.Invalid).errors.bookName)
            .isEqualTo("Book name can't be empty")
    }

    @Test
    fun `zero and negative page numbers are rejected`() = runTest {
        listOf(0, -3).forEach { page ->
            val result = SaveQuote(RecordingRepository(), clock)(draft(pageNumber = page))
            assertThat((result as SaveQuoteResult.Invalid).errors.pageNumber)
                .isEqualTo("Page number must be 1 or higher")
        }
    }

    @Test
    fun `null page number is accepted`() = runTest {
        val result = SaveQuote(RecordingRepository(), clock)(draft(pageNumber = null))
        assertThat(result).isInstanceOf(SaveQuoteResult.Saved::class.java)
    }

    @Test
    fun `empty tag list is accepted`() = runTest {
        val result = SaveQuote(RecordingRepository(), clock)(draft(tags = emptyList()))
        assertThat(result).isInstanceOf(SaveQuoteResult.Saved::class.java)
    }

    @Test
    fun `all field errors are reported together`() = runTest {
        val result = SaveQuote(RecordingRepository(), clock)(
            draft(text = "", bookName = "", pageNumber = 0)
        )
        val errors = (result as SaveQuoteResult.Invalid).errors
        assertThat(errors.text).isNotNull()
        assertThat(errors.bookName).isNotNull()
        assertThat(errors.pageNumber).isNotNull()
    }

    @Test
    fun `text and book name are trimmed before saving`() = runTest {
        val repo = RecordingRepository()
        SaveQuote(repo, clock)(draft(text = "  padded  ", bookName = "  Book  "))

        assertThat(repo.lastDraft!!.text).isEqualTo("padded")
        assertThat(repo.lastDraft!!.bookName).isEqualTo("Book")
    }

    @Test
    fun `tags are trimmed deduplicated and stripped of commas`() = runTest {
        val repo = RecordingRepository()
        SaveQuote(repo, clock)(draft(tags = listOf(" focus ", "focus", "a,b", "")))

        assertThat(repo.lastDraft!!.tags).containsExactly("focus", "ab")
    }

    @Test
    fun `duplicate result is passed through`() = runTest {
        val repo = RecordingRepository(result = SaveQuoteResult.Duplicate)
        assertThat(SaveQuote(repo, clock)(draft())).isEqualTo(SaveQuoteResult.Duplicate)
    }
}
