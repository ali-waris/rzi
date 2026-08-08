package com.hc.rzi.domain.text

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
