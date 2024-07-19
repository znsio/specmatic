package io.specmatic.core

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import io.specmatic.core.pattern.AnyPattern
import io.specmatic.core.pattern.NumberPattern
import io.specmatic.core.pattern.Row
import io.specmatic.core.pattern.StringPattern

internal class HttpRequestPatternKtTest {
    @Test
    fun `when generating new content part types with two value options there should be two types generated`() {
        val multiPartTypes = listOf(MultiPartContentPattern(
            "data",
            AnyPattern(listOf(StringPattern(), NumberPattern())),
        ))

        val newTypes = newMultiPartBasedOn(multiPartTypes, Row(), Resolver()).toList()

        assertThat(newTypes).hasSize(2)

        assertThat(newTypes).contains(listOf(MultiPartContentPattern("data", NumberPattern())))
        assertThat(newTypes).contains(listOf(MultiPartContentPattern("data", StringPattern())))
    }

    @Test
    fun `when a part is optional there should be two lists generated in which one has the part and the other does not`() {
        val multiPartTypes = listOf(MultiPartContentPattern("data?", StringPattern()))

        val newTypes = newMultiPartBasedOn(multiPartTypes, Row(), Resolver()).toList()

        assertThat(newTypes).hasSize(2)

        assertThat(newTypes).contains(listOf(MultiPartContentPattern("data", StringPattern())))
        assertThat(newTypes).contains(emptyList())
    }
}