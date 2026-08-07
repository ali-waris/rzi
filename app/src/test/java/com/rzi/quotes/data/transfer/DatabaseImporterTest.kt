package com.rzi.quotes.data.transfer

import android.database.sqlite.SQLiteDatabase
import com.google.common.truth.Truth.assertThat
import com.rzi.quotes.data.local.RziDatabase
import com.rzi.quotes.domain.model.ImportOutcome
import com.rzi.quotes.domain.model.TransferError
import com.rzi.quotes.domain.text.DedupeKey
import com.rzi.quotes.testutil.DbFixtures
import kotlinx.coroutines.flow.first
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
class DatabaseImporterTest {

    @get:Rule val temp = TemporaryFolder()

    private lateinit var db: RziDatabase
    private lateinit var importer: DatabaseImporter

    @Before
    fun setUp() {
        db = DbFixtures.inMemoryDatabase()
        importer = DatabaseImporter(
            db = db,
            quoteDao = db.quoteDao(),
            bookDao = db.bookDao(),
            tagDao = db.tagDao(),
            ftsDao = db.quoteFtsDao(),
            clock = Clock.fixed(Instant.ofEpochMilli(9_000L), ZoneOffset.UTC),
        )
    }

    @After fun tearDown() { db.close() }

    private fun sourceDb(
        name: String = "source.db",
        rows: List<Triple<String, String, Int?>> =
            listOf(Triple("imported text", "Imported Book", 5)),
        tags: Map<String, List<String>> = emptyMap(),
        omitColumn: Boolean = false,
    ): File {
        val file = File(temp.newFolder(name.removeSuffix(".db")), name)
        val sqlite = SQLiteDatabase.openOrCreateDatabase(file, null)
        sqlite.execSQL("CREATE TABLE books (id INTEGER PRIMARY KEY, name TEXT NOT NULL UNIQUE)")
        sqlite.execSQL("CREATE TABLE tags (id INTEGER PRIMARY KEY, name TEXT NOT NULL UNIQUE)")
        if (omitColumn) {
            sqlite.execSQL("CREATE TABLE quotes (id INTEGER PRIMARY KEY, text TEXT, bookId INTEGER)")
        } else {
            sqlite.execSQL(
                "CREATE TABLE quotes (id INTEGER PRIMARY KEY, text TEXT, bookId INTEGER, " +
                    "pageNumber INTEGER, dedupeKey TEXT, createdAt INTEGER, updatedAt INTEGER)"
            )
        }
        sqlite.execSQL("CREATE TABLE quote_tags (quoteId INTEGER, tagId INTEGER)")

        rows.forEachIndexed { index, (text, bookName, page) ->
            val quoteId = (index + 1).toLong()
            sqlite.execSQL("INSERT OR IGNORE INTO books (name) VALUES (?)", arrayOf(bookName))
            val bookId = sqlite.rawQuery("SELECT id FROM books WHERE name = ?", arrayOf(bookName))
                .use { cursor -> cursor.moveToFirst(); cursor.getLong(0) }

            if (omitColumn) {
                sqlite.execSQL(
                    "INSERT INTO quotes (id, text, bookId) VALUES (?, ?, ?)",
                    arrayOf<Any?>(quoteId, text, bookId),
                )
            } else {
                sqlite.execSQL(
                    "INSERT INTO quotes (id, text, bookId, pageNumber, dedupeKey, createdAt, updatedAt) " +
                        "VALUES (?, ?, ?, ?, ?, ?, ?)",
                    arrayOf<Any?>(quoteId, text, bookId, page, "wrong-key-$index", 1L, 1L),
                )
            }

            tags[text].orEmpty().forEach { tagName ->
                sqlite.execSQL("INSERT OR IGNORE INTO tags (name) VALUES (?)", arrayOf(tagName))
                val tagId = sqlite.rawQuery("SELECT id FROM tags WHERE name = ?", arrayOf(tagName))
                    .use { cursor -> cursor.moveToFirst(); cursor.getLong(0) }
                sqlite.execSQL(
                    "INSERT INTO quote_tags (quoteId, tagId) VALUES (?, ?)",
                    arrayOf<Any?>(quoteId, tagId),
                )
            }
        }
        sqlite.close()
        return file
    }

