package com.hc.rzi

import com.google.common.truth.Truth.assertThat
import org.junit.Test

class ScaffoldSanityTest {
    @Test
    fun `test infrastructure runs`() {
        assertThat(2 + 2).isEqualTo(4)
    }
}
