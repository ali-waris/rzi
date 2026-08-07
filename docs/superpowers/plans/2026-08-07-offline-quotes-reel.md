# Rzi Offline Quote Reel Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Build a fully offline Android app with an endless vertical quote reel (shuffle or linear order) and a searchable library that supports full CRUD plus non-destructive SQLite database import and export.

**Architecture:** Single Gradle module, package `com.rzi.quotes`, with one-way layered dependencies `ui → domain ← data`. `domain` holds pure Kotlin models, ports, and the reel/dedupe/search-query logic. `data` owns Room (including an FTS4 index maintained in-transaction), DataStore, and raw-SQLite import/export. `ui` is Compose with one immutable `UiState` per screen plus a `Channel` for one-shot events.

**Tech Stack:** Kotlin, Jetpack Compose, Material 3, Hilt, Room + KSP, Paging 3, DataStore Preferences, Navigation Compose, JUnit 4 + Truth + Robolectric + coroutines-test.

**Source spec:** [`docs/superpowers/specs/2026-08-07-offline-quotes-reel-design.md`](../specs/2026-08-07-offline-quotes-reel-design.md)

## Global Constraints

Every task's requirements implicitly include this section.

- **Package:** `com.rzi.quotes`. **App name:** `Rzi`. **Database file name:** `rzi.db`.
- **`compileSdk` 36, `targetSdk` 36, `minSdk` 26.** If the scaffold template generates different values, change them to these.
- **No `INTERNET` permission.** `AndroidManifest.xml` must never declare `android.permission.INTERNET` or any other network permission. Task 14 verifies this against the *merged* manifest.
- **`domain` purity:** no file under `domain/` may import `android.*`, `androidx.room.*`, `androidx.compose.*`, or `androidx.datastore.*`. The single permitted androidx import is `androidx.paging.PagingData`/`PagingSource` from `paging-common`, which is a pure-JVM artifact. Task 14 verifies this with a grep.
- **Stable Material 3 only.** Use `androidx.compose.material3:material3` as resolved by the Compose BOM. Do **not** add alpha-only material3 artifacts. Expressive character comes from motion specs, tonal color, and shape — not from alpha-only components. This is why the reel's mode control is a stable `IconToggleButton` rather than the alpha `ToggleButton`.
- **Tag names may not contain a comma.** The editor commits a tag on comma, and `group_concat(name, ',')` is used to read tags back, so a comma inside a tag name would corrupt the round trip. `SaveQuote` strips commas from tag names.
- **Times are `Long` epoch milliseconds**, always obtained from an injected `java.time.Clock` (available on API 26), never `System.currentTimeMillis()` at a call site. This keeps `createdAt`, `updatedAt`, and the export filename testable.
- **All disk work on `Dispatchers.IO`**, injected via a qualifier so tests can substitute a test dispatcher.
- **Error messages are exact copy.** Where this plan quotes a user-facing string, use it verbatim:
  - `"Quote text can't be empty"`
  - `"Book name can't be empty"`
  - `"Page number must be 1 or higher"`
  - `"This quote already exists"`
  - `"No quotes yet"`
  - `"Nothing here yet"`
  - `"Couldn't read that file"`
  - `"That file isn't a SQLite database"`
  - `"That database has a different structure"`
  - `"No quotes found in that file"`
  - `"Couldn't save to the database"`
  - `"Couldn't write the export file"`

---

## File Structure

Files are grouped by responsibility, one clear job each. Paths are relative to `app/src/main/java/com/rzi/quotes/` unless stated otherwise.

**Build & manifest**
| File | Responsibility |
|---|---|
| `gradle/libs.versions.toml` | Version catalog — every dependency version lives here, nowhere else. |
| `app/build.gradle.kts` | Module config, SDK levels, KSP + Hilt plugins, Room schema export, Robolectric test options. |
| `app/src/main/AndroidManifest.xml` | Single activity, `Rzi` app class. No permissions at all. |

**domain — pure Kotlin**
| File | Responsibility |
|---|---|
| `domain/model/Quote.kt` | `Quote`, `QuoteDraft`, `Book`, `TagFilter`. |
| `domain/model/Reel.kt` | `ReelMode`, `ReelFilter`, `ReelPersistedState`. |
| `domain/model/Results.kt` | `ValidationErrors`, `SaveQuoteResult`, `ImportResult`, `TransferError`, `ImportOutcome`, `ExportOutcome`. |
| `domain/text/DedupeKey.kt` | Canonical duplicate identity for a quote. |
| `domain/text/FtsQuery.kt` | Raw user input → safe FTS4 MATCH expression. |
| `domain/reel/Deck.kt` | `Deck` interface + `Decks.create` factory. |
| `domain/reel/LinearDeck.kt` | Wrapping sequential deck. |
| `domain/reel/ShuffleDeck.kt` | Cycle-seeded permutation deck. |
| `domain/repository/QuoteRepository.kt` | Port for all quote reads/writes. |
| `domain/repository/TransferRepository.kt` | Port for import/export (takes URI as `String`, keeping `android.net.Uri` out of domain). |
| `domain/repository/ReelStateStore.kt` | Port for persisted reel position. |
| `domain/usecase/*.kt` | One file per use case; only `SaveQuote`, `ImportDatabase`, `ExportDatabase`, and `ObserveReelDeck` contain logic, the rest are thin. |

**data**
| File | Responsibility |
|---|---|
| `data/local/entity/QuoteEntity.kt`, `BookEntity.kt`, `TagEntity.kt`, `QuoteTagCrossRef.kt`, `QuoteFtsEntity.kt` | Room entities, one per file. |
| `data/local/row/QuoteRow.kt` | Flat projection returned by JOIN queries — no `@Relation`, so it works cleanly with Paging. |
| `data/local/dao/QuoteDao.kt` | Quote reads (paging, reel ids, by id) and raw writes. |
| `data/local/dao/BookDao.kt` | Book upsert, suggestions, orphan cleanup. |
| `data/local/dao/TagDao.kt` | Tag upsert, links, filters with usage counts, suggestions, orphan cleanup. |
| `data/local/dao/QuoteFtsDao.kt` | FTS row insert/delete. |
| `data/local/RziDatabase.kt` | Room database declaration, version 1. |
| `data/repository/QuoteRepositoryImpl.kt` | Composes DAOs inside `withTransaction`; owns the write orchestration. |
| `data/repository/TransferRepositoryImpl.kt` | Delegates to importer/exporter, maps exceptions to `TransferError`. |
| `data/transfer/DatabaseImporter.kt` | Reads a foreign `.db` with raw `SQLiteDatabase`, validates, merges. |
| `data/transfer/DatabaseExporter.kt` | Checkpoints WAL and streams the db file to a URI. |
| `data/prefs/ReelStateStoreImpl.kt` | DataStore-backed reel position. |
| `data/mapper/QuoteMappers.kt` | `QuoteRow` ↔ `Quote`. |

**ui**
| File | Responsibility |
|---|---|
| `ui/theme/Color.kt`, `Type.kt`, `Theme.kt` | Seed-derived scheme, Literata for quotes, dynamic color on 31+. |
| `ui/navigation/Destination.kt`, `RziNavHost.kt` | Two type-safe routes + the `NavigationBar` shell. |
| `ui/components/EmptyState.kt`, `TagChipRow.kt` | Shared, dumb composables. |
| `ui/reel/ReelUiState.kt`, `ReelViewModel.kt`, `ReelScreen.kt`, `ReelPage.kt`, `ReelFilterSheet.kt` | Reel; `ReelPage` is the single-quote page so `ReelScreen` stays about paging. |
| `ui/library/LibraryUiState.kt`, `LibraryViewModel.kt`, `LibraryScreen.kt`, `QuoteRowItem.kt` | Library list, search, filter chips, swipe-delete. |
| `ui/library/editor/QuoteEditorUiState.kt`, `QuoteEditorViewModel.kt`, `QuoteEditorSheet.kt` | Add/edit sheet and its validation state. |
| `di/DatabaseModule.kt`, `RepositoryModule.kt`, `AppModule.kt` | Hilt wiring. |

**tests** (`app/src/test/java/com/rzi/quotes/`)
| File | Responsibility |
|---|---|
| `domain/text/DedupeKeyTest.kt`, `FtsQueryTest.kt` | Pure JUnit. |
| `domain/reel/LinearDeckTest.kt`, `ShuffleDeckTest.kt`, `DeckModeSwitchTest.kt` | Pure JUnit. |
| `domain/usecase/SaveQuoteTest.kt` | Validation, with a fake repository. |
| `data/local/QuoteDaoTest.kt`, `SearchQueryTest.kt`, `SuggestionsTest.kt` | Robolectric + in-memory Room. |
| `data/repository/QuoteRepositoryImplTest.kt` | Write orchestration, cascade, orphan cleanup, FTS sync. |
| `data/transfer/DatabaseImporterTest.kt`, `TransferRoundTripTest.kt` | Robolectric, fixture `.db` files in a temp dir. |
| `testutil/DbFixtures.kt`, `TestClock.kt` | Shared test helpers. |

---

## Task 1: Project scaffold that builds and runs one test

**Files:**
- Create: whole project skeleton via the `android` CLI
- Modify: `gradle/libs.versions.toml`, `app/build.gradle.kts`, `app/src/main/AndroidManifest.xml`
- Test: `app/src/test/java/com/rzi/quotes/ScaffoldSanityTest.kt`

**Interfaces:**
- Consumes: nothing.
- Produces: a buildable module named `app`, package `com.rzi.quotes`, with `libs.versions.toml` aliases every later task references: `androidx-core-ktx`, `androidx-activity-compose`, `androidx-lifecycle-viewmodel-compose`, `compose-bom`, `compose-ui`, `compose-ui-tooling-preview`, `compose-material3`, `compose-material-icons-extended`, `navigation-compose`, `room-runtime`, `room-ktx`, `room-paging`, `room-compiler`, `paging-runtime`, `paging-common`, `hilt-android`, `hilt-compiler`, `hilt-navigation-compose`, `datastore-preferences`, `junit`, `truth`, `robolectric`, `coroutines-test`, `androidx-test-core`.

- [ ] **Step 1: Scaffold the project**

Run from `/Users/hussain.chachuliya/Documents/rzi`:

```bash
android create project --name Rzi --package com.rzi.quotes --type compose --path .
```

If that exact invocation is rejected, run `android create project --help` and use the flags it lists to create a Compose app with package `com.rzi.quotes` in the current directory. Do not hand-roll the Gradle wrapper — the CLI's wrapper and template versions are the point of this step.

- [ ] **Step 2: Record what the template actually generated**

Read `gradle/libs.versions.toml` and `app/build.gradle.kts` and note the generated AGP, Kotlin, and `compileSdk` values. Later steps *add* to this catalog rather than replacing it, so the template's own AGP/Kotlin pairing stays intact.

- [ ] **Step 3: Force the SDK levels from Global Constraints**

In `app/build.gradle.kts`, set `compileSdk = 36`, `defaultConfig { minSdk = 26; targetSdk = 36 }`. Leave everything else the template generated.

- [ ] **Step 4: Add dependency versions to the catalog**

