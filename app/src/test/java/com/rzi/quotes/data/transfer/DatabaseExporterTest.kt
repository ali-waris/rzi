package com.rzi.quotes.data.transfer

import android.database.sqlite.SQLiteDatabase
import androidx.test.core.app.ApplicationProvider
import com.google.common.truth.Truth.assertThat
import com.rzi.quotes.data.local.RziDatabase
import com.rzi.quotes.domain.model.ExportOutcome
import com.rzi.quotes.testutil.DbFixtures
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.io.File
import java.time.Clock
import java.time.Instant
import java.time.ZoneOffset

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class DatabaseExporterTest {

    @get:Rule val temp = TemporaryFolder()

    private lateinit var db: RziDatabase
    private lateinit var exporter: DatabaseExporter

    @Before
    fun setUp() {
        db = DbFixtures.inMemoryDatabase()
        exporter = DatabaseExporter(
            context = ApplicationProvider.getApplicationContext(),
            quoteDao = db.quoteDao(),
            clock = Clock.fixed(Instant.ofEpochMilli(9_000L), ZoneOffset.UTC),
        )
    }

    @After fun tearDown() { db.close() }

    @Test
    fun `export writes a sqlite file with the references schema`() = runTest {
        DbFixtures.insertQuote(db, "quote one", "Book A", pageNumber = 12, tags = listOf("focus"))
        DbFixtures.insertQuote(db, "quote two", "Book B")

        val target = File(temp.newFolder("out"), "out.sqlite")
        val outcome = exporter.exportToFile(target)

        assertThat(outcome).isEqualTo(ExportOutcome.Success)
        assertThat(target.exists()).isTrue()
        assertThat(target.length()).isGreaterThan(0L)

        SQLiteDatabase.openDatabase(target.absolutePath, null, SQLiteDatabase.OPEN_READONLY).use { sqlite ->
            val columns = mutableListOf<String>()
            sqlite.rawQuery("PRAGMA table_info(\"references\")", null).use { cursor ->
                val nameIndex = cursor.getColumnIndexOrThrow("name")
                while (cursor.moveToNext()) columns += cursor.getString(nameIndex)
            }
            assertThat(columns).containsExactly(
                "id", "text", "book_name", "page_number", "tags", "created_at"
            )

            val count = sqlite.rawQuery("SELECT COUNT(*) FROM \"references\"", null)
                .use { cursor -> cursor.moveToFirst(); cursor.getInt(0) }
            assertThat(count).isEqualTo(2)
        }
    }

    @Test
    fun `export writes nulls for missing page and tags`() = runTest {
        DbFixtures.insertQuote(db, "quote", "Book")

        val target = File(temp.newFolder("out"), "out.sqlite")
        val outcome = exporter.exportToFile(target)

        assertThat(outcome).isEqualTo(ExportOutcome.Success)

        SQLiteDatabase.openDatabase(target.absolutePath, null, SQLiteDatabase.OPEN_READONLY).use { sqlite ->
            sqlite.rawQuery(
                "SELECT page_number, tags FROM \"references\" LIMIT 1", null
            ).use { cursor ->
                cursor.moveToFirst()
                assertThat(cursor.isNull(0)).isTrue()
                assertThat(cursor.isNull(1)).isTrue()
            }
        }
    }

    @Test
    fun `export writes created_at as iso text`() = runTest {
        DbFixtures.insertQuote(db, "quote", "Book", createdAt = 9_000L)

        val target = File(temp.newFolder("out"), "out.sqlite")
        val outcome = exporter.exportToFile(target)

        assertThat(outcome).isEqualTo(ExportOutcome.Success)

        SQLiteDatabase.openDatabase(target.absolutePath, null, SQLiteDatabase.OPEN_READONLY).use { sqlite ->
            sqlite.rawQuery("SELECT created_at FROM \"references\" LIMIT 1", null)
                .use { cursor ->
                    cursor.moveToFirst()
                    assertThat(cursor.getString(0)).isEqualTo("1970-01-01T00:00:09")
                }
        }
    }
}