    @Test
    fun `imports quotes books and tags`() = runTest {
        val file = sourceDb(
            rows = listOf(Triple("imported text", "Imported Book", 5)),
            tags = mapOf("imported text" to listOf("focus", "import")),
        )

        val outcome = importer.import(file)

        assertThat(outcome).isInstanceOf(ImportOutcome.Success::class.java)
        val result = (outcome as ImportOutcome.Success).result
        assertThat(result.added).isEqualTo(1)
        assertThat(result.skippedDuplicates).isEqualTo(0)
        assertThat(result.skippedInvalid).isEqualTo(0)

        val row = db.quoteDao().rowById(1L)!!
        assertThat(row.text).isEqualTo("imported text")
        assertThat(row.bookName).isEqualTo("Imported Book")
        assertThat(row.pageNumber).isEqualTo(5)
        assertThat(row.tagsCsv!!.split(",")).containsExactly("focus", "import")
    }

    @Test
    fun `imported rows get the injected clock time not the source timestamp`() = runTest {
        importer.import(sourceDb())
        assertThat(db.quoteDao().entityById(1L)!!.createdAt).isEqualTo(9_000L)
    }

    @Test
    fun `dedupe key is recomputed locally not trusted from the source`() = runTest {
        importer.import(sourceDb())

        assertThat(db.quoteDao().entityById(1L)!!.dedupeKey)
            .isEqualTo(DedupeKey.of("imported text", "Imported Book", 5))
    }

    @Test
    fun `existing quotes are skipped as duplicates`() = runTest {
        DbFixtures.insertQuote(db, "imported text", "Imported Book", pageNumber = 5)

        val result = (importer.import(sourceDb()) as ImportOutcome.Success).result

        assertThat(result.added).isEqualTo(0)
        assertThat(result.skippedDuplicates).isEqualTo(1)
        assertThat(db.quoteDao().observeCount().first()).isEqualTo(1)
    }

    @Test
    fun `import merges rather than replacing`() = runTest {
        DbFixtures.insertQuote(db, "already here", "Local Book")

        importer.import(sourceDb())

        assertThat(db.quoteDao().observeCount().first()).isEqualTo(2)
    }

    @Test
    fun `duplicates inside the source file are collapsed`() = runTest {
        val file = sourceDb(
            rows = listOf(Triple("same text", "Same Book", 1), Triple("same text", "Same Book", 1)),
        )

        val result = (importer.import(file) as ImportOutcome.Success).result

        assertThat(result.added).isEqualTo(1)
        assertThat(result.skippedDuplicates).isEqualTo(1)
    }

    @Test
    fun `blank text and blank book name count as invalid`() = runTest {
        val file = sourceDb(
            rows = listOf(
                Triple("   ", "A Book", 1),
                Triple("valid text", "  ", 1),
                Triple("also valid", "A Book", 2),
            ),
        )

        val result = (importer.import(file) as ImportOutcome.Success).result

        assertThat(result.skippedInvalid).isEqualTo(2)
        assertThat(result.added).isEqualTo(1)
    }

    @Test
    fun `imported quotes are searchable by text and by tag`() = runTest {
        val file = sourceDb(
            rows = listOf(Triple("solitude matters here", "Imported Book", 5)),
            tags = mapOf("solitude matters here" to listOf("focus")),
        )

        importer.import(file)

        assertThat(db.quoteFtsDao().count()).isEqualTo(1)
        assertThat(db.quoteDao().searchRows(1, "solitu*", emptyList(), 0)).hasSize(1)
        assertThat(db.quoteDao().searchRows(1, "focus*", emptyList(), 0)).hasSize(1)
    }

    @Test
    fun `a file of random bytes is rejected without touching existing data`() = runTest {
        DbFixtures.insertQuote(db, "already here", "Local Book")
        val junk = temp.newFile("junk.db")
        junk.writeBytes(ByteArray(2048) { (it % 251).toByte() })

        val outcome = importer.import(junk)

        assertThat(outcome).isNotInstanceOf(ImportOutcome.Success::class.java)
        assertThat(db.quoteDao().observeCount().first()).isEqualTo(1)
    }