Append to `gradle/libs.versions.toml` (keep the template's existing `[versions]` entries for AGP and Kotlin):

```toml
[versions]
ksp = "2.1.0-1.0.29"
room = "2.6.1"
hilt = "2.53"
paging = "3.3.5"
datastore = "1.1.1"
navigationCompose = "2.8.5"
hiltNavigationCompose = "1.2.0"
truth = "1.4.4"
robolectric = "4.14.1"
coroutinesTest = "1.9.0"
androidxTestCore = "1.6.1"

[libraries]
navigation-compose = { module = "androidx.navigation:navigation-compose", version.ref = "navigationCompose" }
compose-material-icons-extended = { module = "androidx.compose.material:material-icons-extended" }
room-runtime = { module = "androidx.room:room-runtime", version.ref = "room" }
room-ktx = { module = "androidx.room:room-ktx", version.ref = "room" }
room-paging = { module = "androidx.room:room-paging", version.ref = "room" }
room-compiler = { module = "androidx.room:room-compiler", version.ref = "room" }
paging-runtime = { module = "androidx.paging:paging-runtime", version.ref = "paging" }
paging-common = { module = "androidx.paging:paging-common", version.ref = "paging" }
hilt-android = { module = "com.google.dagger:hilt-android", version.ref = "hilt" }
hilt-compiler = { module = "com.google.dagger:hilt-android-compiler", version.ref = "hilt" }
hilt-navigation-compose = { module = "androidx.hilt:hilt-navigation-compose", version.ref = "hiltNavigationCompose" }
datastore-preferences = { module = "androidx.datastore:datastore-preferences", version.ref = "datastore" }
truth = { module = "com.google.truth:truth", version.ref = "truth" }
robolectric = { module = "org.robolectric:robolectric", version.ref = "robolectric" }
coroutines-test = { module = "org.jetbrains.kotlinx:kotlinx-coroutines-test", version.ref = "coroutinesTest" }
androidx-test-core = { module = "androidx.test:core-ktx", version.ref = "androidxTestCore" }

[plugins]
ksp = { id = "com.google.devtools.ksp", version.ref = "ksp" }
hilt = { id = "com.google.dagger.hilt.android", version.ref = "hilt" }
```

`compose-material-icons-extended` deliberately has no version — it resolves through the Compose BOM the template already declares.

- [ ] **Step 5: Wire plugins and dependencies into the module**

In the root `build.gradle.kts` `plugins` block add `alias(libs.plugins.ksp) apply false` and `alias(libs.plugins.hilt) apply false`. In `app/build.gradle.kts` add `alias(libs.plugins.ksp)` and `alias(libs.plugins.hilt)`, then:

```kotlin
android {
    // ...existing template config...
    testOptions {
        unitTests {
            isIncludeAndroidResources = true
            isReturnDefaultValues = true
        }
    }
}

ksp {
    arg("room.schemaLocation", "$projectDir/schemas")
    arg("room.generateKotlin", "true")
}

dependencies {
    // ...existing template dependencies...
    implementation(libs.navigation.compose)
    implementation(libs.compose.material.icons.extended)
    implementation(libs.room.runtime)
    implementation(libs.room.ktx)
    implementation(libs.room.paging)
    ksp(libs.room.compiler)
    implementation(libs.paging.runtime)
    implementation(libs.paging.common)
    implementation(libs.hilt.android)
    ksp(libs.hilt.compiler)
    implementation(libs.hilt.navigation.compose)
    implementation(libs.datastore.preferences)

    testImplementation(libs.junit)
    testImplementation(libs.truth)
    testImplementation(libs.robolectric)
    testImplementation(libs.coroutines.test)
    testImplementation(libs.androidx.test.core)
    testImplementation(libs.room.testing)
}
```

Add `room-testing = { module = "androidx.room:room-testing", version.ref = "room" }` to `[libraries]` for that last line.

- [ ] **Step 6: Strip all permissions from the manifest**

Open `app/src/main/AndroidManifest.xml` and delete every `<uses-permission>` element. The file should declare only `<application>` and the single launcher activity. Set `android:label="Rzi"`.

- [ ] **Step 7: Write the scaffold sanity test**

Create `app/src/test/java/com/rzi/quotes/ScaffoldSanityTest.kt`:

```kotlin
package com.rzi.quotes

import com.google.common.truth.Truth.assertThat
import org.junit.Test

class ScaffoldSanityTest {
    @Test
    fun `test infrastructure runs`() {
        assertThat(2 + 2).isEqualTo(4)
    }
}
```

- [ ] **Step 8: Verify dependency resolution and the test run**

Run:

```bash
./gradlew :app:dependencies --configuration debugRuntimeClasspath > /dev/null && ./gradlew :app:testDebugUnitTest --tests '*ScaffoldSanityTest*'
```

Expected: both succeed. If a version in Step 4 fails to resolve, the error names the artifact — replace that one version with the newest available on `maven.google.com` (or Maven Central for Truth/Robolectric/coroutines-test) and re-run. Do not proceed past this step with an unresolved dependency.

- [ ] **Step 9: Verify the app assembles**

Run:

```bash
./gradlew :app:assembleDebug
```

Expected: `BUILD SUCCESSFUL`.

- [ ] **Step 10: Checkpoint**

Run `./gradlew :app:testDebugUnitTest`. All green. Do not commit (see Global Constraints).

---

## Task 2: DedupeKey and FtsQuery (pure domain)

**Files:**
- Create: `app/src/main/java/com/rzi/quotes/domain/text/DedupeKey.kt`
- Create: `app/src/main/java/com/rzi/quotes/domain/text/FtsQuery.kt`
- Test: `app/src/test/java/com/rzi/quotes/domain/text/DedupeKeyTest.kt`
- Test: `app/src/test/java/com/rzi/quotes/domain/text/FtsQueryTest.kt`

**Interfaces:**
- Consumes: nothing.
- Produces:
  - `DedupeKey.of(text: String, bookName: String, pageNumber: Int?): String` — SHA-256 hex.
  - `DedupeKey.normalize(value: String): String` — trim, collapse internal whitespace, lowercase.
  - `FtsQuery.sanitize(raw: String): String?` — MATCH expression, or `null` when the input has no usable tokens.

- [ ] **Step 1: Write the failing DedupeKey tests**

Create `app/src/test/java/com/rzi/quotes/domain/text/DedupeKeyTest.kt`:

```kotlin
package com.rzi.quotes.domain.text

import com.google.common.truth.Truth.assertThat
import org.junit.Test

class DedupeKeyTest {

    @Test
    fun `identical input produces identical key`() {
        assertThat(DedupeKey.of("Some text", "A Book", 12))
            .isEqualTo(DedupeKey.of("Some text", "A Book", 12))
    }

    @Test
    fun `key ignores surrounding whitespace`() {
        assertThat(DedupeKey.of("  Some text  ", "A Book", 12))
            .isEqualTo(DedupeKey.of("Some text", "A Book", 12))
    }

    @Test
    fun `key ignores internal whitespace runs and newlines`() {
        assertThat(DedupeKey.of("Some\n\n  text", "A Book", 12))
            .isEqualTo(DedupeKey.of("Some text", "A Book", 12))
    }

    @Test
    fun `key ignores case in text and book name`() {
        assertThat(DedupeKey.of("SOME TEXT", "a book", 12))
            .isEqualTo(DedupeKey.of("some text", "A BOOK", 12))
    }

    @Test
    fun `different page numbers produce different keys`() {
        assertThat(DedupeKey.of("Some text", "A Book", 12))
            .isNotEqualTo(DedupeKey.of("Some text", "A Book", 13))
    }

    @Test
    fun `null page differs from page zero`() {
        assertThat(DedupeKey.of("Some text", "A Book", null))
            .isNotEqualTo(DedupeKey.of("Some text", "A Book", 0))
    }

    @Test
    fun `different books produce different keys`() {
        assertThat(DedupeKey.of("Some text", "A Book", 12))
            .isNotEqualTo(DedupeKey.of("Some text", "Another Book", 12))
    }

    @Test
    fun `key is a 64 character hex string`() {
        val key = DedupeKey.of("Some text", "A Book", 12)
        assertThat(key).hasLength(64)
        assertThat(key).matches("[0-9a-f]{64}")
    }
}
```

- [ ] **Step 2: Run to verify it fails**

Run: `./gradlew :app:testDebugUnitTest --tests '*DedupeKeyTest*'`
Expected: FAIL — unresolved reference `DedupeKey`.

- [ ] **Step 3: Implement DedupeKey**

Create `app/src/main/java/com/rzi/quotes/domain/text/DedupeKey.kt`:

```kotlin
package com.rzi.quotes.domain.text

import java.security.MessageDigest

/**
 * Canonical identity of a quote, used by the UNIQUE index on `quotes.dedupeKey` so that duplicate
 * rejection is enforced by the database rather than by application checks.
 */
object DedupeKey {

    private val WHITESPACE = Regex("\\s+")
    private const val SEPARATOR = " "

    fun normalize(value: String): String =
        value.trim().replace(WHITESPACE, " ").lowercase()

    fun of(text: String, bookName: String, pageNumber: Int?): String {
        val canonical = buildString {
            append(normalize(text))
            append(SEPARATOR)
            append(normalize(bookName))
            append(SEPARATOR)
            append(pageNumber?.toString().orEmpty())
        }
        val digest = MessageDigest.getInstance("SHA-256").digest(canonical.toByteArray())
        return digest.joinToString("") { byte -> "%02x".format(byte) }
    }
}
```

- [ ] **Step 4: Run to verify DedupeKey passes**

Run: `./gradlew :app:testDebugUnitTest --tests '*DedupeKeyTest*'`
Expected: PASS, 8 tests.

- [ ] **Step 5: Write the failing FtsQuery tests**

Create `app/src/test/java/com/rzi/quotes/domain/text/FtsQueryTest.kt`:

```kotlin
package com.rzi.quotes.domain.text

import com.google.common.truth.Truth.assertThat
import org.junit.Test

class FtsQueryTest {

    @Test
    fun `single word becomes a prefix token`() {
        assertThat(FtsQuery.sanitize("sol")).isEqualTo("sol*")
    }

    @Test
    fun `multiple words each become prefix tokens`() {
        assertThat(FtsQuery.sanitize("deep work")).isEqualTo("deep* work*")
    }

    @Test
    fun `punctuation is stripped rather than escaped`() {
        assertThat(FtsQuery.sanitize("don't stop-now!")).isEqualTo("don* t* stop* now*")
    }

    @Test
    fun `quotes cannot break out of the match expression`() {
        assertThat(FtsQuery.sanitize("\" OR 1=1 --")).isEqualTo("OR* 1* 1*")
    }

    @Test
    fun `whitespace runs collapse`() {
        assertThat(FtsQuery.sanitize("  deep    work  ")).isEqualTo("deep* work*")
    }

    @Test
    fun `blank input yields null`() {
        assertThat(FtsQuery.sanitize("")).isNull()
        assertThat(FtsQuery.sanitize("   ")).isNull()
    }

    @Test
    fun `punctuation only input yields null`() {
        assertThat(FtsQuery.sanitize("!!! ??? ---")).isNull()
    }

    @Test
    fun `digits and letters survive`() {
        assertThat(FtsQuery.sanitize("chapter 12")).isEqualTo("chapter* 12*")
    }
}
```

- [ ] **Step 6: Run to verify it fails**

Run: `./gradlew :app:testDebugUnitTest --tests '*FtsQueryTest*'`
Expected: FAIL — unresolved reference `FtsQuery`.

- [ ] **Step 7: Implement FtsQuery**

Create `app/src/main/java/com/rzi/quotes/domain/text/FtsQuery.kt`:

```kotlin
package com.rzi.quotes.domain.text

/**
 * Turns raw user input into an FTS4 MATCH expression.
 *
 * Everything outside letters, digits, and whitespace is dropped rather than escaped, because FTS4
 * MATCH syntax treats several punctuation characters as operators and an unbalanced quote is a hard
 * SQL error. Each surviving token gets a `*` so typing "sol" matches "solitude".
 */
object FtsQuery {

    private val DISALLOWED = Regex("[^\\p{L}\\p{Nd}]+")

    fun sanitize(raw: String): String? {
        val tokens = raw
            .replace(DISALLOWED, " ")
            .trim()
            .split(' ')
            .filter { it.isNotEmpty() }
        if (tokens.isEmpty()) return null
        return tokens.joinToString(" ") { "$it*" }
    }
}
```

- [ ] **Step 8: Run to verify FtsQuery passes**

Run: `./gradlew :app:testDebugUnitTest --tests '*FtsQueryTest*'`
Expected: PASS, 8 tests.

- [ ] **Step 9: Checkpoint**

Run `./gradlew :app:testDebugUnitTest`. All green.

---

## Task 3: Domain models and the reel decks (pure domain)

**Files:**
- Create: `app/src/main/java/com/rzi/quotes/domain/model/Quote.kt`
- Create: `app/src/main/java/com/rzi/quotes/domain/model/Reel.kt`
- Create: `app/src/main/java/com/rzi/quotes/domain/reel/Deck.kt`
- Create: `app/src/main/java/com/rzi/quotes/domain/reel/LinearDeck.kt`
- Create: `app/src/main/java/com/rzi/quotes/domain/reel/ShuffleDeck.kt`
- Test: `app/src/test/java/com/rzi/quotes/domain/reel/LinearDeckTest.kt`
- Test: `app/src/test/java/com/rzi/quotes/domain/reel/ShuffleDeckTest.kt`
- Test: `app/src/test/java/com/rzi/quotes/domain/reel/DeckModeSwitchTest.kt`

**Interfaces:**
- Consumes: nothing.
- Produces:
  - `data class Quote(id: Long, text: String, bookName: String, pageNumber: Int?, tags: List<String>, createdAt: Long, updatedAt: Long)`
  - `data class QuoteDraft(id: Long?, text: String, bookName: String, pageNumber: Int?, tags: List<String>)`
  - `data class Book(id: Long, name: String)`, `data class TagFilter(id: Long, name: String, usageCount: Int)`
  - `enum class ReelMode { SHUFFLE, LINEAR }`, `data class ReelFilter(bookId: Long?, tagIds: List<Long>)`, `data class ReelPersistedState(mode, baseSeed, absoluteIndex, currentQuoteId, filter)`
  - `interface Deck { val size: Int; fun idAt(index: Int): Long; fun indexOfId(id: Long, nearIndex: Int): Int? }`
  - `Decks.create(mode: ReelMode, ids: List<Long>, baseSeed: Long): Deck`

- [ ] **Step 1: Create the domain models**

Create `app/src/main/java/com/rzi/quotes/domain/model/Quote.kt`:

```kotlin
package com.rzi.quotes.domain.model

data class Quote(
    val id: Long,
    val text: String,
    val bookName: String,
    val pageNumber: Int?,
    val tags: List<String>,
    val createdAt: Long,
    val updatedAt: Long,
)

/** A quote as the editor supplies it. [id] is null when adding, set when editing. */
data class QuoteDraft(
    val id: Long? = null,
    val text: String,
    val bookName: String,
    val pageNumber: Int?,
    val tags: List<String>,
)

data class Book(val id: Long, val name: String)

data class TagFilter(val id: Long, val name: String, val usageCount: Int)
```

Create `app/src/main/java/com/rzi/quotes/domain/model/Reel.kt`:

```kotlin
package com.rzi.quotes.domain.model

enum class ReelMode { SHUFFLE, LINEAR }

data class ReelFilter(
    val bookId: Long? = null,
    val tagIds: List<Long> = emptyList(),
) {
    val isActive: Boolean get() = bookId != null || tagIds.isNotEmpty()
}

data class ReelPersistedState(
    val mode: ReelMode = ReelMode.SHUFFLE,
    val baseSeed: Long = 0L,
    val absoluteIndex: Int = 0,
    val currentQuoteId: Long? = null,
    val filter: ReelFilter = ReelFilter(),
)
```

- [ ] **Step 2: Write the failing LinearDeck tests**

Create `app/src/test/java/com/rzi/quotes/domain/reel/LinearDeckTest.kt`:

```kotlin
package com.rzi.quotes.domain.reel

import com.google.common.truth.Truth.assertThat
import org.junit.Test

class LinearDeckTest {

    private val deck = LinearDeck(listOf(10L, 20L, 30L))

    @Test
    fun `returns ids in order`() {
        assertThat(listOf(deck.idAt(0), deck.idAt(1), deck.idAt(2)))
            .containsExactly(10L, 20L, 30L)
            .inOrder()
    }

    @Test
    fun `wraps forward past the end`() {
        assertThat(deck.idAt(3)).isEqualTo(10L)
        assertThat(deck.idAt(4)).isEqualTo(20L)
        assertThat(deck.idAt(7)).isEqualTo(20L)
    }

    @Test
    fun `wraps backward past zero`() {
        assertThat(deck.idAt(-1)).isEqualTo(30L)
        assertThat(deck.idAt(-2)).isEqualTo(20L)
        assertThat(deck.idAt(-4)).isEqualTo(30L)
    }

    @Test
    fun `size reflects the id list`() {
        assertThat(deck.size).isEqualTo(3)
    }

    @Test
    fun `single id deck returns that id at every index`() {
        val single = LinearDeck(listOf(99L))
        assertThat(single.idAt(0)).isEqualTo(99L)
        assertThat(single.idAt(5)).isEqualTo(99L)
        assertThat(single.idAt(-5)).isEqualTo(99L)
    }

    @Test
    fun `empty deck reports size zero`() {
        assertThat(LinearDeck(emptyList()).size).isEqualTo(0)
    }

    @Test
    fun `indexOfId finds an id in the same cycle as the reference index`() {
        // index 4 is in cycle 1 (indices 3..5); id 30 sits at position 2, so absolute index 5.
        assertThat(deck.indexOfId(30L, nearIndex = 4)).isEqualTo(5)
    }

    @Test
    fun `indexOfId returns null for an unknown id`() {
        assertThat(deck.indexOfId(999L, nearIndex = 0)).isNull()
    }
}
```

- [ ] **Step 3: Run to verify it fails**

Run: `./gradlew :app:testDebugUnitTest --tests '*LinearDeckTest*'`
Expected: FAIL — unresolved reference `LinearDeck`.

- [ ] **Step 4: Implement Deck and LinearDeck**

Create `app/src/main/java/com/rzi/quotes/domain/reel/Deck.kt`:

```kotlin
package com.rzi.quotes.domain.reel

import com.rzi.quotes.domain.model.ReelMode

/**
 * An endless ordering over a fixed set of quote ids.
 *
 * Indices are unbounded in both directions: the pager walks a huge index space and the deck maps
 * any index onto an id. Implementations must be deterministic, so that swiping backwards returns
 * exactly the page the reader came from.
 */
interface Deck {

    val size: Int

    /** @throws IllegalStateException when [size] is 0. */
    fun idAt(index: Int): Long

    /**
     * Absolute index of [id] within the same cycle as [nearIndex], or null when [id] is not in this
     * deck. Used to keep the reader on the same quote across a mode switch or a process restart.
     */
    fun indexOfId(id: Long, nearIndex: Int): Int?
}

object Decks {
    fun create(mode: ReelMode, ids: List<Long>, baseSeed: Long): Deck = when (mode) {
        ReelMode.LINEAR -> LinearDeck(ids)
        ReelMode.SHUFFLE -> ShuffleDeck(ids, baseSeed)
    }
}
```

Create `app/src/main/java/com/rzi/quotes/domain/reel/LinearDeck.kt`:

```kotlin
package com.rzi.quotes.domain.reel

/** Sequential deck. [orderedIds] arrives already sorted by book name, then page number. */
class LinearDeck(private val orderedIds: List<Long>) : Deck {

    override val size: Int get() = orderedIds.size

    override fun idAt(index: Int): Long {
        check(size > 0) { "Cannot read from an empty deck" }
        return orderedIds[index.mod(size)]
    }

    override fun indexOfId(id: Long, nearIndex: Int): Int? {
        if (size == 0) return null
        val position = orderedIds.indexOf(id)
        if (position < 0) return null
        val cycle = nearIndex.floorDiv(size)
        return cycle * size + position
    }
}
```

`index.mod(size)` is Kotlin's floor modulus, so `(-1).mod(3) == 2`. Plain `%` would return `-1` and crash on the list access — that is exactly the backward-swipe bug this guards.

- [ ] **Step 5: Run to verify LinearDeck passes**

Run: `./gradlew :app:testDebugUnitTest --tests '*LinearDeckTest*'`
Expected: PASS, 8 tests.

- [ ] **Step 6: Write the failing ShuffleDeck tests**

Create `app/src/test/java/com/rzi/quotes/domain/reel/ShuffleDeckTest.kt`:

```kotlin
package com.rzi.quotes.domain.reel

import com.google.common.truth.Truth.assertThat
import org.junit.Test

class ShuffleDeckTest {

    private val ids = (1L..20L).toList()

    @Test
    fun `every id appears exactly once within a cycle`() {
        val deck = ShuffleDeck(ids, baseSeed = 42L)
        val cycle = (0 until 20).map { deck.idAt(it) }
        assertThat(cycle).containsExactlyElementsIn(ids)
    }

    @Test
    fun `the second cycle also contains every id exactly once`() {
        val deck = ShuffleDeck(ids, baseSeed = 42L)
        val cycle = (20 until 40).map { deck.idAt(it) }
        assertThat(cycle).containsExactlyElementsIn(ids)
    }

    @Test
    fun `consecutive cycles use different orders`() {
        val deck = ShuffleDeck(ids, baseSeed = 42L)
        val first = (0 until 20).map { deck.idAt(it) }
        val second = (20 until 40).map { deck.idAt(it) }
        assertThat(second).isNotEqualTo(first)
    }

    @Test
    fun `idAt is stable when revisited`() {
        val deck = ShuffleDeck(ids, baseSeed = 42L)
        val forward = (0 until 25).map { deck.idAt(it) }
        val backward = (24 downTo 0).map { deck.idAt(it) }
        assertThat(backward.reversed()).isEqualTo(forward)
    }

    @Test
    fun `the same seed produces the same order`() {
        val a = ShuffleDeck(ids, baseSeed = 7L)
        val b = ShuffleDeck(ids, baseSeed = 7L)
        assertThat((0 until 40).map { b.idAt(it) })
            .isEqualTo((0 until 40).map { a.idAt(it) })
    }

    @Test
    fun `different seeds produce different orders`() {
        val a = ShuffleDeck(ids, baseSeed = 7L)
        val b = ShuffleDeck(ids, baseSeed = 8L)
        assertThat((0 until 20).map { b.idAt(it) })
            .isNotEqualTo((0 until 20).map { a.idAt(it) })
    }

    @Test
    fun `negative indices walk backwards into earlier cycles`() {
        val deck = ShuffleDeck(ids, baseSeed = 42L)
        val previousCycle = (-20 until 0).map { deck.idAt(it) }
        assertThat(previousCycle).containsExactlyElementsIn(ids)
    }

    @Test
    fun `single id deck returns that id at every index`() {
        val deck = ShuffleDeck(listOf(99L), baseSeed = 1L)
        assertThat(deck.idAt(0)).isEqualTo(99L)
        assertThat(deck.idAt(37)).isEqualTo(99L)
        assertThat(deck.idAt(-4)).isEqualTo(99L)
    }

    @Test
    fun `empty deck reports size zero`() {
        assertThat(ShuffleDeck(emptyList(), baseSeed = 1L).size).isEqualTo(0)
    }

    @Test
    fun `indexOfId round trips through idAt`() {
        val deck = ShuffleDeck(ids, baseSeed = 42L)
        val id = deck.idAt(25)
        val found = deck.indexOfId(id, nearIndex = 25)
        assertThat(found).isEqualTo(25)
    }

    @Test
    fun `indexOfId returns null for an unknown id`() {
        val deck = ShuffleDeck(ids, baseSeed = 42L)
        assertThat(deck.indexOfId(999L, nearIndex = 0)).isNull()
    }
}
```

- [ ] **Step 7: Run to verify it fails**

Run: `./gradlew :app:testDebugUnitTest --tests '*ShuffleDeckTest*'`
Expected: FAIL — unresolved reference `ShuffleDeck`.

- [ ] **Step 8: Implement ShuffleDeck**

Create `app/src/main/java/com/rzi/quotes/domain/reel/ShuffleDeck.kt`:

```kotlin
package com.rzi.quotes.domain.reel

import kotlin.random.Random

/**
 * Endless shuffled deck built from per-cycle permutations.
 *
 * A cycle is one full pass over [ids]. The permutation for cycle N is derived from
 * `baseSeed + N`, which makes the whole sequence deterministic and therefore reversible — the
 * reader can swipe back and land on the page they came from, which a bare `Random.nextInt()`
 * cannot offer. Within a cycle each id appears exactly once; each new cycle reshuffles.
 *
 * Only the most recently used cycle's permutation is retained.
 */
class ShuffleDeck(
    private val ids: List<Long>,
    private val baseSeed: Long,
) : Deck {

    override val size: Int get() = ids.size

    private var cachedCycle: Int? = null
    private var cachedPermutation: List<Long> = emptyList()

    override fun idAt(index: Int): Long {
        check(size > 0) { "Cannot read from an empty deck" }
        return permutationFor(index.floorDiv(size))[index.mod(size)]
    }

    override fun indexOfId(id: Long, nearIndex: Int): Int? {
        if (size == 0) return null
        val cycle = nearIndex.floorDiv(size)
        val position = permutationFor(cycle).indexOf(id)
        if (position < 0) return null
        return cycle * size + position
    }

    private fun permutationFor(cycle: Int): List<Long> {
        if (cachedCycle != cycle) {
            cachedPermutation = ids.shuffled(Random(baseSeed + cycle))
            cachedCycle = cycle
        }
        return cachedPermutation
    }
}
```

- [ ] **Step 9: Run to verify ShuffleDeck passes**

Run: `./gradlew :app:testDebugUnitTest --tests '*ShuffleDeckTest*'`
Expected: PASS, 11 tests.

- [ ] **Step 10: Write the mode-switch test**

Create `app/src/test/java/com/rzi/quotes/domain/reel/DeckModeSwitchTest.kt`:

```kotlin
package com.rzi.quotes.domain.reel

import com.google.common.truth.Truth.assertThat
import com.rzi.quotes.domain.model.ReelMode
import org.junit.Test

/**
 * Switching mode must keep the reader on the same quote: find the current id in the new deck and
 * jump to that index.
 */
class DeckModeSwitchTest {

    private val ids = (1L..12L).toList()

    @Test
    fun `shuffle to linear keeps the current quote`() {
        val shuffle = Decks.create(ReelMode.SHUFFLE, ids, baseSeed = 3L)
        val currentIndex = 17
        val currentId = shuffle.idAt(currentIndex)

        val linear = Decks.create(ReelMode.LINEAR, ids, baseSeed = 3L)
        val newIndex = linear.indexOfId(currentId, nearIndex = currentIndex)

        assertThat(newIndex).isNotNull()
        assertThat(linear.idAt(newIndex!!)).isEqualTo(currentId)
    }

    @Test
    fun `linear to shuffle keeps the current quote`() {
        val linear = Decks.create(ReelMode.LINEAR, ids, baseSeed = 3L)
        val currentIndex = 5
        val currentId = linear.idAt(currentIndex)

        val shuffle = Decks.create(ReelMode.SHUFFLE, ids, baseSeed = 3L)
        val newIndex = shuffle.indexOfId(currentId, nearIndex = currentIndex)

        assertThat(newIndex).isNotNull()
        assertThat(shuffle.idAt(newIndex!!)).isEqualTo(currentId)
    }

    @Test
    fun `factory returns the deck matching the mode`() {
        assertThat(Decks.create(ReelMode.LINEAR, ids, 0L)).isInstanceOf(LinearDeck::class.java)
        assertThat(Decks.create(ReelMode.SHUFFLE, ids, 0L)).isInstanceOf(ShuffleDeck::class.java)
    }
}
```

- [ ] **Step 11: Run to verify it passes**

Run: `./gradlew :app:testDebugUnitTest --tests '*DeckModeSwitchTest*'`
Expected: PASS, 3 tests. `Decks`, `LinearDeck`, and `ShuffleDeck` all exist by now, so this suite should pass without new production code.

- [ ] **Step 12: Checkpoint**

Run `./gradlew :app:testDebugUnitTest`. All green.

---

## Task 4: Room schema, DAOs, and FTS sync

**Files:**
- Create: `app/src/main/java/com/rzi/quotes/data/local/entity/QuoteEntity.kt`, `BookEntity.kt`, `TagEntity.kt`, `QuoteTagCrossRef.kt`, `QuoteFtsEntity.kt`
- Create: `app/src/main/java/com/rzi/quotes/data/local/row/QuoteRow.kt`
- Create: `app/src/main/java/com/rzi/quotes/data/local/dao/QuoteDao.kt`, `BookDao.kt`, `TagDao.kt`, `QuoteFtsDao.kt`
- Create: `app/src/main/java/com/rzi/quotes/data/local/RziDatabase.kt`
- Create: `app/src/test/java/com/rzi/quotes/testutil/DbFixtures.kt`
- Test: `app/src/test/java/com/rzi/quotes/data/local/QuoteDaoTest.kt`

**Interfaces:**
- Consumes: `DedupeKey` (Task 2).
- Produces:
  - `RziDatabase` with `quoteDao()`, `bookDao()`, `tagDao()`, `quoteFtsDao()`.
  - `QuoteRow(id, text, bookName, pageNumber, tagsCsv, createdAt, updatedAt)`.
  - DAO methods named exactly as written below — Tasks 5, 6, 12, and 13 call them.
  - `DbFixtures.inMemoryDatabase()` and `DbFixtures.insertQuote(...)` for later test tasks.

- [ ] **Step 1: Create the entities**

`app/src/main/java/com/rzi/quotes/data/local/entity/BookEntity.kt`:

```kotlin
package com.rzi.quotes.data.local.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "books",
    indices = [Index(value = ["name"], unique = true)],
)
data class BookEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    @ColumnInfo(collate = ColumnInfo.NOCASE) val name: String,
)
```

`TagEntity.kt` is the same shape with `tableName = "tags"`.

```kotlin
package com.rzi.quotes.data.local.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "tags",
    indices = [Index(value = ["name"], unique = true)],
)
data class TagEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    @ColumnInfo(collate = ColumnInfo.NOCASE) val name: String,
)
```

`QuoteEntity.kt`:

```kotlin
package com.rzi.quotes.data.local.entity

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "quotes",
    foreignKeys = [
        ForeignKey(
            entity = BookEntity::class,
            parentColumns = ["id"],
            childColumns = ["bookId"],
            onDelete = ForeignKey.RESTRICT,
        ),
    ],
    indices = [
        Index(value = ["bookId"]),
        Index(value = ["createdAt"]),
        Index(value = ["dedupeKey"], unique = true),
    ],
)
data class QuoteEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val text: String,
    val bookId: Long,
    val pageNumber: Int?,
    val dedupeKey: String,
    val createdAt: Long,
    val updatedAt: Long,
)
```

`QuoteTagCrossRef.kt`:

```kotlin
package com.rzi.quotes.data.local.entity

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index

@Entity(
    tableName = "quote_tags",
    primaryKeys = ["quoteId", "tagId"],
    foreignKeys = [
        ForeignKey(
            entity = QuoteEntity::class,
            parentColumns = ["id"],
            childColumns = ["quoteId"],
            onDelete = ForeignKey.CASCADE,
        ),
        ForeignKey(
            entity = TagEntity::class,
            parentColumns = ["id"],
            childColumns = ["tagId"],
            onDelete = ForeignKey.CASCADE,
        ),
    ],
    indices = [Index(value = ["tagId"])],
)
data class QuoteTagCrossRef(
    val quoteId: Long,
    val tagId: Long,
)
```

`QuoteFtsEntity.kt`:

```kotlin
package com.rzi.quotes.data.local.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.Fts4
import androidx.room.PrimaryKey

/**
 * Search index over quote text, book name, and flattened tag names.
 *
 * Deliberately NOT an external-content table: `tagsFlat` is not a column on `quotes`, and external
 * content requires the FTS columns to mirror the content entity. The trade-off is that rows must be
 * written explicitly — every quote write in [com.rzi.quotes.data.repository.QuoteRepositoryImpl]
 * happens inside a transaction that also updates this table.
 */
@Fts4
@Entity(tableName = "quote_fts")
data class QuoteFtsEntity(
    @PrimaryKey @ColumnInfo(name = "rowid") val rowId: Long,
    val text: String,
    val bookName: String,
    val tagsFlat: String,
)
```

- [ ] **Step 2: Create the flat row projection**

`app/src/main/java/com/rzi/quotes/data/local/row/QuoteRow.kt`:

```kotlin
package com.rzi.quotes.data.local.row

/**
 * Flat JOIN projection. Using `group_concat` for tags instead of Room's `@Relation` keeps this
 * usable as a Paging projection and avoids a per-row follow-up query.
 */
data class QuoteRow(
    val id: Long,
    val text: String,
    val bookName: String,
    val pageNumber: Int?,
    val tagsCsv: String?,
    val createdAt: Long,
    val updatedAt: Long,
)
```

- [ ] **Step 3: Create the DAOs**

`app/src/main/java/com/rzi/quotes/data/local/dao/BookDao.kt`:

```kotlin
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
```

`app/src/main/java/com/rzi/quotes/data/local/dao/TagDao.kt`:

```kotlin
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
```

`app/src/main/java/com/rzi/quotes/data/local/dao/QuoteFtsDao.kt`:

```kotlin
package com.rzi.quotes.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.rzi.quotes.data.local.entity.QuoteFtsEntity

@Dao
interface QuoteFtsDao {

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(row: QuoteFtsEntity)

    @Query("DELETE FROM quote_fts WHERE rowid = :quoteId")
    suspend fun delete(quoteId: Long)

    @Query("SELECT COUNT(*) FROM quote_fts")
    suspend fun count(): Int
}
```

`app/src/main/java/com/rzi/quotes/data/local/dao/QuoteDao.kt`:

```kotlin
package com.rzi.quotes.data.local.dao

import androidx.paging.PagingSource
import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import com.rzi.quotes.data.local.entity.QuoteEntity
import com.rzi.quotes.data.local.row.QuoteRow
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

    companion object {
        const val ROW_SELECT = """
            SELECT q.id AS id, q.text AS text, b.name AS bookName, q.pageNumber AS pageNumber,
                   (SELECT group_concat(t.name, ',') FROM quote_tags qt
                     JOIN tags t ON t.id = qt.tagId WHERE qt.quoteId = q.id) AS tagsCsv,
                   q.createdAt AS createdAt, q.updatedAt AS updatedAt
            FROM quotes q JOIN books b ON b.id = q.bookId
        """
    }
}
```

Search, reel-id, and paging queries are added to this DAO in Task 5.

- [ ] **Step 4: Create the database**

`app/src/main/java/com/rzi/quotes/data/local/RziDatabase.kt`:

```kotlin
package com.rzi.quotes.data.local

import androidx.room.Database
import androidx.room.RoomDatabase
import com.rzi.quotes.data.local.dao.BookDao
import com.rzi.quotes.data.local.dao.QuoteDao
import com.rzi.quotes.data.local.dao.QuoteFtsDao
import com.rzi.quotes.data.local.dao.TagDao
import com.rzi.quotes.data.local.entity.BookEntity
import com.rzi.quotes.data.local.entity.QuoteEntity
import com.rzi.quotes.data.local.entity.QuoteFtsEntity
import com.rzi.quotes.data.local.entity.QuoteTagCrossRef
import com.rzi.quotes.data.local.entity.TagEntity

@Database(
    entities = [
        QuoteEntity::class,
        BookEntity::class,
        TagEntity::class,
        QuoteTagCrossRef::class,
        QuoteFtsEntity::class,
    ],
    version = 1,
    exportSchema = true,
)
abstract class RziDatabase : RoomDatabase() {
    abstract fun quoteDao(): QuoteDao
    abstract fun bookDao(): BookDao
    abstract fun tagDao(): TagDao
    abstract fun quoteFtsDao(): QuoteFtsDao

    companion object {
        const val NAME = "rzi.db"
    }
}
```

- [ ] **Step 5: Create the shared test fixtures**

`app/src/test/java/com/rzi/quotes/testutil/DbFixtures.kt`:

```kotlin
package com.rzi.quotes.testutil

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.rzi.quotes.data.local.RziDatabase
import com.rzi.quotes.data.local.entity.BookEntity
import com.rzi.quotes.data.local.entity.QuoteEntity
import com.rzi.quotes.data.local.entity.QuoteFtsEntity
import com.rzi.quotes.data.local.entity.QuoteTagCrossRef
import com.rzi.quotes.data.local.entity.TagEntity
import com.rzi.quotes.domain.text.DedupeKey

object DbFixtures {

    fun inMemoryDatabase(): RziDatabase {
        val context = ApplicationProvider.getApplicationContext<Context>()
        return Room.inMemoryDatabaseBuilder(context, RziDatabase::class.java)
            .allowMainThreadQueries()
            .build()
    }

    /** Inserts a quote plus its book, tags, links, and FTS row. Returns the new quote id. */
    suspend fun insertQuote(
        db: RziDatabase,
        text: String,
        bookName: String,
        pageNumber: Int? = null,
        tags: List<String> = emptyList(),
        createdAt: Long = 1_000L,
    ): Long {
        val bookDao = db.bookDao()
        val tagDao = db.tagDao()
        bookDao.insertIgnoring(BookEntity(name = bookName))
        val bookId = requireNotNull(bookDao.findByName(bookName)).id

        val quoteId = db.quoteDao().insertIgnoring(
            QuoteEntity(
                text = text,
                bookId = bookId,
                pageNumber = pageNumber,
                dedupeKey = DedupeKey.of(text, bookName, pageNumber),
                createdAt = createdAt,
                updatedAt = createdAt,
            )
        )
        require(quoteId != -1L) { "Fixture quote was a duplicate: $text" }

        tags.forEach { name ->
            tagDao.insertIgnoring(TagEntity(name = name))
            val tagId = requireNotNull(tagDao.findByName(name)).id
            tagDao.link(QuoteTagCrossRef(quoteId = quoteId, tagId = tagId))
        }

        db.quoteFtsDao().upsert(
            QuoteFtsEntity(
                rowId = quoteId,
                text = text,
                bookName = bookName,
                tagsFlat = tags.joinToString(" "),
            )
        )
        return quoteId
    }
}
```

- [ ] **Step 6: Write the DAO test, including the FTS smoke test**

`app/src/test/java/com/rzi/quotes/data/local/QuoteDaoTest.kt`:

```kotlin
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
        // Guards the whole Robolectric + FTS4 arrangement; if this fails, see the note below.
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
```

- [ ] **Step 7: Run the DAO tests**

Run: `./gradlew :app:testDebugUnitTest --tests '*QuoteDaoTest*'`
Expected: PASS, 11 tests.

**If `fts table is usable in this test environment` fails** with an SQLite error about `fts4` or `MATCH`, Robolectric's SQLite build in this environment lacks FTS4. Do not work around it in production code. Instead: move `QuoteDaoTest`, plus the search/suggestion tests from Task 5 and the repository/transfer tests from Tasks 6, 12, and 13, to `app/src/androidTest/java/...` as instrumented tests (swap `RobolectricTestRunner` for `AndroidJUnit4`, drop `@Config`, and add `androidTestImplementation(libs.androidx.test.ext.junit)` plus `androidTestImplementation(libs.androidx.test.runner)` to the catalog and module). The pure-JUnit suites from Tasks 2, 3, and 6 stay on the JVM either way. Record the choice in a comment at the top of `DbFixtures.kt` so later tasks put their tests in the right source set.

- [ ] **Step 8: Verify the schema was exported**

Run:

```bash
ls app/schemas/com.rzi.quotes.data.local.RziDatabase/1.json
```

Expected: the file exists. It is the baseline for any future migration.

- [ ] **Step 9: Checkpoint**

Run `./gradlew :app:testDebugUnitTest`. All green.

---

## Task 5: Search, reel-id, and suggestion queries

**Files:**
- Modify: `app/src/main/java/com/rzi/quotes/data/local/dao/QuoteDao.kt`
- Test: `app/src/test/java/com/rzi/quotes/data/local/SearchQueryTest.kt`
- Test: `app/src/test/java/com/rzi/quotes/data/local/SuggestionsTest.kt`

**Interfaces:**
- Consumes: `QuoteDao.ROW_SELECT`, `DbFixtures` (Task 4); `FtsQuery` (Task 2).
- Produces, on `QuoteDao`:
  - `fun pagingSource(hasQuery: Int, ftsQuery: String, tagIds: List<Long>, tagCount: Int): PagingSource<Int, QuoteRow>`
  - `suspend fun searchRows(hasQuery: Int, ftsQuery: String, tagIds: List<Long>, tagCount: Int): List<QuoteRow>` — same WHERE clause, used by tests and by the result count.
  - `fun observeReelIdsForShuffle(bookId: Long?, tagIds: List<Long>, tagCount: Int): Flow<List<Long>>`
  - `fun observeReelIdsForLinear(bookId: Long?, tagIds: List<Long>, tagCount: Int): Flow<List<Long>>`
  - `fun observeMatchCount(hasQuery: Int, ftsQuery: String, tagIds: List<Long>, tagCount: Int): Flow<Int>`

`hasQuery` is `0` or `1` rather than a Boolean because the flag short-circuits the FTS subquery inside SQL; Room maps Kotlin `Boolean` to an integer anyway, and an explicit `Int` keeps the SQL obvious.

- [ ] **Step 1: Write the failing search tests**

`app/src/test/java/com/rzi/quotes/data/local/SearchQueryTest.kt`:

```kotlin
package com.rzi.quotes.data.local

import com.google.common.truth.Truth.assertThat
import com.rzi.quotes.domain.text.FtsQuery
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
    fun `punctuation in the query does not break the match`() = runTest {
        assertThat(search("don't")).hasSize(1) // "So Good They Can't Ignore You" → token "don" is absent
            .also { assertThat(search("can")).hasSize(1) }
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
    fun `multiple tags are ANDed not ORed`() = runTest {
        val focusId = db.tagDao().findByName("focus")!!.id
        val workId = db.tagDao().findByName("work")!!.id

        val result = search(tagIds = listOf(focusId, workId))

        assertThat(result).hasSize(1)
        assertThat(result.single().bookName).isEqualTo("So Good They Can't Ignore You")
    }

    @Test
    fun `query and tag filter combine`() = runTest {
        val focusId = db.tagDao().findByName("focus")!!.id
        assertThat(search("solitude", tagIds = listOf(focusId))).hasSize(1)
        assertThat(search("generosity", tagIds = listOf(focusId))).isEmpty()
    }

    @Test
    fun `match count matches the row count`() = runTest {
        val count = db.quoteDao().observeMatchCount(0, "", emptyList(), 0).first()
        assertThat(count).isEqualTo(3)
    }

    @Test
    fun `shuffle reel ids are unfiltered by default`() = runTest {
        assertThat(db.quoteDao().observeReelIdsForShuffle(null, emptyList(), 0).first()).hasSize(3)
    }

    @Test
    fun `linear reel ids are ordered by book then page with unpaged last`() = runTest {
        DbFixtures.insertQuote(db, "second page of deep work", "Deep Work", pageNumber = 7)
        DbFixtures.insertQuote(db, "unpaged deep work note", "Deep Work", pageNumber = null)

        val ids = db.quoteDao().observeReelIdsForLinear(null, emptyList(), 0).first()
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
        val ids = db.quoteDao().observeReelIdsForShuffle(bookId, emptyList(), 0).first()
        assertThat(ids).hasSize(1)
    }

    @Test
    fun `reel ids honour ANDed tag filters`() = runTest {
        val focusId = db.tagDao().findByName("focus")!!.id
        val workId = db.tagDao().findByName("work")!!.id
        val ids = db.quoteDao()
            .observeReelIdsForShuffle(null, listOf(focusId, workId), 2)
            .first()
        assertThat(ids).hasSize(1)
    }
}
```

- [ ] **Step 2: Run to verify it fails**

Run: `./gradlew :app:testDebugUnitTest --tests '*SearchQueryTest*'`
Expected: FAIL — `searchRows`, `observeMatchCount`, `observeReelIdsForShuffle`, and `observeReelIdsForLinear` are unresolved.

- [ ] **Step 3: Add the queries to QuoteDao**

Append to `QuoteDao` (inside the interface body), and extend the `companion object` with the shared predicate:

```kotlin
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
```

And in the `companion object`:

```kotlin
        /** Tag chips are ANDed: the quote must carry every selected tag. */
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
```

- [ ] **Step 4: Run to verify the search tests pass**

Run: `./gradlew :app:testDebugUnitTest --tests '*SearchQueryTest*'`
Expected: PASS, 16 tests.

If Room rejects `:bookId IS NULL` for a nullable `Long?` parameter, change the signature to take `bookId: Long?` and the SQL to `(:bookId IS NULL OR q.bookId = :bookId)` — which is what is written. If the generated code still complains, pass `-1L` as a sentinel for "no book" and compare `(:bookId = -1 OR q.bookId = :bookId)`, updating the tests' expectations to use `-1L`.

- [ ] **Step 5: Write the suggestion tests**

`app/src/test/java/com/rzi/quotes/data/local/SuggestionsTest.kt`:

```kotlin
package com.rzi.quotes.data.local

import com.google.common.truth.Truth.assertThat
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
```

- [ ] **Step 6: Run the suggestion tests**

Run: `./gradlew :app:testDebugUnitTest --tests '*SuggestionsTest*'`
Expected: PASS, 7 tests. No new production code should be needed — `BookDao.suggest` and `TagDao.suggest` came from Task 4.

- [ ] **Step 7: Checkpoint**

Run `./gradlew :app:testDebugUnitTest`. All green.

---

## Task 6: Repository, mappers, and use cases

**Files:**
- Create: `app/src/main/java/com/rzi/quotes/domain/model/Results.kt`
- Create: `app/src/main/java/com/rzi/quotes/domain/repository/QuoteRepository.kt`
- Create: `app/src/main/java/com/rzi/quotes/data/mapper/QuoteMappers.kt`
- Create: `app/src/main/java/com/rzi/quotes/data/repository/QuoteRepositoryImpl.kt`
- Create: `app/src/main/java/com/rzi/quotes/domain/usecase/SaveQuote.kt`, `DeleteQuote.kt`, `SearchQuotes.kt`, `BookSuggestions.kt`, `TagSuggestions.kt`
- Test: `app/src/test/java/com/rzi/quotes/domain/usecase/SaveQuoteTest.kt`
- Test: `app/src/test/java/com/rzi/quotes/data/repository/QuoteRepositoryImplTest.kt`

**Interfaces:**
- Consumes: all DAOs (Tasks 4–5), `DedupeKey`, `FtsQuery` (Task 2), domain models (Task 3).
- Produces:
  - `ValidationErrors(text: String?, bookName: String?, pageNumber: String?)` with `val hasErrors: Boolean`
  - `sealed interface SaveQuoteResult { data class Saved(val id: Long); object Duplicate; data class Invalid(val errors: ValidationErrors) }`
  - `QuoteRepository` with `pagedQuotes`, `observeMatchCount`, `observeReelIds`, `quoteById`, `saveValidated`, `delete`, `bookSuggestions`, `tagSuggestions`, `observeTagFilters`, `observeBooks`, `observeQuoteCount`
  - `SaveQuote(repository)` — `suspend operator fun invoke(draft: QuoteDraft): SaveQuoteResult`
  - `DeleteQuote`, `SearchQuotes`, `BookSuggestions`, `TagSuggestions` — thin delegates

- [ ] **Step 1: Create the result models**

`app/src/main/java/com/rzi/quotes/domain/model/Results.kt`:

```kotlin
package com.rzi.quotes.domain.model

data class ValidationErrors(
    val text: String? = null,
    val bookName: String? = null,
    val pageNumber: String? = null,
) {
    val hasErrors: Boolean get() = text != null || bookName != null || pageNumber != null
}

sealed interface SaveQuoteResult {
    data class Saved(val id: Long) : SaveQuoteResult
    data object Duplicate : SaveQuoteResult
    data class Invalid(val errors: ValidationErrors) : SaveQuoteResult
}

data class ImportResult(
    val added: Int,
    val skippedDuplicates: Int,
    val skippedInvalid: Int,
)

enum class TransferError {
    UNREADABLE_FILE,
    NOT_A_DATABASE,
    SCHEMA_MISMATCH,
    NO_QUOTES_FOUND,
    WRITE_FAILED,
}

sealed interface ImportOutcome {
    data class Success(val result: ImportResult) : ImportOutcome
    data class Failure(val reason: TransferError) : ImportOutcome
}

sealed interface ExportOutcome {
    data object Success : ExportOutcome
    data class Failure(val reason: TransferError) : ExportOutcome
}
```

- [ ] **Step 2: Create the repository port**

`app/src/main/java/com/rzi/quotes/domain/repository/QuoteRepository.kt`:

```kotlin
package com.rzi.quotes.domain.repository

import androidx.paging.PagingData
import com.rzi.quotes.domain.model.Book
import com.rzi.quotes.domain.model.Quote
import com.rzi.quotes.domain.model.QuoteDraft
import com.rzi.quotes.domain.model.ReelFilter
import com.rzi.quotes.domain.model.ReelMode
import com.rzi.quotes.domain.model.SaveQuoteResult
import com.rzi.quotes.domain.model.TagFilter
import kotlinx.coroutines.flow.Flow

interface QuoteRepository {

    fun pagedQuotes(query: String, tagIds: List<Long>): Flow<PagingData<Quote>>

    fun observeMatchCount(query: String, tagIds: List<Long>): Flow<Int>

    fun observeReelIds(mode: ReelMode, filter: ReelFilter): Flow<List<Long>>

    suspend fun quoteById(id: Long): Quote?

    /**
     * Persists an already-validated draft. Validation lives in
     * [com.rzi.quotes.domain.usecase.SaveQuote]; this returns [SaveQuoteResult.Duplicate] when the
     * dedupe key collides and [SaveQuoteResult.Saved] otherwise.
     */
    suspend fun saveValidated(draft: QuoteDraft, nowMillis: Long): SaveQuoteResult

    suspend fun delete(id: Long)

    fun bookSuggestions(prefix: String): Flow<List<String>>

    fun tagSuggestions(prefix: String): Flow<List<String>>

    fun observeTagFilters(): Flow<List<TagFilter>>

    fun observeBooks(): Flow<List<Book>>

    fun observeQuoteCount(): Flow<Int>
}
```

- [ ] **Step 3: Create the mapper**

`app/src/main/java/com/rzi/quotes/data/mapper/QuoteMappers.kt`:

```kotlin
package com.rzi.quotes.data.mapper

import com.rzi.quotes.data.local.row.QuoteRow
import com.rzi.quotes.domain.model.Quote

fun QuoteRow.toDomain(): Quote = Quote(
    id = id,
    text = text,
    bookName = bookName,
    pageNumber = pageNumber,
    tags = tagsCsv?.split(',')?.filter { it.isNotBlank() }?.sorted().orEmpty(),
    createdAt = createdAt,
    updatedAt = updatedAt,
)
```

- [ ] **Step 4: Write the failing SaveQuote validation tests**

`app/src/test/java/com/rzi/quotes/domain/usecase/SaveQuoteTest.kt`:

```kotlin
package com.rzi.quotes.domain.usecase

import androidx.paging.PagingData
import com.google.common.truth.Truth.assertThat
import com.rzi.quotes.domain.model.Book
import com.rzi.quotes.domain.model.Quote
import com.rzi.quotes.domain.model.QuoteDraft
import com.rzi.quotes.domain.model.ReelFilter
import com.rzi.quotes.domain.model.ReelMode
import com.rzi.quotes.domain.model.SaveQuoteResult
import com.rzi.quotes.domain.model.TagFilter
import com.rzi.quotes.domain.repository.QuoteRepository
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

        override fun pagedQuotes(query: String, tagIds: List<Long>): Flow<PagingData<Quote>> =
            flowOf(PagingData.empty())
        override fun observeMatchCount(query: String, tagIds: List<Long>): Flow<Int> = flowOf(0)
        override fun observeReelIds(mode: ReelMode, filter: ReelFilter): Flow<List<Long>> =
            flowOf(emptyList())
        override suspend fun quoteById(id: Long): Quote? = null
        override suspend fun delete(id: Long) = Unit
        override fun bookSuggestions(prefix: String): Flow<List<String>> = flowOf(emptyList())
        override fun tagSuggestions(prefix: String): Flow<List<String>> = flowOf(emptyList())
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
```

- [ ] **Step 5: Run to verify it fails**

Run: `./gradlew :app:testDebugUnitTest --tests '*SaveQuoteTest*'`
Expected: FAIL — unresolved reference `SaveQuote`.

- [ ] **Step 6: Implement SaveQuote and the thin use cases**

`app/src/main/java/com/rzi/quotes/domain/usecase/SaveQuote.kt`:

```kotlin
package com.rzi.quotes.domain.usecase

import com.rzi.quotes.domain.model.QuoteDraft
import com.rzi.quotes.domain.model.SaveQuoteResult
import com.rzi.quotes.domain.model.ValidationErrors
import com.rzi.quotes.domain.repository.QuoteRepository
import java.time.Clock
import javax.inject.Inject

/**
 * Validates and normalizes a draft, then persists it.
 *
 * Validation lives here rather than in the database because `NOT NULL` would happily accept a
 * whitespace-only string, and the editor needs field-level messages rather than an exception.
 */
class SaveQuote @Inject constructor(
    private val repository: QuoteRepository,
    private val clock: Clock,
) {

    suspend operator fun invoke(draft: QuoteDraft): SaveQuoteResult {
        val text = draft.text.trim()
        val bookName = draft.bookName.trim()

        val errors = ValidationErrors(
            text = if (text.isEmpty()) "Quote text can't be empty" else null,
            bookName = if (bookName.isEmpty()) "Book name can't be empty" else null,
            pageNumber = draft.pageNumber
                ?.takeIf { it < 1 }
                ?.let { "Page number must be 1 or higher" },
        )
        if (errors.hasErrors) return SaveQuoteResult.Invalid(errors)

        val tags = draft.tags
            .map { it.replace(",", "").trim() }
            .filter { it.isNotEmpty() }
            .distinctBy { it.lowercase() }

        return repository.saveValidated(
            draft = draft.copy(text = text, bookName = bookName, tags = tags),
            nowMillis = clock.millis(),
        )
    }
}
```

`DeleteQuote.kt`, `SearchQuotes.kt`, `BookSuggestions.kt`, `TagSuggestions.kt`:

```kotlin
package com.rzi.quotes.domain.usecase

import androidx.paging.PagingData
import com.rzi.quotes.domain.model.Quote
import com.rzi.quotes.domain.repository.QuoteRepository
import kotlinx.coroutines.flow.Flow
import javax.inject.Inject

class DeleteQuote @Inject constructor(private val repository: QuoteRepository) {
    suspend operator fun invoke(id: Long) = repository.delete(id)
}

class SearchQuotes @Inject constructor(private val repository: QuoteRepository) {
    operator fun invoke(query: String, tagIds: List<Long>): Flow<PagingData<Quote>> =
        repository.pagedQuotes(query, tagIds)
}

class BookSuggestions @Inject constructor(private val repository: QuoteRepository) {
    operator fun invoke(prefix: String): Flow<List<String>> = repository.bookSuggestions(prefix)
}

class TagSuggestions @Inject constructor(private val repository: QuoteRepository) {
    operator fun invoke(prefix: String): Flow<List<String>> = repository.tagSuggestions(prefix)
}
```

Put each class in the file named for it, as listed under **Files**; they are shown together here only to avoid repetition.

- [ ] **Step 7: Run to verify SaveQuote passes**

Run: `./gradlew :app:testDebugUnitTest --tests '*SaveQuoteTest*'`
Expected: PASS, 11 tests.

- [ ] **Step 8: Write the failing repository tests**

`app/src/test/java/com/rzi/quotes/data/repository/QuoteRepositoryImplTest.kt`:

```kotlin
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
```

- [ ] **Step 9: Run to verify it fails**

Run: `./gradlew :app:testDebugUnitTest --tests '*QuoteRepositoryImplTest*'`
Expected: FAIL — unresolved reference `QuoteRepositoryImpl`.

- [ ] **Step 10: Implement QuoteRepositoryImpl**

`app/src/main/java/com/rzi/quotes/data/repository/QuoteRepositoryImpl.kt`:

```kotlin
package com.rzi.quotes.data.repository

import androidx.paging.Pager
import androidx.paging.PagingConfig
import androidx.paging.PagingData
import androidx.paging.map
import androidx.room.withTransaction
import com.rzi.quotes.data.local.RziDatabase
import com.rzi.quotes.data.local.dao.BookDao
import com.rzi.quotes.data.local.dao.QuoteDao
import com.rzi.quotes.data.local.dao.QuoteFtsDao
import com.rzi.quotes.data.local.dao.TagDao
import com.rzi.quotes.data.local.entity.BookEntity
import com.rzi.quotes.data.local.entity.QuoteEntity
import com.rzi.quotes.data.local.entity.QuoteFtsEntity
import com.rzi.quotes.data.local.entity.QuoteTagCrossRef
import com.rzi.quotes.data.local.entity.TagEntity
import com.rzi.quotes.data.mapper.toDomain
import com.rzi.quotes.domain.model.Book
import com.rzi.quotes.domain.model.Quote
import com.rzi.quotes.domain.model.QuoteDraft
import com.rzi.quotes.domain.model.ReelFilter
import com.rzi.quotes.domain.model.ReelMode
import com.rzi.quotes.domain.model.SaveQuoteResult
import com.rzi.quotes.domain.model.TagFilter
import com.rzi.quotes.domain.repository.QuoteRepository
import com.rzi.quotes.domain.text.DedupeKey
import com.rzi.quotes.domain.text.FtsQuery
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class QuoteRepositoryImpl @Inject constructor(
    private val db: RziDatabase,
    private val quoteDao: QuoteDao,
    private val bookDao: BookDao,
    private val tagDao: TagDao,
    private val ftsDao: QuoteFtsDao,
) : QuoteRepository {

    override fun pagedQuotes(query: String, tagIds: List<Long>): Flow<PagingData<Quote>> {
        val fts = FtsQuery.sanitize(query)
        return Pager(
            config = PagingConfig(pageSize = 25, enablePlaceholders = false),
            pagingSourceFactory = {
                quoteDao.pagingSource(
                    hasQuery = if (fts == null) 0 else 1,
                    ftsQuery = fts.orEmpty(),
                    tagIds = tagIds,
                    tagCount = tagIds.size,
                )
            },
        ).flow.map { paging -> paging.map { row -> row.toDomain() } }
    }

    override fun observeMatchCount(query: String, tagIds: List<Long>): Flow<Int> {
        val fts = FtsQuery.sanitize(query)
        return quoteDao.observeMatchCount(
            hasQuery = if (fts == null) 0 else 1,
            ftsQuery = fts.orEmpty(),
            tagIds = tagIds,
            tagCount = tagIds.size,
        )
    }

    override fun observeReelIds(mode: ReelMode, filter: ReelFilter): Flow<List<Long>> =
        when (mode) {
            ReelMode.SHUFFLE -> quoteDao.observeReelIdsForShuffle(
                filter.bookId, filter.tagIds, filter.tagIds.size,
            )
            ReelMode.LINEAR -> quoteDao.observeReelIdsForLinear(
                filter.bookId, filter.tagIds, filter.tagIds.size,
            )
        }

    override suspend fun quoteById(id: Long): Quote? = quoteDao.rowById(id)?.toDomain()

    override suspend fun saveValidated(draft: QuoteDraft, nowMillis: Long): SaveQuoteResult =
        db.withTransaction {
            val dedupeKey = DedupeKey.of(draft.text, draft.bookName, draft.pageNumber)
            val clash = quoteDao.entityByDedupeKey(dedupeKey)
            if (clash != null && clash.id != draft.id) return@withTransaction SaveQuoteResult.Duplicate

            val bookId = upsertBook(draft.bookName)
            val existing = draft.id?.let { quoteDao.entityById(it) }

            val quoteId = if (existing == null) {
                val inserted = quoteDao.insertIgnoring(
                    QuoteEntity(
                        text = draft.text,
                        bookId = bookId,
                        pageNumber = draft.pageNumber,
                        dedupeKey = dedupeKey,
                        createdAt = nowMillis,
                        updatedAt = nowMillis,
                    )
                )
                if (inserted == -1L) return@withTransaction SaveQuoteResult.Duplicate
                inserted
            } else {
                quoteDao.update(
                    existing.copy(
                        text = draft.text,
                        bookId = bookId,
                        pageNumber = draft.pageNumber,
                        dedupeKey = dedupeKey,
                        updatedAt = nowMillis,
                    )
                )
                existing.id
            }

            tagDao.unlinkAll(quoteId)
            draft.tags.forEach { name ->
                tagDao.insertIgnoring(TagEntity(name = name))
                val tagId = requireNotNull(tagDao.findByName(name)).id
                tagDao.link(QuoteTagCrossRef(quoteId = quoteId, tagId = tagId))
            }

            ftsDao.upsert(
                QuoteFtsEntity(
                    rowId = quoteId,
                    text = draft.text,
                    bookName = draft.bookName,
                    tagsFlat = draft.tags.joinToString(" "),
                )
            )

            bookDao.deleteOrphans()
            tagDao.deleteOrphans()
            SaveQuoteResult.Saved(quoteId)
        }

    override suspend fun delete(id: Long) = db.withTransaction {
        ftsDao.delete(id)
        quoteDao.deleteById(id)
        bookDao.deleteOrphans()
        tagDao.deleteOrphans()
    }

    override fun bookSuggestions(prefix: String): Flow<List<String>> = bookDao.suggest(prefix)

    override fun tagSuggestions(prefix: String): Flow<List<String>> = tagDao.suggest(prefix)

    override fun observeTagFilters(): Flow<List<TagFilter>> =
        tagDao.observeFilters().map { rows ->
            rows.map { TagFilter(id = it.id, name = it.name, usageCount = it.usageCount) }
        }

    override fun observeBooks(): Flow<List<Book>> =
        bookDao.observeAll().map { books -> books.map { Book(id = it.id, name = it.name) } }

    override fun observeQuoteCount(): Flow<Int> = quoteDao.observeCount()

    private suspend fun upsertBook(name: String): Long {
        bookDao.insertIgnoring(BookEntity(name = name))
        return requireNotNull(bookDao.findByName(name)) { "Book row missing after upsert: $name" }.id
    }
}
```

- [ ] **Step 11: Run to verify the repository tests pass**

Run: `./gradlew :app:testDebugUnitTest --tests '*QuoteRepositoryImplTest*'`
Expected: PASS, 12 tests.

- [ ] **Step 12: Checkpoint**

Run `./gradlew :app:testDebugUnitTest`. All green.

---

## Task 7: Reel state store, ObserveReelDeck, and Hilt wiring

**Files:**
- Create: `app/src/main/java/com/rzi/quotes/domain/repository/ReelStateStore.kt`
- Create: `app/src/main/java/com/rzi/quotes/data/prefs/ReelStateStoreImpl.kt`
- Create: `app/src/main/java/com/rzi/quotes/domain/usecase/ObserveReelDeck.kt`
- Create: `app/src/main/java/com/rzi/quotes/di/AppModule.kt`, `DatabaseModule.kt`, `RepositoryModule.kt`
- Create: `app/src/main/java/com/rzi/quotes/RziApplication.kt`
- Modify: `app/src/main/AndroidManifest.xml`
- Test: `app/src/test/java/com/rzi/quotes/domain/usecase/ObserveReelDeckTest.kt`

**Interfaces:**
- Consumes: `QuoteRepository` (Task 6), `Decks`/`Deck` (Task 3), `ReelPersistedState` (Task 3).
- Produces:
  - `interface ReelStateStore { val state: Flow<ReelPersistedState>; suspend fun update(transform: (ReelPersistedState) -> ReelPersistedState) }`
  - `data class ReelDeckState(deck: Deck, index: Int, mode: ReelMode, filter: ReelFilter)`
  - `ObserveReelDeck(repository, store)` — `operator fun invoke(): Flow<ReelDeckState>`
  - Hilt graph providing `RziDatabase`, all DAOs, `Clock`, `QuoteRepository`, `TransferRepository` (bound in Task 12), `ReelStateStore`, and an `@IoDispatcher CoroutineDispatcher`.

- [ ] **Step 1: Create the reel state port and its DataStore implementation**

`app/src/main/java/com/rzi/quotes/domain/repository/ReelStateStore.kt`:

```kotlin
package com.rzi.quotes.domain.repository

import com.rzi.quotes.domain.model.ReelPersistedState
import kotlinx.coroutines.flow.Flow

interface ReelStateStore {
    val state: Flow<ReelPersistedState>
    suspend fun update(transform: (ReelPersistedState) -> ReelPersistedState)
}
```

`app/src/main/java/com/rzi/quotes/data/prefs/ReelStateStoreImpl.kt`:

```kotlin
package com.rzi.quotes.data.prefs

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import com.rzi.quotes.domain.model.ReelFilter
import com.rzi.quotes.domain.model.ReelMode
import com.rzi.quotes.domain.model.ReelPersistedState
import com.rzi.quotes.domain.repository.ReelStateStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class ReelStateStoreImpl @Inject constructor(
    private val dataStore: DataStore<Preferences>,
) : ReelStateStore {

    override val state: Flow<ReelPersistedState> = dataStore.data.map { prefs ->
        ReelPersistedState(
            mode = prefs[MODE]?.let(ReelMode::valueOf) ?: ReelMode.SHUFFLE,
            baseSeed = prefs[BASE_SEED] ?: 0L,
            absoluteIndex = prefs[INDEX] ?: 0,
            currentQuoteId = prefs[QUOTE_ID]?.takeIf { prefs[HAS_QUOTE_ID] == true },
            filter = ReelFilter(
                bookId = prefs[BOOK_ID]?.takeIf { prefs[HAS_BOOK_ID] == true },
                tagIds = prefs[TAG_IDS]
                    ?.split(',')
                    ?.filter { it.isNotBlank() }
                    ?.map { it.toLong() }
                    .orEmpty(),
            ),
        )
    }

    override suspend fun update(transform: (ReelPersistedState) -> ReelPersistedState) {
        val next = transform(state.first())
        dataStore.edit { prefs ->
            prefs[MODE] = next.mode.name
            prefs[BASE_SEED] = next.baseSeed
            prefs[INDEX] = next.absoluteIndex
            prefs[HAS_QUOTE_ID] = next.currentQuoteId != null
            prefs[QUOTE_ID] = next.currentQuoteId ?: 0L
            prefs[HAS_BOOK_ID] = next.filter.bookId != null
            prefs[BOOK_ID] = next.filter.bookId ?: 0L
            prefs[TAG_IDS] = next.filter.tagIds.joinToString(",")
        }
    }

    private companion object {
        val MODE = stringPreferencesKey("reel_mode")
        val BASE_SEED = longPreferencesKey("reel_base_seed")
        val INDEX = intPreferencesKey("reel_index")
        val QUOTE_ID = longPreferencesKey("reel_quote_id")
        val HAS_QUOTE_ID = booleanPreferencesKey("reel_has_quote_id")
        val BOOK_ID = longPreferencesKey("reel_book_id")
        val HAS_BOOK_ID = booleanPreferencesKey("reel_has_book_id")
        val TAG_IDS = stringPreferencesKey("reel_tag_ids")
    }
}
```

The paired `HAS_*` booleans exist because Preferences DataStore has no nullable `Long`, and `0L` is a legitimate id value in theory — the flag keeps "no filter" distinct from "id 0".

- [ ] **Step 2: Write the failing ObserveReelDeck test**

`app/src/test/java/com/rzi/quotes/domain/usecase/ObserveReelDeckTest.kt`:

```kotlin
package com.rzi.quotes.domain.usecase

import androidx.paging.PagingData
import com.google.common.truth.Truth.assertThat
import com.rzi.quotes.domain.model.Book
import com.rzi.quotes.domain.model.Quote
import com.rzi.quotes.domain.model.QuoteDraft
import com.rzi.quotes.domain.model.ReelFilter
import com.rzi.quotes.domain.model.ReelMode
import com.rzi.quotes.domain.model.ReelPersistedState
import com.rzi.quotes.domain.model.SaveQuoteResult
import com.rzi.quotes.domain.model.TagFilter
import com.rzi.quotes.domain.repository.QuoteRepository
import com.rzi.quotes.domain.repository.ReelStateStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest
import org.junit.Test

class ObserveReelDeckTest {

    private val ids = MutableStateFlow(listOf(10L, 20L, 30L))

    private val repository = object : QuoteRepository {
        override fun observeReelIds(mode: ReelMode, filter: ReelFilter): Flow<List<Long>> = ids
        override fun pagedQuotes(query: String, tagIds: List<Long>): Flow<PagingData<Quote>> =
            flowOf(PagingData.empty())
        override fun observeMatchCount(query: String, tagIds: List<Long>): Flow<Int> = flowOf(0)
        override suspend fun quoteById(id: Long): Quote? = null
        override suspend fun saveValidated(draft: QuoteDraft, nowMillis: Long): SaveQuoteResult =
            SaveQuoteResult.Saved(0L)
        override suspend fun delete(id: Long) = Unit
        override fun bookSuggestions(prefix: String): Flow<List<String>> = flowOf(emptyList())
        override fun tagSuggestions(prefix: String): Flow<List<String>> = flowOf(emptyList())
        override fun observeTagFilters(): Flow<List<TagFilter>> = flowOf(emptyList())
        override fun observeBooks(): Flow<List<Book>> = flowOf(emptyList())
        override fun observeQuoteCount(): Flow<Int> = flowOf(0)
    }

    private class FakeStore(initial: ReelPersistedState) : ReelStateStore {
        val flow = MutableStateFlow(initial)
        override val state: Flow<ReelPersistedState> = flow
        override suspend fun update(transform: (ReelPersistedState) -> ReelPersistedState) {
            flow.value = transform(flow.value)
        }
    }

    @Test
    fun `deck is built from the persisted mode`() = runTest {
        val store = FakeStore(ReelPersistedState(mode = ReelMode.LINEAR, baseSeed = 1L))
        val state = ObserveReelDeck(repository, store)().first()

        assertThat(state.mode).isEqualTo(ReelMode.LINEAR)
        assertThat(state.deck.size).isEqualTo(3)
    }

    @Test
    fun `saved index is restored when it still points at the saved quote`() = runTest {
        val store = FakeStore(ReelPersistedState(mode = ReelMode.LINEAR, absoluteIndex = 1))
        store.flow.value = store.flow.value.copy(currentQuoteId = 20L)

        val state = ObserveReelDeck(repository, store)().first()

        assertThat(state.index).isEqualTo(1)
        assertThat(state.deck.idAt(state.index)).isEqualTo(20L)
    }

    @Test
    fun `index is relocated when the saved index no longer matches the saved quote`() = runTest {
        val store = FakeStore(
            ReelPersistedState(mode = ReelMode.LINEAR, absoluteIndex = 0, currentQuoteId = 30L)
        )

        val state = ObserveReelDeck(repository, store)().first()

        assertThat(state.deck.idAt(state.index)).isEqualTo(30L)
    }

    @Test
    fun `index falls back to zero when the saved quote is gone`() = runTest {
        val store = FakeStore(
            ReelPersistedState(mode = ReelMode.LINEAR, absoluteIndex = 2, currentQuoteId = 999L)
        )

        assertThat(ObserveReelDeck(repository, store)().first().index).isEqualTo(0)
    }

    @Test
    fun `empty id list yields an empty deck at index zero`() = runTest {
        ids.value = emptyList()
        val store = FakeStore(ReelPersistedState())

        val state = ObserveReelDeck(repository, store)().first()

        assertThat(state.deck.size).isEqualTo(0)
        assertThat(state.index).isEqualTo(0)
    }
}
```

- [ ] **Step 3: Run to verify it fails**

Run: `./gradlew :app:testDebugUnitTest --tests '*ObserveReelDeckTest*'`
Expected: FAIL — unresolved reference `ObserveReelDeck`.

- [ ] **Step 4: Implement ObserveReelDeck**

`app/src/main/java/com/rzi/quotes/domain/usecase/ObserveReelDeck.kt`:

```kotlin
package com.rzi.quotes.domain.usecase

import com.rzi.quotes.domain.model.ReelFilter
import com.rzi.quotes.domain.model.ReelMode
import com.rzi.quotes.domain.reel.Deck
import com.rzi.quotes.domain.reel.Decks
import com.rzi.quotes.domain.repository.QuoteRepository
import com.rzi.quotes.domain.repository.ReelStateStore
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.map
import javax.inject.Inject

data class ReelDeckState(
    val deck: Deck,
    val index: Int,
    val mode: ReelMode,
    val filter: ReelFilter,
)

/**
 * Combines the persisted reel state with the filtered id list into a positioned deck.
 *
 * The saved index is trusted only when it still resolves to the saved quote id; otherwise the quote
 * is located in the current ordering, and failing that the reel restarts at index 0. This is what
 * keeps the reader in place across a process restart, a mode switch, or an edit that reshuffled the
 * id set.
 */
class ObserveReelDeck @Inject constructor(
    private val repository: QuoteRepository,
    private val store: ReelStateStore,
) {

    @OptIn(ExperimentalCoroutinesApi::class)
    operator fun invoke(): Flow<ReelDeckState> = store.state
        .distinctUntilChanged()
        .flatMapLatest { persisted ->
            repository.observeReelIds(persisted.mode, persisted.filter).map { ids ->
                val deck = Decks.create(persisted.mode, ids, persisted.baseSeed)
                ReelDeckState(
                    deck = deck,
                    index = resolveIndex(deck, persisted.absoluteIndex, persisted.currentQuoteId),
                    mode = persisted.mode,
                    filter = persisted.filter,
                )
            }
        }

    private fun resolveIndex(deck: Deck, savedIndex: Int, savedQuoteId: Long?): Int {
        if (deck.size == 0 || savedQuoteId == null) return 0
        if (deck.idAt(savedIndex) == savedQuoteId) return savedIndex
        return deck.indexOfId(savedQuoteId, nearIndex = savedIndex) ?: 0
    }
}
```

- [ ] **Step 5: Run to verify it passes**

Run: `./gradlew :app:testDebugUnitTest --tests '*ObserveReelDeckTest*'`
Expected: PASS, 5 tests.

- [ ] **Step 6: Create the Hilt modules**

`app/src/main/java/com/rzi/quotes/di/AppModule.kt`:

```kotlin
package com.rzi.quotes.di

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.preferencesDataStoreFile
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import java.time.Clock
import javax.inject.Qualifier
import javax.inject.Singleton

@Qualifier
@Retention(AnnotationRetention.BINARY)
annotation class IoDispatcher

@Module
@InstallIn(SingletonComponent::class)
object AppModule {

    @Provides
    @Singleton
    fun clock(): Clock = Clock.systemUTC()

    @Provides
    @IoDispatcher
    fun ioDispatcher(): CoroutineDispatcher = Dispatchers.IO

    @Provides
    @Singleton
    fun preferences(@ApplicationContext context: Context): DataStore<Preferences> =
        PreferenceDataStoreFactory.create {
            context.preferencesDataStoreFile("rzi_reel_state")
        }
}
```

`app/src/main/java/com/rzi/quotes/di/DatabaseModule.kt`:

```kotlin
package com.rzi.quotes.di

import android.content.Context
import androidx.room.Room
import com.rzi.quotes.data.local.RziDatabase
import com.rzi.quotes.data.local.dao.BookDao
import com.rzi.quotes.data.local.dao.QuoteDao
import com.rzi.quotes.data.local.dao.QuoteFtsDao
import com.rzi.quotes.data.local.dao.TagDao
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object DatabaseModule {

    @Provides
    @Singleton
    fun database(@ApplicationContext context: Context): RziDatabase =
        Room.databaseBuilder(context, RziDatabase::class.java, RziDatabase.NAME).build()

    @Provides fun quoteDao(db: RziDatabase): QuoteDao = db.quoteDao()
    @Provides fun bookDao(db: RziDatabase): BookDao = db.bookDao()
    @Provides fun tagDao(db: RziDatabase): TagDao = db.tagDao()
    @Provides fun quoteFtsDao(db: RziDatabase): QuoteFtsDao = db.quoteFtsDao()
}
```

`app/src/main/java/com/rzi/quotes/di/RepositoryModule.kt`:

```kotlin
package com.rzi.quotes.di

import com.rzi.quotes.data.prefs.ReelStateStoreImpl
import com.rzi.quotes.data.repository.QuoteRepositoryImpl
import com.rzi.quotes.domain.repository.QuoteRepository
import com.rzi.quotes.domain.repository.ReelStateStore
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
interface RepositoryModule {

    @Binds @Singleton
    fun quoteRepository(impl: QuoteRepositoryImpl): QuoteRepository

    @Binds @Singleton
    fun reelStateStore(impl: ReelStateStoreImpl): ReelStateStore
}
```

`TransferRepository` is bound here too, in Task 12.

- [ ] **Step 7: Create the Application class and register it**

`app/src/main/java/com/rzi/quotes/RziApplication.kt`:

```kotlin
package com.rzi.quotes

import android.app.Application
import dagger.hilt.android.HiltAndroidApp

@HiltAndroidApp
class RziApplication : Application()
```

In `AndroidManifest.xml`, add `android:name=".RziApplication"` to the `<application>` tag. Annotate the generated activity with `@AndroidEntryPoint`.

- [ ] **Step 8: Verify the Hilt graph compiles**

Run:

```bash
./gradlew :app:assembleDebug
```

Expected: `BUILD SUCCESSFUL`. A Hilt error naming a missing binding means a `@Provides`/`@Binds` above was mistyped — fix it rather than adding `@Suppress`.

- [ ] **Step 9: Checkpoint**

Run `./gradlew :app:testDebugUnitTest`. All green.

---

## Task 8: Theme and the two-destination navigation shell

**Files:**
- Create: `app/src/main/java/com/rzi/quotes/ui/theme/Color.kt`, `Type.kt`, `Theme.kt`
- Create: `app/src/main/java/com/rzi/quotes/ui/navigation/Destination.kt`, `RziNavHost.kt`
- Create: `app/src/main/java/com/rzi/quotes/ui/components/EmptyState.kt`
- Modify: the generated `MainActivity.kt`

**Interfaces:**
- Consumes: nothing from earlier tasks.
- Produces:
  - `RziTheme(content: @Composable () -> Unit)` — dynamic color on API 31+, seed scheme below.
  - `quoteTextStyle(charCount: Int): TextStyle` and `QUOTE_MAX_LINES` in `Type.kt`.
  - `sealed interface Destination { @Serializable data object Reel; @Serializable data object Library }`
  - `RziNavHost()` — the `Scaffold` + `NavigationBar` shell.
  - `EmptyState(title, actionLabel, onAction, secondaryLabel, onSecondary)`.

**Deviation from the spec, deliberate:** the spec called for bundling Literata. This plan uses the platform serif (`FontFamily.Serif`, Noto Serif on Android) instead — visually equivalent at reading sizes, zero bundled bytes, no build-time font download, identically offline. If a bundled face is wanted later, drop `literata_regular.ttf` into `res/font/` and change one line in `Type.kt`.

- [ ] **Step 1: Create the color scheme**

`app/src/main/java/com/rzi/quotes/ui/theme/Color.kt`:

```kotlin
package com.rzi.quotes.ui.theme

import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.ui.graphics.Color

// Tonal scheme derived from seed #8A5A2B (warm leather brown). Used when dynamic color is
// unavailable (API < 31).
val RziLightScheme = lightColorScheme(
    primary = Color(0xFF8A5325), onPrimary = Color(0xFFFFFFFF),
    primaryContainer = Color(0xFFFFDCBE), onPrimaryContainer = Color(0xFF2E1500),
    secondary = Color(0xFF725A42), onSecondary = Color(0xFFFFFFFF),
    secondaryContainer = Color(0xFFFEDCBE), onSecondaryContainer = Color(0xFF291805),
    tertiary = Color(0xFF58633A), onTertiary = Color(0xFFFFFFFF),
    tertiaryContainer = Color(0xFFDCE8B4), onTertiaryContainer = Color(0xFF161E00),
    error = Color(0xFFBA1A1A), onError = Color(0xFFFFFFFF),
    errorContainer = Color(0xFFFFDAD6), onErrorContainer = Color(0xFF410002),
    background = Color(0xFFFFF8F5), onBackground = Color(0xFF201A17),
    surface = Color(0xFFFFF8F5), onSurface = Color(0xFF201A17),
    surfaceVariant = Color(0xFFF2DFD1), onSurfaceVariant = Color(0xFF51443B),
    outline = Color(0xFF837469), outlineVariant = Color(0xFFD5C3B5),
    surfaceContainerLowest = Color(0xFFFFFFFF), surfaceContainerLow = Color(0xFFFFF1E7),
    surfaceContainer = Color(0xFFFCEBE0), surfaceContainerHigh = Color(0xFFF6E5DA),
    surfaceContainerHighest = Color(0xFFF0DFD5),
)

val RziDarkScheme = darkColorScheme(
    primary = Color(0xFFFFB876), onPrimary = Color(0xFF4C2700),
    primaryContainer = Color(0xFF6C3C0D), onPrimaryContainer = Color(0xFFFFDCBE),
    secondary = Color(0xFFE1C1A4), onSecondary = Color(0xFF402C18),
    secondaryContainer = Color(0xFF58412C), onSecondaryContainer = Color(0xFFFEDCBE),
    tertiary = Color(0xFFC0CC9A), onTertiary = Color(0xFF2B3410),
    tertiaryContainer = Color(0xFF414B24), onTertiaryContainer = Color(0xFFDCE8B4),
    error = Color(0xFFFFB4AB), onError = Color(0xFF690005),
    errorContainer = Color(0xFF93000A), onErrorContainer = Color(0xFFFFDAD6),
    background = Color(0xFF181210), onBackground = Color(0xFFECE0DA),
    surface = Color(0xFF181210), onSurface = Color(0xFFECE0DA),
    surfaceVariant = Color(0xFF51443B), onSurfaceVariant = Color(0xFFD5C3B5),
    outline = Color(0xFF9D8E82), outlineVariant = Color(0xFF51443B),
    surfaceContainerLowest = Color(0xFF120D0B), surfaceContainerLow = Color(0xFF201A17),
    surfaceContainer = Color(0xFF241E1B), surfaceContainerHigh = Color(0xFF2F2825),
    surfaceContainerHighest = Color(0xFF3A322F),
)
```

- [ ] **Step 2: Create typography and the quote text scale**

`app/src/main/java/com/rzi/quotes/ui/theme/Type.kt`:

```kotlin
package com.rzi.quotes.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.runtime.Composable
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily

val RziTypography = Typography()

const val QUOTE_MAX_LINES = 14

/**
 * Quote text steps down in size as it gets longer, so short aphorisms fill the page and long
 * passages still fit. Thresholds come from the spec.
 */
@Composable
@ReadOnlyComposable
fun quoteTextStyle(charCount: Int): TextStyle {
    val base = when {
        charCount <= 120 -> MaterialTheme.typography.displaySmall
        charCount <= 300 -> MaterialTheme.typography.headlineSmall
        charCount <= 600 -> MaterialTheme.typography.titleLarge
        else -> MaterialTheme.typography.bodyLarge
    }
    return base.copy(fontFamily = FontFamily.Serif)
}
```

- [ ] **Step 3: Create the theme**

`app/src/main/java/com/rzi/quotes/ui/theme/Theme.kt`:

```kotlin
package com.rzi.quotes.ui.theme

import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalContext

@Composable
fun RziTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit,
) {
    val context = LocalContext.current
    val scheme = when {
        Build.VERSION.SDK_INT >= Build.VERSION_CODES.S ->
            if (darkTheme) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
        darkTheme -> RziDarkScheme
        else -> RziLightScheme
    }
    MaterialTheme(colorScheme = scheme, typography = RziTypography, content = content)
}
```

- [ ] **Step 4: Create the shared empty state**

`app/src/main/java/com/rzi/quotes/ui/components/EmptyState.kt`:

```kotlin
package com.rzi.quotes.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

@Composable
fun EmptyState(
    title: String,
    modifier: Modifier = Modifier,
    actionLabel: String? = null,
    onAction: (() -> Unit)? = null,
    secondaryLabel: String? = null,
    onSecondary: (() -> Unit)? = null,
) {
    Column(
        modifier = modifier.fillMaxSize().padding(32.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp, Alignment.CenterVertically),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(text = title, style = MaterialTheme.typography.titleLarge)
        if (actionLabel != null && onAction != null) {
            Button(onClick = onAction) { Text(actionLabel) }
        }
        if (secondaryLabel != null && onSecondary != null) {
            TextButton(onClick = onSecondary) { Text(secondaryLabel) }
        }
    }
}
```

- [ ] **Step 5: Add type-safe navigation routes**

In `libs.versions.toml` add under `[plugins]`:
`kotlin-serialization = { id = "org.jetbrains.kotlin.plugin.serialization", version.ref = "kotlin" }`
(reusing the template's existing `kotlin` version ref), and under `[libraries]`:
`kotlinx-serialization-json = { module = "org.jetbrains.kotlinx:kotlinx-serialization-json", version = "1.7.3" }`.
Apply `alias(libs.plugins.kotlin.serialization)` in `app/build.gradle.kts` and add
`implementation(libs.kotlinx.serialization.json)`.

`app/src/main/java/com/rzi/quotes/ui/navigation/Destination.kt`:

```kotlin
package com.rzi.quotes.ui.navigation

import kotlinx.serialization.Serializable

sealed interface Destination {
    @Serializable data object Reel : Destination
    @Serializable data object Library : Destination
}
```

- [ ] **Step 6: Stub the two screens so the shell compiles**

`app/src/main/java/com/rzi/quotes/ui/reel/ReelScreen.kt`:

```kotlin
package com.rzi.quotes.ui.reel

import androidx.compose.runtime.Composable
import com.rzi.quotes.ui.components.EmptyState

@Composable
fun ReelScreen(onAddQuote: () -> Unit) {
    EmptyState(title = "No quotes yet", actionLabel = "Add your first quote", onAction = onAddQuote)
}
```

`app/src/main/java/com/rzi/quotes/ui/library/LibraryScreen.kt`:

```kotlin
package com.rzi.quotes.ui.library

import androidx.compose.runtime.Composable
import com.rzi.quotes.ui.components.EmptyState

@Composable
fun LibraryScreen() {
    EmptyState(title = "Nothing here yet")
}
```

Both are replaced by the real implementations in Tasks 10 and 11.

- [ ] **Step 7: Create the navigation shell**

`app/src/main/java/com/rzi/quotes/ui/navigation/RziNavHost.kt`:

```kotlin
package com.rzi.quotes.ui.navigation

import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AutoStories
import androidx.compose.material.icons.filled.CollectionsBookmark
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import com.rzi.quotes.ui.library.LibraryScreen
import com.rzi.quotes.ui.reel.ReelScreen

@Composable
fun RziNavHost() {
    val navController = rememberNavController()
    val backStackEntry by navController.currentBackStackEntryAsState()
    val currentRoute = backStackEntry?.destination?.route

    Scaffold(
        bottomBar = {
            NavigationBar {
                NavigationBarItem(
                    selected = currentRoute?.contains("Reel") == true,
                    onClick = {
                        navController.navigate(Destination.Reel) { launchSingleTop = true }
                    },
                    icon = { Icon(Icons.Filled.AutoStories, contentDescription = null) },
                    label = { Text("Reel") },
                )
                NavigationBarItem(
                    selected = currentRoute?.contains("Library") == true,
                    onClick = {
                        navController.navigate(Destination.Library) { launchSingleTop = true }
                    },
                    icon = { Icon(Icons.Filled.CollectionsBookmark, contentDescription = null) },
                    label = { Text("Library") },
                )
            }
        },
    ) { padding ->
        NavHost(
            navController = navController,
            startDestination = Destination.Reel,
            modifier = Modifier.padding(padding),
        ) {
            composable<Destination.Reel> {
                ReelScreen(onAddQuote = { navController.navigate(Destination.Library) })
            }
            composable<Destination.Library> { LibraryScreen() }
        }
    }
}
```

- [ ] **Step 8: Wire the activity**

Replace the generated `MainActivity.kt` body with:

```kotlin
package com.rzi.quotes

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import com.rzi.quotes.ui.navigation.RziNavHost
import com.rzi.quotes.ui.theme.RziTheme
import dagger.hilt.android.AndroidEntryPoint

@AndroidEntryPoint
class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        setContent { RziTheme { RziNavHost() } }
    }
}
```

- [ ] **Step 9: Build, install, and look at it**

Run:

```bash
./gradlew :app:installDebug
```

Launch the app. Expected: two bottom-nav items; "No quotes yet" on Reel; "Nothing here yet" on Library; tapping between them works; the system dark theme is honoured.

- [ ] **Step 10: Checkpoint**

Run `./gradlew :app:testDebugUnitTest`. All green.

---

## Task 9: Quote editor sheet

**Files:**
- Create: `app/src/main/java/com/rzi/quotes/ui/library/editor/QuoteEditorUiState.kt`
- Create: `app/src/main/java/com/rzi/quotes/ui/library/editor/QuoteEditorViewModel.kt`
- Create: `app/src/main/java/com/rzi/quotes/ui/library/editor/QuoteEditorSheet.kt`

**Interfaces:**
- Consumes: `SaveQuote`, `DeleteQuote`, `BookSuggestions`, `TagSuggestions`, `QuoteRepository.quoteById` (Task 6).
- Produces: `QuoteEditorSheet(quoteId: Long?, onDismiss: () -> Unit, onMessage: (String) -> Unit)` — hosted by Task 10.

- [ ] **Step 1: Create the UI state**

`QuoteEditorUiState.kt`:

```kotlin
package com.rzi.quotes.ui.library.editor

import com.rzi.quotes.domain.model.ValidationErrors

data class QuoteEditorUiState(
    val quoteId: Long? = null,
    val text: String = "",
    val bookName: String = "",
    val pageText: String = "",
    val tags: List<String> = emptyList(),
    val tagInput: String = "",
    val bookSuggestions: List<String> = emptyList(),
    val tagSuggestions: List<String> = emptyList(),
    val errors: ValidationErrors = ValidationErrors(),
    val isSaving: Boolean = false,
) {
    val isEditing: Boolean get() = quoteId != null
    val title: String get() = if (isEditing) "Edit quote" else "New quote"
    val canSave: Boolean get() = text.isNotBlank() && bookName.isNotBlank() && !isSaving
}

sealed interface EditorEvent {
    data object Saved : EditorEvent
    data object Deleted : EditorEvent
    data class Message(val text: String) : EditorEvent
}
```

- [ ] **Step 2: Create the ViewModel**

`QuoteEditorViewModel.kt`:

```kotlin
package com.rzi.quotes.ui.library.editor

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.rzi.quotes.domain.model.QuoteDraft
import com.rzi.quotes.domain.model.SaveQuoteResult
import com.rzi.quotes.domain.repository.QuoteRepository
import com.rzi.quotes.domain.usecase.BookSuggestions
import com.rzi.quotes.domain.usecase.DeleteQuote
import com.rzi.quotes.domain.usecase.SaveQuote
import com.rzi.quotes.domain.usecase.TagSuggestions
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class QuoteEditorViewModel @Inject constructor(
    private val repository: QuoteRepository,
    private val saveQuote: SaveQuote,
    private val deleteQuote: DeleteQuote,
    private val bookSuggestions: BookSuggestions,
    private val tagSuggestions: TagSuggestions,
) : ViewModel() {

    private val _state = MutableStateFlow(QuoteEditorUiState())
    val state: StateFlow<QuoteEditorUiState> = _state.asStateFlow()

    private val _events = Channel<EditorEvent>(Channel.BUFFERED)
    val events = _events.receiveAsFlow()

    private var loaded = false

    fun load(quoteId: Long?) {
        if (loaded) return
        loaded = true
        viewModelScope.launch {
            val quote = quoteId?.let { repository.quoteById(it) }
            _state.value = QuoteEditorUiState(
                quoteId = quoteId,
                text = quote?.text.orEmpty(),
                bookName = quote?.bookName.orEmpty(),
                pageText = quote?.pageNumber?.toString().orEmpty(),
                tags = quote?.tags.orEmpty(),
            )
            refreshBookSuggestions("")
            refreshTagSuggestions("")
        }
    }

    fun onTextChange(value: String) {
        _state.value = _state.value.copy(
            text = value,
            errors = _state.value.errors.copy(text = null),
        )
    }

    fun onBookNameChange(value: String) {
        _state.value = _state.value.copy(
            bookName = value,
            errors = _state.value.errors.copy(bookName = null),
        )
        viewModelScope.launch { refreshBookSuggestions(value) }
    }

    fun onPageChange(value: String) {
        if (value.any { !it.isDigit() }) return
        _state.value = _state.value.copy(
            pageText = value,
            errors = _state.value.errors.copy(pageNumber = null),
        )
    }

    fun onTagInputChange(value: String) {
        if (value.endsWith(",")) {
            commitTag(value.dropLast(1))
            return
        }
        _state.value = _state.value.copy(tagInput = value)
        viewModelScope.launch { refreshTagSuggestions(value) }
    }

    fun commitTag(raw: String = _state.value.tagInput) {
        val name = raw.replace(",", "").trim()
        if (name.isEmpty()) {
            _state.value = _state.value.copy(tagInput = "")
            return
        }
        val existing = _state.value.tags
        val next = if (existing.any { it.equals(name, ignoreCase = true) }) existing else existing + name
        _state.value = _state.value.copy(tags = next, tagInput = "")
        viewModelScope.launch { refreshTagSuggestions("") }
    }

    fun removeTag(name: String) {
        _state.value = _state.value.copy(tags = _state.value.tags - name)
    }

    fun save() {
        val current = _state.value
        _state.value = current.copy(isSaving = true)
        viewModelScope.launch {
            val result = saveQuote(
                QuoteDraft(
                    id = current.quoteId,
                    text = current.text,
                    bookName = current.bookName,
                    pageNumber = current.pageText.toIntOrNull(),
                    tags = current.tags,
                )
            )
            _state.value = _state.value.copy(isSaving = false)
            when (result) {
                is SaveQuoteResult.Saved -> _events.send(EditorEvent.Saved)
                is SaveQuoteResult.Duplicate ->
                    _events.send(EditorEvent.Message("This quote already exists"))
                is SaveQuoteResult.Invalid ->
                    _state.value = _state.value.copy(errors = result.errors)
            }
        }
    }

    fun delete() {
        val id = _state.value.quoteId ?: return
        viewModelScope.launch {
            deleteQuote(id)
            _events.send(EditorEvent.Deleted)
        }
    }

    private suspend fun refreshBookSuggestions(prefix: String) {
        _state.value = _state.value.copy(bookSuggestions = bookSuggestions(prefix).first())
    }

    private suspend fun refreshTagSuggestions(prefix: String) {
        val already = _state.value.tags.map { it.lowercase() }.toSet()
        _state.value = _state.value.copy(
            tagSuggestions = tagSuggestions(prefix).first().filter { it.lowercase() !in already },
        )
    }
}
```

The `loaded` flag exists because `QuoteEditorSheet` is created fresh per open (Task 10 keys it on the sheet's visibility), so `load` should populate exactly once and never clobber the user's typing on recomposition.

- [ ] **Step 3: Create the sheet**

`QuoteEditorSheet.kt`:

```kotlin
package com.rzi.quotes.ui.library.editor

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.InputChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.SuggestionChip
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun QuoteEditorSheet(
    quoteId: Long?,
    onDismiss: () -> Unit,
    onMessage: (String) -> Unit,
    viewModel: QuoteEditorViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)

    LaunchedEffect(quoteId) { viewModel.load(quoteId) }
    LaunchedEffect(Unit) {
        viewModel.events.collect { event ->
            when (event) {
                EditorEvent.Saved, EditorEvent.Deleted -> onDismiss()
                is EditorEvent.Message -> onMessage(event.text)
            }
        }
    }

    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = sheetState) {
        Column(
            modifier = Modifier
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp)
                .padding(bottom = 32.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                IconButton(onClick = onDismiss) {
                    Icon(Icons.Filled.Close, contentDescription = "Cancel")
                }
                Text(
                    text = state.title,
                    style = MaterialTheme.typography.titleMedium,
                    modifier = Modifier.padding(start = 4.dp),
                )
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.End,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    if (state.isEditing) {
                        IconButton(onClick = viewModel::delete) {
                            Icon(Icons.Filled.Delete, contentDescription = "Delete quote")
                        }
                    }
                    TextButton(onClick = viewModel::save, enabled = state.canSave) { Text("Save") }
                }
            }

            OutlinedTextField(
                value = state.text,
                onValueChange = viewModel::onTextChange,
                label = { Text("Quote") },
                minLines = 5,
                isError = state.errors.text != null,
                supportingText = state.errors.text?.let { message -> { Text(message) } },
                modifier = Modifier.fillMaxWidth(),
            )

            BookNameField(state = state, onValueChange = viewModel::onBookNameChange)

            OutlinedTextField(
                value = state.pageText,
                onValueChange = viewModel::onPageChange,
                label = { Text("Page number (optional)") },
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                isError = state.errors.pageNumber != null,
                supportingText = state.errors.pageNumber?.let { message -> { Text(message) } },
                modifier = Modifier.fillMaxWidth(),
            )

            if (state.tags.isNotEmpty()) {
                LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    items(state.tags.size) { index ->
                        val tag = state.tags[index]
                        InputChip(
                            selected = false,
                            onClick = { viewModel.removeTag(tag) },
                            label = { Text(tag) },
                            trailingIcon = {
                                Icon(Icons.Filled.Close, contentDescription = "Remove $tag")
                            },
                        )
                    }
                }
            }

            OutlinedTextField(
                value = state.tagInput,
                onValueChange = viewModel::onTagInputChange,
                label = { Text("Tags (optional)") },
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
                keyboardActions = KeyboardActions(onDone = { viewModel.commitTag() }),
                modifier = Modifier.fillMaxWidth(),
            )

            if (state.tagSuggestions.isNotEmpty()) {
                LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    items(state.tagSuggestions.size) { index ->
                        val suggestion = state.tagSuggestions[index]
                        SuggestionChip(
                            onClick = { viewModel.commitTag(suggestion) },
                            label = { Text(suggestion) },
                        )
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun BookNameField(state: QuoteEditorUiState, onValueChange: (String) -> Unit) {
    var expanded by remember { mutableStateOf(false) }
    val matches = state.bookSuggestions.filter {
        state.bookName.isBlank() || it.contains(state.bookName, ignoreCase = true)
    }
    val showMenu = expanded && matches.isNotEmpty()

    ExposedDropdownMenuBox(expanded = showMenu, onExpandedChange = { expanded = it }) {
        OutlinedTextField(
            value = state.bookName,
            onValueChange = { onValueChange(it); expanded = true },
            label = { Text("Book") },
            isError = state.errors.bookName != null,
            supportingText = state.errors.bookName?.let { message -> { Text(message) } },
            modifier = Modifier.fillMaxWidth().menuAnchor(),
        )
        ExposedDropdownMenu(expanded = showMenu, onDismissRequest = { expanded = false }) {
            matches.forEach { name ->
                DropdownMenuItem(
                    text = { Text(name) },
                    onClick = { onValueChange(name); expanded = false },
                    contentPadding = ExposedDropdownMenuDefaults.ItemContentPadding,
                )
            }
        }
    }
}
```

- [ ] **Step 4: Build**

Run: `./gradlew :app:assembleDebug`
Expected: `BUILD SUCCESSFUL`.

If `Modifier.menuAnchor()` is reported as deprecated, switch to
`Modifier.menuAnchor(MenuAnchorType.PrimaryEditable, enabled = true)` and import
`androidx.compose.material3.MenuAnchorType` — newer material3 versions require the typed overload,
older ones only have the no-argument form. Behaviour is identical here.

If `items(count)` fails to resolve on `LazyRow`, add `import androidx.compose.foundation.lazy.items` and index the list directly.

- [ ] **Step 5: Checkpoint**

Run `./gradlew :app:testDebugUnitTest`. All green. The editor gets its hands-on verification in Task 10, once it has a host screen.

---

## Task 10: Library screen with search, tag filters, and swipe-delete

**Files:**
- Create: `app/src/main/java/com/rzi/quotes/ui/library/LibraryUiState.kt`
- Create: `app/src/main/java/com/rzi/quotes/ui/library/LibraryViewModel.kt`
- Create: `app/src/main/java/com/rzi/quotes/ui/library/QuoteRowItem.kt`
- Modify: `app/src/main/java/com/rzi/quotes/ui/library/LibraryScreen.kt` (replacing the Task 8 stub)
- Modify: `app/build.gradle.kts` (add `paging-compose`)

**Interfaces:**
- Consumes: `SearchQuotes`, `DeleteQuote`, `SaveQuote`, `QuoteRepository` (Task 6); `QuoteEditorSheet` (Task 9).
- Produces: the real `LibraryScreen`, with `onImport`/`onExport` lambda parameters that Task 13 replaces with document-picker launchers.

- [ ] **Step 1: Add the paging-compose dependency**

Add to `libs.versions.toml` `[libraries]`:
`paging-compose = { module = "androidx.paging:paging-compose", version.ref = "paging" }`
and `implementation(libs.paging.compose)` to `app/build.gradle.kts`.

- [ ] **Step 2: Create the UI state**

`LibraryUiState.kt`:

```kotlin
package com.rzi.quotes.ui.library

import com.rzi.quotes.domain.model.TagFilter

data class LibraryUiState(
    val query: String = "",
    val tagFilters: List<TagFilter> = emptyList(),
    val selectedTagIds: List<Long> = emptyList(),
    val matchCount: Int = 0,
    val totalCount: Int = 0,
    val editorQuoteId: Long? = null,
    val isEditorOpen: Boolean = false,
    val isTransferInProgress: Boolean = false,
) {
    val isSearching: Boolean get() = query.isNotBlank() || selectedTagIds.isNotEmpty()

    val countLabel: String get() = if (isSearching) {
        "$matchCount ${if (matchCount == 1) "result" else "results"}"
    } else {
        "$totalCount ${if (totalCount == 1) "quote" else "quotes"}"
    }
}
```

`isTransferInProgress` is unused until Task 13; it lives here so that task touches one file fewer.

- [ ] **Step 3: Create the ViewModel**

`LibraryViewModel.kt`:

```kotlin
package com.rzi.quotes.ui.library

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.paging.PagingData
import androidx.paging.cachedIn
import com.rzi.quotes.domain.model.Quote
import com.rzi.quotes.domain.model.QuoteDraft
import com.rzi.quotes.domain.repository.QuoteRepository
import com.rzi.quotes.domain.usecase.DeleteQuote
import com.rzi.quotes.domain.usecase.SaveQuote
import com.rzi.quotes.domain.usecase.SearchQuotes
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

private data class SearchKey(val query: String, val tagIds: List<Long>)

@OptIn(ExperimentalCoroutinesApi::class, FlowPreview::class)
@HiltViewModel
class LibraryViewModel @Inject constructor(
    private val repository: QuoteRepository,
    private val searchQuotes: SearchQuotes,
    private val deleteQuote: DeleteQuote,
    private val saveQuote: SaveQuote,
) : ViewModel() {

    private val _state = MutableStateFlow(LibraryUiState())
    val state: StateFlow<LibraryUiState> = _state.asStateFlow()

    private val _messages = Channel<String>(Channel.BUFFERED)
    val messages = _messages.receiveAsFlow()

    /** Held so Undo can reinsert the last deleted quote. */
    private var lastDeleted: Quote? = null

    private val searchKey: Flow<SearchKey> = _state
        .map { SearchKey(it.query, it.selectedTagIds) }
        .distinctUntilChanged()
        .debounce(250)

    val quotes: Flow<PagingData<Quote>> = searchKey
        .flatMapLatest { key -> searchQuotes(key.query, key.tagIds) }
        .cachedIn(viewModelScope)

    init {
        repository.observeTagFilters()
            .onEach { filters -> _state.value = _state.value.copy(tagFilters = filters) }
            .launchIn(viewModelScope)

        repository.observeQuoteCount()
            .onEach { total -> _state.value = _state.value.copy(totalCount = total) }
            .launchIn(viewModelScope)

        searchKey
            .flatMapLatest { key -> repository.observeMatchCount(key.query, key.tagIds) }
            .onEach { count -> _state.value = _state.value.copy(matchCount = count) }
            .launchIn(viewModelScope)
    }

    fun onQueryChange(value: String) {
        _state.value = _state.value.copy(query = value)
    }

    fun onTagToggle(tagId: Long) {
        val selected = _state.value.selectedTagIds
        _state.value = _state.value.copy(
            selectedTagIds = if (tagId in selected) selected - tagId else selected + tagId,
        )
    }

    fun openEditor(quoteId: Long?) {
        _state.value = _state.value.copy(editorQuoteId = quoteId, isEditorOpen = true)
    }

    fun closeEditor() {
        _state.value = _state.value.copy(isEditorOpen = false, editorQuoteId = null)
    }

    fun delete(quote: Quote) {
        lastDeleted = quote
        viewModelScope.launch {
            deleteQuote(quote.id)
            _messages.send(DELETE_MESSAGE)
        }
    }

    fun undoDelete() {
        val quote = lastDeleted ?: return
        lastDeleted = null
        viewModelScope.launch {
            saveQuote(
                QuoteDraft(
                    text = quote.text,
                    bookName = quote.bookName,
                    pageNumber = quote.pageNumber,
                    tags = quote.tags,
                )
            )
        }
    }

    fun showMessage(text: String) {
        viewModelScope.launch { _messages.send(text) }
    }

    companion object {
        const val DELETE_MESSAGE = "Deleted"
    }
}
```

Undo reinserts as a new row, so the quote's id and `createdAt` change. That is deliberate: the reader gets the quote back, and nothing else in the app depends on a quote id surviving a delete.

- [ ] **Step 4: Create the row item**

`QuoteRowItem.kt`:

```kotlin
package com.rzi.quotes.ui.library

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Card
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.rzi.quotes.domain.model.Quote

@Composable
fun QuoteRowItem(
    quote: Quote,
    query: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Card(
        onClick = onClick,
        modifier = modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 4.dp),
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Text(
                text = highlight(quote.text, query),
                style = MaterialTheme.typography.bodyLarge,
                maxLines = 3,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                text = buildString {
                    append(quote.bookName)
                    quote.pageNumber?.let { append(" · p. $it") }
                },
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            if (quote.tags.isNotEmpty()) {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    quote.tags.take(3).forEach { tag ->
                        AssistChip(onClick = onClick, label = { Text(tag) })
                    }
                }
            }
        }
    }
}

/** Bolds the first occurrence of each query token so the reason for a match is visible. */
private fun highlight(text: String, query: String): AnnotatedString {
    val tokens = query.trim().split(' ').filter { it.length > 1 }
    if (tokens.isEmpty()) return AnnotatedString(text)
    return buildAnnotatedString {
        append(text)
        tokens.forEach { token ->
            val start = text.indexOf(token, ignoreCase = true)
            if (start >= 0) {
                addStyle(SpanStyle(fontWeight = FontWeight.Bold), start, start + token.length)
            }
        }
    }
}
```

- [ ] **Step 5: Replace the LibraryScreen stub**

`LibraryScreen.kt`:

```kotlin
package com.rzi.quotes.ui.library

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material3.DockedSearchBar
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarResult
import androidx.compose.material3.SwipeToDismissBox
import androidx.compose.material3.SwipeToDismissBoxValue
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.rememberSwipeToDismissBoxState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.paging.compose.collectAsLazyPagingItems
import com.rzi.quotes.ui.components.EmptyState
import com.rzi.quotes.ui.library.editor.QuoteEditorSheet

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LibraryScreen(
    onImport: () -> Unit = {},
    onExport: () -> Unit = {},
    viewModel: LibraryViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val quotes = viewModel.quotes.collectAsLazyPagingItems()
    val snackbarHostState = remember { SnackbarHostState() }
    var overflowOpen by remember { mutableStateOf(false) }

    LaunchedEffect(Unit) {
        viewModel.messages.collect { message ->
            val result = snackbarHostState.showSnackbar(
                message = message,
                actionLabel = if (message == LibraryViewModel.DELETE_MESSAGE) "Undo" else null,
                duration = SnackbarDuration.Short,
            )
            if (result == SnackbarResult.ActionPerformed) viewModel.undoDelete()
        }
    }

    Scaffold(
        snackbarHost = { SnackbarHost(snackbarHostState) },
        topBar = {
            TopAppBar(
                title = { Text("Library") },
                actions = {
                    IconButton(onClick = { overflowOpen = true }) {
                        Icon(Icons.Filled.MoreVert, contentDescription = "More")
                    }
                    DropdownMenu(
                        expanded = overflowOpen,
                        onDismissRequest = { overflowOpen = false },
                    ) {
                        DropdownMenuItem(
                            text = { Text("Import database") },
                            onClick = { overflowOpen = false; onImport() },
                        )
                        DropdownMenuItem(
                            text = { Text("Export database") },
                            onClick = { overflowOpen = false; onExport() },
                        )
                    }
                },
            )
        },
        floatingActionButton = {
            FloatingActionButton(onClick = { viewModel.openEditor(null) }) {
                Icon(Icons.Filled.Add, contentDescription = "Add quote")
            }
        },
    ) { padding ->
        Column(modifier = Modifier.fillMaxSize().padding(padding)) {
            DockedSearchBar(
                query = state.query,
                onQueryChange = viewModel::onQueryChange,
                onSearch = {},
                active = false,
                onActiveChange = {},
                placeholder = { Text("Search quotes, books, tags") },
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
            ) {}

            if (state.isTransferInProgress) {
                LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
            }

            if (state.tagFilters.isNotEmpty()) {
                LazyRow(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    contentPadding = PaddingValues(horizontal = 16.dp),
                ) {
                    items(state.tagFilters.size) { index ->
                        val filter = state.tagFilters[index]
                        FilterChip(
                            selected = filter.id in state.selectedTagIds,
                            onClick = { viewModel.onTagToggle(filter.id) },
                            label = { Text("${filter.name} (${filter.usageCount})") },
                        )
                    }
                }
            }

            Text(
                text = state.countLabel,
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
            )

            when {
                state.totalCount == 0 -> EmptyState(
                    title = "Nothing here yet",
                    actionLabel = "Add a quote",
                    onAction = { viewModel.openEditor(null) },
                    secondaryLabel = "Import a database",
                    onSecondary = onImport,
                )

                quotes.itemCount == 0 && state.isSearching ->
                    EmptyState(title = "No matches for '${state.query}'")

                else -> LazyColumn(modifier = Modifier.fillMaxSize()) {
                    items(quotes.itemCount) { index ->
                        val quote = quotes[index] ?: return@items
                        val dismissState = rememberSwipeToDismissBoxState(
                            confirmValueChange = { value ->
                                if (value == SwipeToDismissBoxValue.EndToStart) {
                                    viewModel.delete(quote)
                                    true
                                } else {
                                    false
                                }
                            },
                        )
                        SwipeToDismissBox(
                            state = dismissState,
                            enableDismissFromStartToEnd = false,
                            backgroundContent = {
                                Box(
                                    modifier = Modifier
                                        .fillMaxSize()
                                        .background(MaterialTheme.colorScheme.errorContainer),
                                )
                            },
                        ) {
                            QuoteRowItem(
                                quote = quote,
                                query = state.query,
                                onClick = { viewModel.openEditor(quote.id) },
                            )
                        }
                    }
                }
            }
        }

        if (state.isEditorOpen) {
            QuoteEditorSheet(
                quoteId = state.editorQuoteId,
                onDismiss = viewModel::closeEditor,
                onMessage = viewModel::showMessage,
            )
        }
    }
}
```

Since `LibraryScreen` now takes parameters with defaults, `RziNavHost`'s `composable<Destination.Library> { LibraryScreen() }` still compiles unchanged.

- [ ] **Step 6: Build, install, and verify by hand**

Run:

```bash
./gradlew :app:installDebug
```

On the device, verify each of these:
- FAB opens the editor; saving with text and book name succeeds and the quote appears in the list.
- Saving with blank text shows `Quote text can't be empty` inline; blank book name shows `Book name can't be empty`.
- Saving the same text/book/page twice shows `This quote already exists` and does not add a second row.
- Typing in the search box filters the list; a tag chip filters it; two chips narrow further.
- The count line reads "N quotes" with no filter and "N results" while searching.
- Tapping a row opens it prefilled; edits save.
- Swiping a row end-to-start deletes it with a `Deleted` snackbar whose **Undo** brings it back.
- The book field suggests previously used books; the tag field suggests previously used tags.

- [ ] **Step 7: Checkpoint**

Run `./gradlew :app:testDebugUnitTest`. All green.

---

## Task 11: Reel screen

**Files:**
- Create: `app/src/main/java/com/rzi/quotes/ui/reel/ReelUiState.kt`
- Create: `app/src/main/java/com/rzi/quotes/ui/reel/ReelViewModel.kt`
- Create: `app/src/main/java/com/rzi/quotes/ui/reel/ReelPage.kt`
- Create: `app/src/main/java/com/rzi/quotes/ui/reel/ReelFilterSheet.kt`
- Modify: `app/src/main/java/com/rzi/quotes/ui/reel/ReelScreen.kt` (replacing the Task 8 stub)

**Interfaces:**
- Consumes: `ObserveReelDeck`/`ReelDeckState`, `ReelStateStore` (Task 7); `QuoteRepository` (Task 6); `Deck` (Task 3); `quoteTextStyle`/`QUOTE_MAX_LINES` (Task 8).
- Produces: the real `ReelScreen(onAddQuote: () -> Unit)`.

- [ ] **Step 1: Create the UI state**

`ReelUiState.kt`:

```kotlin
package com.rzi.quotes.ui.reel

import com.rzi.quotes.domain.model.Book
import com.rzi.quotes.domain.model.Quote
import com.rzi.quotes.domain.model.ReelFilter
import com.rzi.quotes.domain.model.ReelMode
import com.rzi.quotes.domain.model.TagFilter
import com.rzi.quotes.domain.reel.Deck
import com.rzi.quotes.domain.reel.LinearDeck

data class ReelUiState(
    val deck: Deck = LinearDeck(emptyList()),
    val initialPage: Int = 0,
    val mode: ReelMode = ReelMode.SHUFFLE,
    val filter: ReelFilter = ReelFilter(),
    val quotes: Map<Long, Quote> = emptyMap(),
    val books: List<Book> = emptyList(),
    val tagFilters: List<TagFilter> = emptyList(),
    val isFilterSheetOpen: Boolean = false,
    val isLoading: Boolean = true,
) {
    val isEmpty: Boolean get() = !isLoading && deck.size == 0

    /**
     * Recreating the pager whenever this changes is what stops a filter change, a mode switch, or a
     * resized id set from leaving the pager pointed at a stale index.
     */
    val deckKey: String get() = "$mode-${filter.bookId}-${filter.tagIds}-${deck.size}"
}
```

- [ ] **Step 2: Create the ViewModel**

`ReelViewModel.kt`:

```kotlin
package com.rzi.quotes.ui.reel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.rzi.quotes.domain.model.ReelFilter
import com.rzi.quotes.domain.model.ReelMode
import com.rzi.quotes.domain.repository.QuoteRepository
import com.rzi.quotes.domain.repository.ReelStateStore
import com.rzi.quotes.domain.usecase.ObserveReelDeck
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class ReelViewModel @Inject constructor(
    private val repository: QuoteRepository,
    private val store: ReelStateStore,
    observeReelDeck: ObserveReelDeck,
) : ViewModel() {

    private val _state = MutableStateFlow(ReelUiState())
    val state: StateFlow<ReelUiState> = _state.asStateFlow()

    init {
        observeReelDeck()
            .onEach { deckState ->
                _state.value = _state.value.copy(
                    deck = deckState.deck,
                    initialPage = startPageFor(deckState.deck.size, deckState.index),
                    mode = deckState.mode,
                    filter = deckState.filter,
                    isLoading = false,
                )
                prefetchAround(startPageFor(deckState.deck.size, deckState.index))
            }
            .launchIn(viewModelScope)

        repository.observeBooks()
            .onEach { books -> _state.value = _state.value.copy(books = books) }
            .launchIn(viewModelScope)

        repository.observeTagFilters()
            .onEach { tags -> _state.value = _state.value.copy(tagFilters = tags) }
            .launchIn(viewModelScope)
    }

    /** Called when the pager settles. Persists position and warms neighbouring quotes. */
    fun onPageSettled(page: Int) {
        val deck = _state.value.deck
        if (deck.size == 0) return
        val quoteId = deck.idAt(page)
        viewModelScope.launch {
            store.update { it.copy(absoluteIndex = page, currentQuoteId = quoteId) }
        }
        prefetchAround(page)
    }

    fun toggleMode() {
        val nextMode =
            if (_state.value.mode == ReelMode.SHUFFLE) ReelMode.LINEAR else ReelMode.SHUFFLE
        viewModelScope.launch { store.update { it.copy(mode = nextMode) } }
    }

    fun openFilterSheet() { _state.value = _state.value.copy(isFilterSheetOpen = true) }

    fun closeFilterSheet() { _state.value = _state.value.copy(isFilterSheetOpen = false) }

    fun applyFilter(filter: ReelFilter) {
        viewModelScope.launch {
            store.update { it.copy(filter = filter, absoluteIndex = 0, currentQuoteId = null) }
        }
        closeFilterSheet()
    }

    fun clearFilter() = applyFilter(ReelFilter())

    fun filterByTag(tagName: String) {
        val tagId = _state.value.tagFilters.firstOrNull { it.name == tagName }?.id ?: return
        applyFilter(ReelFilter(tagIds = listOf(tagId)))
    }

    private fun prefetchAround(page: Int) {
        val deck = _state.value.deck
        if (deck.size == 0) return
        viewModelScope.launch {
            val wanted = (page - PREFETCH..page + PREFETCH).map { deck.idAt(it) }.distinct()
            val known = _state.value.quotes
            val fetched = wanted.filter { it !in known }.mapNotNull { repository.quoteById(it) }
            if (fetched.isEmpty()) return@launch
            // Bound the cache so a long session cannot accumulate the whole collection in memory.
            val merged = known + fetched.associateBy { it.id }
            val retained = if (merged.size <= CACHE_LIMIT) merged else {
                merged.filterKeys { it in wanted } +
                    merged.entries.take(CACHE_LIMIT - wanted.size)
                        .associate { it.key to it.value }
            }
            _state.value = _state.value.copy(quotes = retained)
        }
    }

    private fun startPageFor(size: Int, index: Int): Int {
        if (size == 0) return 0
        // Offset into the middle of the index space so backward swipes have room, clamped so the
        // multiplication cannot overflow for large collections.
        val initialCycle = minOf(1_000, (Int.MAX_VALUE / 2) / size)
        return initialCycle * size + index.mod(size)
    }

    private companion object {
        const val PREFETCH = 3
        const val CACHE_LIMIT = 60
    }
}
```

- [ ] **Step 3: Create the page composable**

`ReelPage.kt`:

```kotlin
package com.rzi.quotes.ui.reel

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.AssistChip
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.rzi.quotes.domain.model.Quote
import com.rzi.quotes.ui.theme.QUOTE_MAX_LINES
import com.rzi.quotes.ui.theme.quoteTextStyle

@Composable
fun ReelPage(
    quote: Quote?,
    onCopy: (Quote) -> Unit,
    onShare: (Quote) -> Unit,
    onTagClick: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    if (quote == null) {
        Box(modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            CircularProgressIndicator()
        }
        return
    }

    var showFullText by remember(quote.id) { mutableStateOf(false) }
    var isClamped by remember(quote.id) { mutableStateOf(false) }

    Box(modifier = modifier.fillMaxSize().background(pageColor(quote.bookName))) {
        Column(
            modifier = Modifier.fillMaxSize().padding(horizontal = 24.dp, vertical = 32.dp),
            verticalArrangement = Arrangement.Center,
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Text(
                text = quote.text,
                style = quoteTextStyle(quote.text.length),
                textAlign = TextAlign.Center,
                maxLines = QUOTE_MAX_LINES,
                onTextLayout = { layout -> isClamped = layout.hasVisualOverflow },
            )
            if (isClamped) {
                TextButton(onClick = { showFullText = true }) { Text("Read more") }
            }
            Text(
                text = buildString {
                    append(quote.bookName)
                    quote.pageNumber?.let { append(" · p. $it") }
                },
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(top = 24.dp),
            )
            if (quote.tags.isNotEmpty()) {
                Row(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    modifier = Modifier.padding(top = 12.dp),
                ) {
                    quote.tags.take(3).forEach { tag ->
                        AssistChip(onClick = { onTagClick(tag) }, label = { Text(tag) })
                    }
                }
            }
        }

        Row(
            modifier = Modifier.align(Alignment.BottomEnd).padding(16.dp),
            horizontalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            IconButton(onClick = { onCopy(quote) }) {
                Icon(Icons.Filled.ContentCopy, contentDescription = "Copy quote")
            }
            IconButton(onClick = { onShare(quote) }) {
                Icon(Icons.Filled.Share, contentDescription = "Share quote")
            }
        }
    }

    if (showFullText) {
        AlertDialog(
            onDismissRequest = { showFullText = false },
            confirmButton = {
                TextButton(onClick = { showFullText = false }) { Text("Close") }
            },
            title = { Text(quote.bookName) },
            text = {
                Column(modifier = Modifier.verticalScroll(rememberScrollState())) {
                    Text(quote.text)
                }
            },
        )
    }
}

/**
 * One of six tonal containers, chosen deterministically from the book name so every quote from a
 * book shares a background and the scroll gains rhythm without leaving the theme.
 */
@Composable
private fun pageColor(bookName: String): Color {
    val scheme = MaterialTheme.colorScheme
    val palette = listOf(
        scheme.surfaceContainerLowest,
        scheme.surfaceContainerLow,
        scheme.surfaceContainer,
        scheme.surfaceContainerHigh,
        scheme.surfaceContainerHighest,
        scheme.secondaryContainer,
    )
    return palette[bookName.lowercase().hashCode().mod(palette.size)]
}
```

- [ ] **Step 4: Create the filter sheet**

`ReelFilterSheet.kt`:

```kotlin
package com.rzi.quotes.ui.reel

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.rzi.quotes.domain.model.Book
import com.rzi.quotes.domain.model.ReelFilter
import com.rzi.quotes.domain.model.TagFilter

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun ReelFilterSheet(
    books: List<Book>,
    tagFilters: List<TagFilter>,
    current: ReelFilter,
    onApply: (ReelFilter) -> Unit,
    onDismiss: () -> Unit,
) {
    var bookId by remember { mutableStateOf(current.bookId) }
    var tagIds by remember { mutableStateOf(current.tagIds) }

    ModalBottomSheet(onDismissRequest = onDismiss) {
        Column(
            modifier = Modifier.padding(horizontal = 20.dp).padding(bottom = 32.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Text("Filter the reel", style = MaterialTheme.typography.titleMedium)

            Text("Book", style = MaterialTheme.typography.labelLarge)
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                FilterChip(
                    selected = bookId == null,
                    onClick = { bookId = null },
                    label = { Text("All books") },
                )
                books.forEach { book ->
                    FilterChip(
                        selected = bookId == book.id,
                        onClick = { bookId = if (bookId == book.id) null else book.id },
                        label = { Text(book.name) },
                    )
                }
            }

            Text("Tags", style = MaterialTheme.typography.labelLarge)
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                tagFilters.forEach { tag ->
                    FilterChip(
                        selected = tag.id in tagIds,
                        onClick = {
                            tagIds = if (tag.id in tagIds) tagIds - tag.id else tagIds + tag.id
                        },
                        label = { Text("${tag.name} (${tag.usageCount})") },
                    )
                }
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.End,
            ) {
                TextButton(onClick = { onApply(ReelFilter()) }) { Text("Clear") }
                TextButton(onClick = { onApply(ReelFilter(bookId, tagIds)) }) { Text("Apply") }
            }
        }
    }
}
```

- [ ] **Step 5: Replace the ReelScreen stub**

`ReelScreen.kt`:

```kotlin
package com.rzi.quotes.ui.reel

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.pager.VerticalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.FormatListNumbered
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.FilterList
import androidx.compose.material.icons.filled.Shuffle
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconToggleButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.rzi.quotes.domain.model.Quote
import com.rzi.quotes.domain.model.ReelMode
import com.rzi.quotes.ui.components.EmptyState
import kotlin.math.absoluteValue

@Composable
fun ReelScreen(onAddQuote: () -> Unit, viewModel: ReelViewModel = hiltViewModel()) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val context = LocalContext.current

    if (state.isEmpty) {
        EmptyState(
            title = "No quotes yet",
            actionLabel = "Add your first quote",
            onAction = onAddQuote,
        )
        return
    }

    Box(modifier = Modifier.fillMaxSize()) {
        key(state.deckKey) {
            val pagerState = rememberPagerState(
                initialPage = state.initialPage,
                pageCount = { if (state.deck.size == 0) 0 else Int.MAX_VALUE },
            )

            LaunchedEffect(pagerState) {
                snapshotFlow { pagerState.settledPage }.collect(viewModel::onPageSettled)
            }

            VerticalPager(state = pagerState, modifier = Modifier.fillMaxSize()) { page ->
                val quoteId = state.deck.idAt(page)
                val offset = (pagerState.currentPage - page + pagerState.currentPageOffsetFraction)
                    .absoluteValue
                    .coerceIn(0f, 1f)
                ReelPage(
                    quote = state.quotes[quoteId],
                    onCopy = { copyQuote(context, it) },
                    onShare = { shareQuote(context, it) },
                    onTagClick = viewModel::filterByTag,
                    modifier = Modifier.graphicsLayer {
                        alpha = 1f - offset * 0.5f
                        val scale = 1f - offset * 0.05f
                        scaleX = scale
                        scaleY = scale
                    },
                )
            }
        }

        Row(
            modifier = Modifier.align(Alignment.TopStart).padding(12.dp),
            horizontalArrangement = Arrangement.spacedBy(4.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            IconButton(onClick = viewModel::openFilterSheet) {
                Icon(Icons.Filled.FilterList, contentDescription = "Filter the reel")
            }
            IconToggleButton(
                checked = state.mode == ReelMode.SHUFFLE,
                onCheckedChange = { viewModel.toggleMode() },
            ) {
                if (state.mode == ReelMode.SHUFFLE) {
                    Icon(Icons.Filled.Shuffle, contentDescription = "Shuffled order")
                } else {
                    Icon(
                        Icons.AutoMirrored.Filled.FormatListNumbered,
                        contentDescription = "Book order",
                    )
                }
            }
            if (state.filter.isActive) {
                AssistChip(
                    onClick = viewModel::clearFilter,
                    label = { Text("Filtered") },
                    trailingIcon = {
                        Icon(Icons.Filled.Close, contentDescription = "Clear filter")
                    },
                )
            }
        }
    }

    if (state.isFilterSheetOpen) {
        ReelFilterSheet(
            books = state.books,
            tagFilters = state.tagFilters,
            current = state.filter,
            onApply = viewModel::applyFilter,
            onDismiss = viewModel::closeFilterSheet,
        )
    }
}

private fun quoteAsText(quote: Quote): String = buildString {
    append(quote.text)
    append("\n— ")
    append(quote.bookName)
    quote.pageNumber?.let { append(", p. $it") }
}

private fun copyQuote(context: Context, quote: Quote) {
    val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
    clipboard.setPrimaryClip(ClipData.newPlainText("Quote", quoteAsText(quote)))
}

private fun shareQuote(context: Context, quote: Quote) {
    val intent = Intent(Intent.ACTION_SEND).apply {
        type = "text/plain"
        putExtra(Intent.EXTRA_TEXT, quoteAsText(quote))
    }
    context.startActivity(Intent.createChooser(intent, null))
}
```

- [ ] **Step 6: Build, install, and verify by hand**

Run:

```bash
./gradlew :app:installDebug
```

Add at least six quotes across two books with page numbers, then on the Reel tab verify:
- Swiping up advances; swiping down returns to the *same* previous quote, not a new random one.
- The shuffle/list toggle flips mode and keeps the current quote on screen.
- In list mode the order is book name, then page number, with unpaged quotes last in their book.
- Tapping a tag chip filters the reel; the "Filtered" chip clears it.
- The filter sheet's book and tag chips apply and clear.
- Copy puts the quote plus `— Book, p. N` on the clipboard; share opens the share sheet.
- A very long quote clamps and "Read more" opens the full text in a scrollable dialog.
- Force-stopping and reopening the app returns to the same quote.

- [ ] **Step 7: Checkpoint**

Run `./gradlew :app:testDebugUnitTest`. All green.

---

## Task 12: Database import

**Files:**
- Create: `app/src/main/java/com/rzi/quotes/domain/repository/TransferRepository.kt`
- Create: `app/src/main/java/com/rzi/quotes/domain/usecase/ImportDatabase.kt`
- Create: `app/src/main/java/com/rzi/quotes/data/transfer/DatabaseImporter.kt`
- Test: `app/src/test/java/com/rzi/quotes/data/transfer/DatabaseImporterTest.kt`

**Interfaces:**
- Consumes: DAOs (Tasks 4–5), `DedupeKey` (Task 2), `ImportResult`/`ImportOutcome`/`TransferError` (Task 6), `Clock` (Task 7).
- Produces:
  - `interface TransferRepository { suspend fun import(uriString: String): ImportOutcome; suspend fun export(uriString: String): ExportOutcome }`
  - `DatabaseImporter(db, quoteDao, bookDao, tagDao, ftsDao, clock).import(source: File): ImportOutcome`
  - `ImportDatabase(repository)` — `suspend operator fun invoke(uriString: String): ImportOutcome`

`TransferRepositoryImpl` is created in Task 13, once the exporter exists, so this task never leaves the tree uncompilable.

- [ ] **Step 1: Create the port and use case**

`app/src/main/java/com/rzi/quotes/domain/repository/TransferRepository.kt`:

```kotlin
package com.rzi.quotes.domain.repository

import com.rzi.quotes.domain.model.ExportOutcome
import com.rzi.quotes.domain.model.ImportOutcome

/** URIs cross this boundary as strings so `android.net.Uri` stays out of the domain layer. */
interface TransferRepository {
    suspend fun import(uriString: String): ImportOutcome
    suspend fun export(uriString: String): ExportOutcome
}
```

`app/src/main/java/com/rzi/quotes/domain/usecase/ImportDatabase.kt`:

```kotlin
package com.rzi.quotes.domain.usecase

import com.rzi.quotes.domain.model.ImportOutcome
import com.rzi.quotes.domain.repository.TransferRepository
import javax.inject.Inject

class ImportDatabase @Inject constructor(private val repository: TransferRepository) {
    suspend operator fun invoke(uriString: String): ImportOutcome = repository.import(uriString)
}
```

- [ ] **Step 2: Write the failing importer tests**

`app/src/test/java/com/rzi/quotes/data/transfer/DatabaseImporterTest.kt`:

```kotlin
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

    /** Builds a source database shaped like this app's schema. */
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

        assertThat(outcome).isEqualTo(ImportOutcome.Failure(TransferError.NOT_A_DATABASE))
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
}
```

- [ ] **Step 3: Run to verify it fails**

Run: `./gradlew :app:testDebugUnitTest --tests '*DatabaseImporterTest*'`
Expected: FAIL — unresolved reference `DatabaseImporter`.

- [ ] **Step 4: Implement DatabaseImporter**

`app/src/main/java/com/rzi/quotes/data/transfer/DatabaseImporter.kt`:

```kotlin
package com.rzi.quotes.data.transfer

import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteException
import androidx.room.withTransaction
import com.rzi.quotes.data.local.RziDatabase
import com.rzi.quotes.data.local.dao.BookDao
import com.rzi.quotes.data.local.dao.QuoteDao
import com.rzi.quotes.data.local.dao.QuoteFtsDao
import com.rzi.quotes.data.local.dao.TagDao
import com.rzi.quotes.data.local.entity.BookEntity
import com.rzi.quotes.data.local.entity.QuoteEntity
import com.rzi.quotes.data.local.entity.QuoteFtsEntity
import com.rzi.quotes.data.local.entity.QuoteTagCrossRef
import com.rzi.quotes.data.local.entity.TagEntity
import com.rzi.quotes.domain.model.ImportOutcome
import com.rzi.quotes.domain.model.ImportResult
import com.rzi.quotes.domain.model.TransferError
import com.rzi.quotes.domain.text.DedupeKey
import java.io.File
import java.time.Clock
import javax.inject.Inject

/** One quote as read out of a foreign database file. */
private data class SourceQuote(
    val text: String,
    val bookName: String,
    val pageNumber: Int?,
    val tags: List<String>,
)

/**
 * Merges a foreign `.db` into the live database.
 *
 * The source is opened read-only through raw [SQLiteDatabase] rather than Room, because Room would
 * demand a matching schema and identity hash. Dedupe keys are recomputed locally rather than trusted
 * from the file, which may have been hand-edited. The whole merge runs in one transaction, so a
 * failure part-way through leaves existing data untouched.
 */
class DatabaseImporter @Inject constructor(
    private val db: RziDatabase,
    private val quoteDao: QuoteDao,
    private val bookDao: BookDao,
    private val tagDao: TagDao,
    private val ftsDao: QuoteFtsDao,
    private val clock: Clock,
) {

    suspend fun import(source: File): ImportOutcome {
        if (!source.exists() || !source.canRead() || source.length() == 0L) {
            return ImportOutcome.Failure(TransferError.UNREADABLE_FILE)
        }

        val quotes = try {
            SQLiteDatabase.openDatabase(source.absolutePath, null, SQLiteDatabase.OPEN_READONLY)
                .use { sqlite ->
                    if (!hasRequiredSchema(sqlite)) {
                        return ImportOutcome.Failure(TransferError.SCHEMA_MISMATCH)
                    }
                    readQuotes(sqlite)
                }
        } catch (e: SQLiteException) {
            return ImportOutcome.Failure(TransferError.NOT_A_DATABASE)
        }

        if (quotes.isEmpty()) return ImportOutcome.Failure(TransferError.NO_QUOTES_FOUND)

        val valid = quotes.filter { it.text.isNotBlank() && it.bookName.isNotBlank() }
        val skippedInvalid = quotes.size - valid.size
        val importedAt = clock.millis()

        return try {
            var added = 0
            var duplicates = 0
            db.withTransaction {
                valid.forEach { quote ->
                    if (insert(quote, importedAt)) added++ else duplicates++
                }
            }
            ImportOutcome.Success(
                ImportResult(
                    added = added,
                    skippedDuplicates = duplicates,
                    skippedInvalid = skippedInvalid,
                )
            )
        } catch (e: SQLiteException) {
            ImportOutcome.Failure(TransferError.WRITE_FAILED)
        }
    }

    private suspend fun insert(quote: SourceQuote, importedAt: Long): Boolean {
        val text = quote.text.trim()
        val bookName = quote.bookName.trim()
        val dedupeKey = DedupeKey.of(text, bookName, quote.pageNumber)
        if (quoteDao.entityByDedupeKey(dedupeKey) != null) return false

        bookDao.insertIgnoring(BookEntity(name = bookName))
        val bookId = requireNotNull(bookDao.findByName(bookName)).id

        val quoteId = quoteDao.insertIgnoring(
            QuoteEntity(
                text = text,
                bookId = bookId,
                pageNumber = quote.pageNumber?.takeIf { it >= 1 },
                dedupeKey = dedupeKey,
                createdAt = importedAt,
                updatedAt = importedAt,
            )
        )
        if (quoteId == -1L) return false

        quote.tags.forEach { name ->
            val tagName = name.replace(",", "").trim()
            if (tagName.isEmpty()) return@forEach
            tagDao.insertIgnoring(TagEntity(name = tagName))
            val tagId = requireNotNull(tagDao.findByName(tagName)).id
            tagDao.link(QuoteTagCrossRef(quoteId = quoteId, tagId = tagId))
        }

        ftsDao.upsert(
            QuoteFtsEntity(
                rowId = quoteId,
                text = text,
                bookName = bookName,
                tagsFlat = quote.tags.joinToString(" "),
            )
        )
        return true
    }

    private fun hasRequiredSchema(sqlite: SQLiteDatabase): Boolean =
        REQUIRED_COLUMNS.all { (table, columns) ->
            val present = try {
                sqlite.rawQuery("PRAGMA table_info($table)", null).use { cursor ->
                    val names = mutableSetOf<String>()
                    val nameIndex = cursor.getColumnIndexOrThrow("name")
                    while (cursor.moveToNext()) names += cursor.getString(nameIndex)
                    names
                }
            } catch (e: SQLiteException) {
                return false
            }
            present.isNotEmpty() && columns.all { it in present }
        }

    private fun readQuotes(sqlite: SQLiteDatabase): List<SourceQuote> {
        val tagsByQuote = mutableMapOf<Long, MutableList<String>>()
        sqlite.rawQuery(
            "SELECT qt.quoteId, t.name FROM quote_tags qt JOIN tags t ON t.id = qt.tagId",
            null,
        ).use { cursor ->
            while (cursor.moveToNext()) {
                tagsByQuote.getOrPut(cursor.getLong(0)) { mutableListOf() } += cursor.getString(1)
            }
        }

        return sqlite.rawQuery(
            "SELECT q.id, q.text, b.name, q.pageNumber " +
                "FROM quotes q LEFT JOIN books b ON b.id = q.bookId",
            null,
        ).use { cursor ->
            buildList {
                while (cursor.moveToNext()) {
                    val id = cursor.getLong(0)
                    add(
                        SourceQuote(
                            text = if (cursor.isNull(1)) "" else cursor.getString(1),
                            bookName = if (cursor.isNull(2)) "" else cursor.getString(2),
                            pageNumber = if (cursor.isNull(3)) null else cursor.getInt(3),
                            tags = tagsByQuote[id]?.distinct().orEmpty(),
                        )
                    )
                }
            }
        }
    }

    private companion object {
        /** Extra columns are tolerated; every listed column must be present. */
        val REQUIRED_COLUMNS = mapOf(
            "quotes" to listOf("id", "text", "bookId", "pageNumber"),
            "books" to listOf("id", "name"),
            "tags" to listOf("id", "name"),
            "quote_tags" to listOf("quoteId", "tagId"),
        )
    }
}
```

- [ ] **Step 5: Run to verify the importer tests pass**

Run: `./gradlew :app:testDebugUnitTest --tests '*DatabaseImporterTest*'`
Expected: PASS, 15 tests.

If `a file of random bytes is rejected` fails because `SQLiteDatabase.openDatabase` throws something other than `SQLiteException` (some Android versions throw `SQLiteDatabaseCorruptException`, which is a subclass, or wrap it), widen that catch to `catch (e: RuntimeException)` and add a comment naming the observed exception. Import must never crash the app.

- [ ] **Step 6: Checkpoint**

Run `./gradlew :app:testDebugUnitTest`. All green.

---

## Task 13: Database export and wiring transfer into the UI

**Files:**
- Create: `app/src/main/java/com/rzi/quotes/data/transfer/DatabaseExporter.kt`
- Create: `app/src/main/java/com/rzi/quotes/data/repository/TransferRepositoryImpl.kt`
- Create: `app/src/main/java/com/rzi/quotes/domain/usecase/ExportDatabase.kt`
- Modify: `app/src/main/java/com/rzi/quotes/di/RepositoryModule.kt`
- Modify: `app/src/main/java/com/rzi/quotes/ui/library/LibraryViewModel.kt`
- Modify: `app/src/main/java/com/rzi/quotes/ui/library/LibraryScreen.kt`
- Modify: `app/src/test/java/com/rzi/quotes/testutil/DbFixtures.kt`
- Test: `app/src/test/java/com/rzi/quotes/data/transfer/TransferRoundTripTest.kt`

**Interfaces:**
- Consumes: `DatabaseImporter` (Task 12), `RziDatabase` (Task 4), `Clock` and `@IoDispatcher` (Task 7).
- Produces:
  - `DatabaseExporter.export(target: Uri, context: Context): ExportOutcome`, `DatabaseExporter.exportToFile(target: File): ExportOutcome`, `DatabaseExporter.suggestedFileName(): String` → `rzi-quotes-YYYY-MM-DD.db`
  - `TransferRepositoryImpl` bound to `TransferRepository`
  - `ExportDatabase(repository)`
  - `LibraryViewModel.onImportFilePicked(uriString)`, `onExportTargetPicked(uriString)`, `suggestedExportName()`

- [ ] **Step 1: Add the file-backed test fixture**

Append inside `object DbFixtures` in `app/src/test/java/com/rzi/quotes/testutil/DbFixtures.kt`:

```kotlin
    /**
     * A database backed by a real file. Export copies the database file itself, so the round-trip
     * test cannot use an in-memory instance as its source.
     */
    fun fileBackedDatabase(file: java.io.File): RziDatabase {
        val context = ApplicationProvider.getApplicationContext<Context>()
        return Room.databaseBuilder(context, RziDatabase::class.java, file.absolutePath).build()
    }
```

- [ ] **Step 2: Write the failing export and round-trip tests**

`app/src/test/java/com/rzi/quotes/data/transfer/TransferRoundTripTest.kt`:

```kotlin
package com.rzi.quotes.data.transfer

import com.google.common.truth.Truth.assertThat
import com.rzi.quotes.data.local.RziDatabase
import com.rzi.quotes.domain.model.ExportOutcome
import com.rzi.quotes.domain.model.ImportOutcome
import com.rzi.quotes.testutil.DbFixtures
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
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
class TransferRoundTripTest {

    @get:Rule val temp = TemporaryFolder()

    private val clock = Clock.fixed(Instant.parse("2026-08-07T10:15:30Z"), ZoneOffset.UTC)

    private fun exporterFor(db: RziDatabase) = DatabaseExporter(db = db, clock = clock)

    private fun importerFor(db: RziDatabase) = DatabaseImporter(
        db = db,
        quoteDao = db.quoteDao(),
        bookDao = db.bookDao(),
        tagDao = db.tagDao(),
        ftsDao = db.quoteFtsDao(),
        clock = clock,
    )

    @Test
    fun `suggested file name carries the current date`() {
        val db = DbFixtures.inMemoryDatabase()
        try {
            assertThat(exporterFor(db).suggestedFileName()).isEqualTo("rzi-quotes-2026-08-07.db")
        } finally {
            db.close()
        }
    }

    @Test
    fun `export then import into an empty database preserves every row`() = runTest {
        val source = DbFixtures.fileBackedDatabase(File(temp.newFolder("source"), "rzi.db"))
        DbFixtures.insertQuote(source, "first quote", "Book One", 1, listOf("alpha", "beta"))
        DbFixtures.insertQuote(source, "second quote", "Book One", 2, listOf("beta"))
        DbFixtures.insertQuote(source, "third quote", "Book Two", null, emptyList())

        val exported = File(temp.newFolder("out"), "backup.db")
        val exportOutcome = exporterFor(source).exportToFile(exported)
        source.close()

        assertThat(exportOutcome).isEqualTo(ExportOutcome.Success)
        assertThat(exported.length()).isGreaterThan(0L)

        val destination = DbFixtures.inMemoryDatabase()
        try {
            val importOutcome = importerFor(destination).import(exported)

            assertThat(importOutcome).isInstanceOf(ImportOutcome.Success::class.java)
            val result = (importOutcome as ImportOutcome.Success).result
            assertThat(result.added).isEqualTo(3)
            assertThat(result.skippedDuplicates).isEqualTo(0)
            assertThat(result.skippedInvalid).isEqualTo(0)

            assertThat(destination.quoteDao().observeCount().first()).isEqualTo(3)
            assertThat(destination.bookDao().observeAll().first()).hasSize(2)
            assertThat(destination.tagDao().observeFilters().first().map { it.name })
                .containsExactly("beta", "alpha")
            assertThat(destination.quoteFtsDao().count()).isEqualTo(3)
        } finally {
            destination.close()
        }
    }

    @Test
    fun `importing the same export twice adds nothing the second time`() = runTest {
        val source = DbFixtures.fileBackedDatabase(File(temp.newFolder("source2"), "rzi.db"))
        DbFixtures.insertQuote(source, "only quote", "Only Book", 7)
        val exported = File(temp.newFolder("out2"), "backup.db")
        exporterFor(source).exportToFile(exported)
        source.close()

        val destination = DbFixtures.inMemoryDatabase()
        try {
            importerFor(destination).import(exported)
            val second = importerFor(destination).import(exported) as ImportOutcome.Success

            assertThat(second.result.added).isEqualTo(0)
            assertThat(second.result.skippedDuplicates).isEqualTo(1)
            assertThat(destination.quoteDao().observeCount().first()).isEqualTo(1)
        } finally {
            destination.close()
        }
    }

    @Test
    fun `exporting to an unwritable location fails without throwing`() = runTest {
        val source = DbFixtures.fileBackedDatabase(File(temp.newFolder("source3"), "rzi.db"))
        DbFixtures.insertQuote(source, "a quote", "A Book")
        try {
            val target = File(temp.root, "no-such-directory/backup.db")
            assertThat(exporterFor(source).exportToFile(target))
                .isInstanceOf(ExportOutcome.Failure::class.java)
        } finally {
            source.close()
        }
    }
}
```

- [ ] **Step 3: Run to verify it fails**

Run: `./gradlew :app:testDebugUnitTest --tests '*TransferRoundTripTest*'`
Expected: FAIL — unresolved reference `DatabaseExporter`.

- [ ] **Step 4: Implement DatabaseExporter**

`app/src/main/java/com/rzi/quotes/data/transfer/DatabaseExporter.kt`:

```kotlin
package com.rzi.quotes.data.transfer

import android.content.Context
import android.net.Uri
import com.rzi.quotes.data.local.RziDatabase
import com.rzi.quotes.domain.model.ExportOutcome
import com.rzi.quotes.domain.model.TransferError
import java.io.File
import java.io.OutputStream
import java.time.Clock
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import javax.inject.Inject

/**
 * Writes a self-contained copy of the database.
 *
 * WAL is checkpointed with `TRUNCATE` first so every page lives in the main database file and a
 * plain byte copy is complete. `VACUUM INTO` would be tidier but needs a newer SQLite than
 * `minSdk 26` guarantees.
 *
 * [Context] is a parameter rather than a constructor dependency so the exporter stays constructible
 * in tests without a Hilt graph.
 */
class DatabaseExporter @Inject constructor(
    private val db: RziDatabase,
    private val clock: Clock,
) {

    fun suggestedFileName(): String =
        "rzi-quotes-${LocalDate.now(clock).format(DateTimeFormatter.ISO_LOCAL_DATE)}.db"

    fun export(target: Uri, context: Context): ExportOutcome = runCatching {
        checkpoint()
        context.contentResolver.openOutputStream(target)?.use { output ->
            copyDatabaseTo(output)
            ExportOutcome.Success
        } ?: ExportOutcome.Failure(TransferError.WRITE_FAILED)
    }.getOrElse { ExportOutcome.Failure(TransferError.WRITE_FAILED) }

    fun exportToFile(target: File): ExportOutcome = runCatching {
        checkpoint()
        target.outputStream().use { output ->
            copyDatabaseTo(output)
            ExportOutcome.Success
        }
    }.getOrElse { ExportOutcome.Failure(TransferError.WRITE_FAILED) }

    private fun checkpoint() {
        db.openHelper.writableDatabase
            .query("PRAGMA wal_checkpoint(TRUNCATE)", emptyArray())
            .close()
    }

    private fun copyDatabaseTo(output: OutputStream) {
        val path = requireNotNull(db.openHelper.writableDatabase.path) {
            "Cannot export an in-memory database"
        }
        File(path).inputStream().use { input -> input.copyTo(output) }
    }
}
```

`runCatching` is used deliberately rather than a list of `catch` clauses: an export must never crash the app, and the failure modes here span `IOException`, `SecurityException`, and `IllegalArgumentException` (in-memory database with no file path).

- [ ] **Step 5: Run to verify the round-trip tests pass**

Run: `./gradlew :app:testDebugUnitTest --tests '*TransferRoundTripTest*'`
Expected: PASS, 4 tests. The round-trip test is the strongest single check on the whole transfer feature — do not move on while it is red.

- [ ] **Step 6: Create TransferRepositoryImpl, the use case, and the binding**

`app/src/main/java/com/rzi/quotes/data/repository/TransferRepositoryImpl.kt`:

```kotlin
package com.rzi.quotes.data.repository

import android.content.Context
import android.net.Uri
import com.rzi.quotes.data.transfer.DatabaseExporter
import com.rzi.quotes.data.transfer.DatabaseImporter
import com.rzi.quotes.di.IoDispatcher
import com.rzi.quotes.domain.model.ExportOutcome
import com.rzi.quotes.domain.model.ImportOutcome
import com.rzi.quotes.domain.model.TransferError
import com.rzi.quotes.domain.repository.TransferRepository
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.withContext
import java.io.File
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class TransferRepositoryImpl @Inject constructor(
    @ApplicationContext private val context: Context,
    private val importer: DatabaseImporter,
    private val exporter: DatabaseExporter,
    @IoDispatcher private val io: CoroutineDispatcher,
) : TransferRepository {

    override suspend fun import(uriString: String): ImportOutcome = withContext(io) {
        // SQLiteDatabase cannot open a content:// URI, so the picked document is staged in the cache
        // and the staging file is removed regardless of outcome.
        val staging = File(context.cacheDir, "import-staging.db")
        try {
            val copied = context.contentResolver.openInputStream(Uri.parse(uriString))?.use { input ->
                staging.outputStream().use(input::copyTo)
                true
            } ?: false
            if (!copied) {
                ImportOutcome.Failure(TransferError.UNREADABLE_FILE)
            } else {
                importer.import(staging)
            }
        } catch (e: Exception) {
            // Any failure reading the picked document is a read failure from the user's point of
            // view, and no read failure may crash the app.
            ImportOutcome.Failure(TransferError.UNREADABLE_FILE)
        } finally {
            staging.delete()
        }
    }

    override suspend fun export(uriString: String): ExportOutcome = withContext(io) {
        exporter.export(Uri.parse(uriString), context)
    }
}
```

`app/src/main/java/com/rzi/quotes/domain/usecase/ExportDatabase.kt`:

```kotlin
package com.rzi.quotes.domain.usecase

import com.rzi.quotes.domain.model.ExportOutcome
import com.rzi.quotes.domain.repository.TransferRepository
import javax.inject.Inject

class ExportDatabase @Inject constructor(private val repository: TransferRepository) {
    suspend operator fun invoke(uriString: String): ExportOutcome = repository.export(uriString)
}
```

In `RepositoryModule`, add:

```kotlin
    @Binds @Singleton
    fun transferRepository(impl: TransferRepositoryImpl): TransferRepository
```

- [ ] **Step 7: Wire transfer into LibraryViewModel**

Add these constructor parameters to `LibraryViewModel`: `private val importDatabase: ImportDatabase`, `private val exportDatabase: ExportDatabase`, `private val exporter: DatabaseExporter`. Add these members:

```kotlin
    fun suggestedExportName(): String = exporter.suggestedFileName()

    fun onImportFilePicked(uriString: String) {
        _state.value = _state.value.copy(isTransferInProgress = true)
        viewModelScope.launch {
            val outcome = importDatabase(uriString)
            _state.value = _state.value.copy(isTransferInProgress = false)
            _messages.send(
                when (outcome) {
                    is ImportOutcome.Success -> with(outcome.result) {
                        "$added added, $skippedDuplicates duplicates skipped"
                    }
                    is ImportOutcome.Failure -> importErrorMessage(outcome.reason)
                }
            )
        }
    }

    fun onExportTargetPicked(uriString: String) {
        _state.value = _state.value.copy(isTransferInProgress = true)
        viewModelScope.launch {
            val outcome = exportDatabase(uriString)
            _state.value = _state.value.copy(isTransferInProgress = false)
            _messages.send(
                when (outcome) {
                    ExportOutcome.Success -> "Exported"
                    is ExportOutcome.Failure -> "Couldn't write the export file"
                }
            )
        }
    }

    /** WRITE_FAILED means different things on the two paths, so the copy differs. */
    private fun importErrorMessage(reason: TransferError): String = when (reason) {
        TransferError.UNREADABLE_FILE -> "Couldn't read that file"
        TransferError.NOT_A_DATABASE -> "That file isn't a SQLite database"
        TransferError.SCHEMA_MISMATCH -> "That database has a different structure"
        TransferError.NO_QUOTES_FOUND -> "No quotes found in that file"
        TransferError.WRITE_FAILED -> "Couldn't save to the database"
    }
```

Import `com.rzi.quotes.domain.model.ExportOutcome`, `ImportOutcome`, `TransferError`, `com.rzi.quotes.domain.usecase.ExportDatabase`, `ImportDatabase`, and `com.rzi.quotes.data.transfer.DatabaseExporter`.

Injecting `DatabaseExporter` (a data-layer class) into a ViewModel just for the filename is the one place this plan crosses its own layer rule. If that grates, add `suggestedExportFileName(): String` to `TransferRepository` and delegate — three extra lines, and the ViewModel stops knowing about `data`. Prefer the port version if you have the appetite.

- [ ] **Step 8: Wire the document pickers into LibraryScreen**

Inside `LibraryScreen`, before the `Scaffold`, create the launchers:

```kotlin
    val importLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument(),
    ) { uri -> uri?.let { viewModel.onImportFilePicked(it.toString()) } }

    val exportLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.CreateDocument("application/octet-stream"),
    ) { uri -> uri?.let { viewModel.onExportTargetPicked(it.toString()) } }
```

with `import androidx.activity.compose.rememberLauncherForActivityResult` and
`import androidx.activity.result.contract.ActivityResultContracts`.

Replace the overflow item handlers:
- Import → `{ overflowOpen = false; importLauncher.launch(arrayOf("*/*")) }`
- Export → `{ overflowOpen = false; exportLauncher.launch(viewModel.suggestedExportName()) }`

`arrayOf("*/*")` is deliberate: `.db` has no registered MIME type, so a narrower filter greys out the very file the user is trying to pick.

Point the empty state's `onSecondary` at `{ importLauncher.launch(arrayOf("*/*")) }` too, and delete the now-unused `onImport`/`onExport` parameters from the signature (updating `RziNavHost` if needed — with both parameters gone, `LibraryScreen()` still compiles unchanged).

- [ ] **Step 9: Build, install, and verify by hand**

Run:

```bash
./gradlew :app:installDebug
```

On the device:
- Export the database; confirm a `rzi-quotes-2026-08-07.db` file (today's date) appears where you chose and the snackbar says `Exported`.
- Add another quote, then import the exported file. Expect `0 added, N duplicates skipped` and no data lost.
- Import a photo or any non-database file. Expect `That file isn't a SQLite database` and an unchanged library.
- Import a database exported from a *different* dataset and confirm the new quotes merge in alongside the existing ones.

- [ ] **Step 10: Checkpoint**

Run `./gradlew :app:testDebugUnitTest`. All green.

---

## Task 14: Final verification

**Files:**
- No production changes expected. Fix whatever this task surfaces.

**Interfaces:**
- Consumes: everything.
- Produces: a verified build.

- [ ] **Step 1: Full test run**

Run:

```bash
./gradlew :app:testDebugUnitTest
```

Expected: every suite passes. Record the total test count.

- [ ] **Step 2: Clean build**

Run:

```bash
./gradlew clean :app:assembleDebug
```

Expected: `BUILD SUCCESSFUL` from a clean state.

- [ ] **Step 3: Verify the offline guarantee against the merged manifest**

Run:

```bash
./gradlew :app:processDebugMainManifest && find app/build -name AndroidManifest.xml -path '*merged*' -exec grep -Hn "uses-permission" {} +
```

Expected: no matches. Any permission found here came from a dependency — identify it via `./gradlew :app:dependencies` and remove the dependency or strip the permission with `tools:node="remove"`.

- [ ] **Step 4: Verify domain purity**

Run:

```bash
grep -rnE "^import (android\.|androidx\.(room|compose|datastore))" app/src/main/java/com/rzi/quotes/domain/
```

Expected: no output. `androidx.paging` imports in `domain` are permitted per Global Constraints and do not match this pattern.

- [ ] **Step 5: Manual acceptance pass**

Run:

```bash
./gradlew :app:installDebug
```

Walk the whole app once:
- **Reel:** swipe up and down; both modes; the mode toggle keeps the current quote; a tag chip filters; the filter sheet applies and clears; copy; share; a long quote clamps with "Read more"; force-stop and reopen resumes on the same quote.
- **Library:** search by quote text, by book name, by tag; two tag chips AND together; the count line updates; add, edit, delete with Undo; a duplicate save shows `This quote already exists`; blank text and blank book name show inline errors; book and tag autocomplete suggest previously used values.
- **Transfer:** export; re-import the export and see the duplicate count; import a non-database file and see the error with data intact.
- Toggle the system dark theme and confirm both schemes render.
- Turn on airplane mode and confirm everything still works — it should, since there is no network code at all.

- [ ] **Step 6: Final report**

Report the test count and confirm Steps 2–4 passed. Do not commit (see Global Constraints).

---

## Plan Self-Review

**Spec coverage.** Every spec section maps to at least one task: field requirements → Task 6 (`SaveQuote`); data model → Task 4; search and autocomplete queries → Task 5; reel deck, ordering source, pager, mode and position persistence → Tasks 3, 7, 11; screens and theme → Tasks 8–11; editor → Task 9; import → Task 12; export → Task 13; error handling → Tasks 6, 12, 13; the test plan → the test steps throughout; verification → Task 14. The spec's non-goals stay unimplemented.

**Deliberate deviations from the spec**, each flagged where it occurs:
1. **Font (Task 8).** Platform `FontFamily.Serif` instead of a bundled Literata. Same reading experience, no build-time font download, still fully offline, reversible in one line.
2. **Import timestamps (Task 12).** The spec says imported rows default to import time; this plan injects a `Clock` so that is deterministic under test.
3. **Filename source (Task 13, Step 7).** `LibraryViewModel` injects `DatabaseExporter` purely for `suggestedFileName()`, which crosses the `ui → domain ← data` rule. Called out with the three-line fix if you want the rule kept clean.

**Known risks, stated rather than hidden:**
- **Robolectric + FTS4** (Task 4, Step 7) is the riskiest environmental assumption here. The first test that touches the schema checks it, and the step carries a concrete fallback: move the DB-backed suites to `androidTest`. Tasks 5, 6, 12, and 13 inherit that decision.
- **Dependency versions** (Task 1, Step 4) are pinned to plausible values but not verified against a live repository. Step 8 fails loudly on any that do not resolve and tells you what to do.
- **material3 API drift** — `Modifier.menuAnchor()` (Task 9) and `DockedSearchBar`'s parameter list (Task 10) have both changed across recent material3 versions. Each is flagged with the alternative signature.

**Type consistency check.** `QuoteRow`, `Quote`, `QuoteDraft`, `Book`, `TagFilter`, `ReelFilter`, `ReelMode`, `ReelPersistedState`, `ValidationErrors`, `SaveQuoteResult`, `ImportResult`, `ImportOutcome`, `ExportOutcome`, and `TransferError` are each defined once and used with identical field names throughout. Every DAO method called in Tasks 6, 12, and 13 (`insertIgnoring`, `update`, `deleteById`, `entityById`, `entityByDedupeKey`, `rowById`, `searchRows`, `pagingSource`, `observeCount`, `observeMatchCount`, `observeReelIdsForShuffle`, `observeReelIdsForLinear`, `findByName`, `observeAll`, `suggest`, `link`, `unlinkAll`, `namesForQuote`, `observeFilters`, `deleteOrphans`, `upsert`, `delete`, `count`) traces back to a definition in Task 4 or Task 5. `DatabaseImporter`'s constructor takes `clock` from the moment it is written in Task 12 and is called with it in both Task 12's and Task 13's tests. `LibraryUiState.isTransferInProgress` is declared in Task 10 and first used in Task 13.
