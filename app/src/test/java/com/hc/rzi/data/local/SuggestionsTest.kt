package com.hc.rzi.data.local

import com.google.common.truth.Truth.assertThat
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
class SuggestionsTest {

    private lateinit var db: RziDatabase

    @Before
    fun setUp() = runTest {
        db = DbFixtures.inMemoryDatabase()
        DbFixtures.insertQuote(db, "a", "Deep Work", tags = listOf("focus"))
        DbFixtures.insertQuote(db, "b", "Deep Work", tags = listOf("focus"))
        DbFixtures.insertQuote(db, "c", "Deep River", tags = listOf("rare"))
    }

    @After fun tearDown() { db.close() }

    @Test
    fun `book suggestions are ordered by usage then name`() = runTest {
        assertThat(db.bookDao().suggest("deep").first())
            .containsExactly("Deep Work", "Deep River").inOrder()
    }

    @Test
    fun `book suggestions match anywhere in the name`() = runTest {
        assertThat(db.bookDao().suggest("work").first()).containsExactly("Deep Work")
    }

    @Test
    fun `book suggestions are case insensitive`() = runTest {
        assertThat(db.bookDao().suggest("DEEP").first()).hasSize(2)
    }

    @Test
    fun `empty prefix returns the most used books first`() = runTest {
        assertThat(db.bookDao().suggest("").first().first()).isEqualTo("Deep Work")
    }

    @Test
    fun `tag suggestions are ordered by usage then name`() = runTest {
        assertThat(db.tagDao().suggest("").first()).containsExactly("focus", "rare").inOrder()
    }

    @Test
    fun `tag suggestions filter by substring`() = runTest {
        assertThat(db.tagDao().suggest("rar").first()).containsExactly("rare")
    }

    @Test
    fun `suggestions cap at ten results`() = runTest {
        (1..15).forEach { DbFixtures.insertQuote(db, "q$it", "Book $it") }
        assertThat(db.bookDao().suggest("").first()).hasSize(10)
    }
}