    @Test
    fun `a sqlite file with the wrong schema is rejected`() = runTest {
        val file = File(temp.newFolder("wrong"), "wrong.db")
        SQLiteDatabase.openOrCreateDatabase(file, null).use { sqlite ->
            sqlite.execSQL("CREATE TABLE unrelated (id INTEGER PRIMARY KEY, value TEXT)")
        }

        assertThat(importer.import(file))
            .isEqualTo(ImportOutcome.Failure(TransferError.SCHEMA_MISMATCH))
    }

    @Test
    fun `a database missing a required column is rejected`() = runTest {
        assertThat(importer.import(sourceDb(name = "missing.db", omitColumn = true)))
            .isEqualTo(ImportOutcome.Failure(TransferError.SCHEMA_MISMATCH))
    }

    @Test
    fun `an empty but well formed database reports no quotes found`() = runTest {
        assertThat(importer.import(sourceDb(name = "empty.db", rows = emptyList())))
            .isEqualTo(ImportOutcome.Failure(TransferError.NO_QUOTES_FOUND))
    }

    @Test
    fun `a missing file is reported as unreadable`() = runTest {
        assertThat(importer.import(File(temp.root, "does-not-exist.db")))
            .isEqualTo(ImportOutcome.Failure(TransferError.UNREADABLE_FILE))
    }

    @Test
    fun `extra columns in the source are tolerated`() = runTest {
        val file = File(temp.newFolder("extra"), "extra.db")
        SQLiteDatabase.openOrCreateDatabase(file, null).use { sqlite ->
            sqlite.execSQL("CREATE TABLE books (id INTEGER PRIMARY KEY, name TEXT, colour TEXT)")
            sqlite.execSQL("CREATE TABLE tags (id INTEGER PRIMARY KEY, name TEXT)")
            sqlite.execSQL(
                "CREATE TABLE quotes (id INTEGER PRIMARY KEY, text TEXT, bookId INTEGER, " +
                    "pageNumber INTEGER, favourite INTEGER)"
            )
            sqlite.execSQL("CREATE TABLE quote_tags (quoteId INTEGER, tagId INTEGER)")
            sqlite.execSQL("INSERT INTO books (id, name, colour) VALUES (1, 'Book', 'red')")
            sqlite.execSQL(
                "INSERT INTO quotes (id, text, bookId, pageNumber, favourite) " +
                    "VALUES (1, 'tolerated', 1, 3, 1)"
            )
        }

        assertThat((importer.import(file) as ImportOutcome.Success).result.added).isEqualTo(1)
    }

    @Test
    fun `a quote referencing a missing book counts as invalid`() = runTest {
        val file = File(temp.newFolder("orphan"), "orphan.db")
        SQLiteDatabase.openOrCreateDatabase(file, null).use { sqlite ->
            sqlite.execSQL("CREATE TABLE books (id INTEGER PRIMARY KEY, name TEXT)")
            sqlite.execSQL("CREATE TABLE tags (id INTEGER PRIMARY KEY, name TEXT)")
            sqlite.execSQL(
                "CREATE TABLE quotes (id INTEGER PRIMARY KEY, text TEXT, bookId INTEGER, pageNumber INTEGER)"
            )
            sqlite.execSQL("CREATE TABLE quote_tags (quoteId INTEGER, tagId INTEGER)")
            sqlite.execSQL("INSERT INTO books (id, name) VALUES (1, 'Present')")
            sqlite.execSQL("INSERT INTO quotes (id, text, bookId, pageNumber) VALUES (1, 'ok', 1, 1)")
            sqlite.execSQL("INSERT INTO quotes (id, text, bookId, pageNumber) VALUES (2, 'orphan', 99, 1)")
        }

        val result = (importer.import(file) as ImportOutcome.Success).result

        assertThat(result.added).isEqualTo(1)
        assertThat(result.skippedInvalid).isEqualTo(1)
    }

