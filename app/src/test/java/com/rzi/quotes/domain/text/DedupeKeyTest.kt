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
