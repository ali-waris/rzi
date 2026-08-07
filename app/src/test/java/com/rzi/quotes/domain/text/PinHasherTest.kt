package com.rzi.quotes.domain.text

import com.google.common.truth.Truth.assertThat
import org.junit.Test

class PinHasherTest {

    @Test
    fun `hash is deterministic`() {
        assertThat(PinHasher.of("1234")).isEqualTo(PinHasher.of("1234"))
    }

    @Test
    fun `different pins produce different hashes`() {
        assertThat(PinHasher.of("1234")).isNotEqualTo(PinHasher.of("1235"))
        assertThat(PinHasher.of("0000")).isNotEqualTo(PinHasher.of("9999"))
    }

    @Test
    fun `hash is a 64 character hex string`() {
        val hash = PinHasher.of("1234")
        assertThat(hash).hasLength(64)
        assertThat(hash).matches("[0-9a-f]{64}")
    }

    @Test
    fun `valid pin is exactly four digits`() {
        assertThat(PinHasher.isValid("1234")).isTrue()
        assertThat(PinHasher.isValid("0000")).isTrue()
    }

    @Test
    fun `invalid pins are rejected`() {
        assertThat(PinHasher.isValid("")).isFalse()
        assertThat(PinHasher.isValid("123")).isFalse()
        assertThat(PinHasher.isValid("12345")).isFalse()
        assertThat(PinHasher.isValid("12a4")).isFalse()
        assertThat(PinHasher.isValid("123 ")).isFalse()
    }
}