    @Test
    fun `imports from legacy references table format`() = runTest {
        val file = File(temp.newFolder("legacy"), "legacy.db")
        SQLiteDatabase.openOrCreateDatabase(file, null).use { sqlite ->
            sqlite.execSQL(
                "CREATE TABLE \"references\" (" +
                    "id INTEGER PRIMARY KEY AUTOINCREMENT, " +
                    "text TEXT NOT NULL, " +
                    "book_name TEXT NOT NULL, " +
                    "page_number INTEGER, " +
                    "tags TEXT, " +
                    "created_at TEXT DEFAULT (datetime('now')))"
            )
            sqlite.execSQL(
                "INSERT INTO \"references\" (text, book_name, page_number, tags) VALUES (?, ?, ?, ?)",
                arrayOf<Any?>("The only way to do great work is to love what you do", "Steve Jobs", 42, "motivation,work")
            )
            sqlite.execSQL(
                "INSERT INTO \"references\" (text, book_name, page_number, tags) VALUES (?, ?, ?, ?)",
                arrayOf<Any?>("Innovation distinguishes between a leader and a follower", "Steve Jobs", 100, "innovation")
            )
        }

        val outcome = importer.import(file)

        assertThat(outcome).isInstanceOf(ImportOutcome.Success::class.java)
        val result = (outcome as ImportOutcome.Success).result
        assertThat(result.added).isEqualTo(2)
        assertThat(result.skippedDuplicates).isEqualTo(0)
        assertThat(result.skippedInvalid).isEqualTo(0)

        val first = db.quoteDao().rowById(1L)!!
        assertThat(first.text).isEqualTo("The only way to do great work is to love what you do")
        assertThat(first.bookName).isEqualTo("Steve Jobs")
        assertThat(first.pageNumber).isEqualTo(42)
        assertThat(first.tagsCsv!!.split(",")).containsExactly("motivation", "work").inOrder()

        val second = db.quoteDao().rowById(2L)!!
        assertThat(second.text).isEqualTo("Innovation distinguishes between a leader and a follower")
        assertThat(second.tagsCsv).isEqualTo("innovation")
    }

    @Test
    fun `legacy format with null page number is imported correctly`() = runTest {
        val file = File(temp.newFolder("legacy_null"), "legacy_null.db")
        SQLiteDatabase.openOrCreateDatabase(file, null).use { sqlite ->
            sqlite.execSQL(
                "CREATE TABLE \"references\" (" +
                    "id INTEGER PRIMARY KEY AUTOINCREMENT, " +
                    "text TEXT NOT NULL, " +
                    "book_name TEXT NOT NULL, " +
                    "page_number INTEGER, " +
                    "tags TEXT, " +
                    "created_at TEXT)"
            )
            sqlite.execSQL(
                "INSERT INTO \"references\" (text, book_name, tags) VALUES (?, ?, ?)",
                arrayOf<Any?>("A quote without a page", "Some Book", "tag1, tag2")
            )
        }

        val outcome = importer.import(file)

        assertThat(outcome).isInstanceOf(ImportOutcome.Success::class.java)
        val row = db.quoteDao().rowById(1L)!!
        assertThat(row.text).isEqualTo("A quote without a page")
        assertThat(row.pageNumber).isNull()
        assertThat(row.tagsCsv!!.split(",")).containsExactly("tag1", "tag2").inOrder()
    }

    @Test
    fun `legacy format with duplicate quotes are collapsed`() = runTest {
        DbFixtures.insertQuote(db, "existing text", "Existing Book", pageNumber = 1)

        val file = File(temp.newFolder("legacy_dupe"), "legacy_dupe.db")
        SQLiteDatabase.openOrCreateDatabase(file, null).use { sqlite ->
            sqlite.execSQL(
                "CREATE TABLE \"references\" (" +
                    "id INTEGER PRIMARY KEY AUTOINCREMENT, " +
                    "text TEXT NOT NULL, " +
                    "book_name TEXT NOT NULL, " +
                    "page_number INTEGER, " +
                    "tags TEXT, " +
                    "created_at TEXT)"
            )
            sqlite.execSQL(
                "INSERT INTO \"references\" (text, book_name, page_number) VALUES (?, ?, ?)",
                arrayOf<Any?>("existing text", "Existing Book", 1)
            )
        }

        val result = (importer.import(file) as ImportOutcome.Success).result

        assertThat(result.added).isEqualTo(0)
        assertThat(result.skippedDuplicates).isEqualTo(1)
    }
}
