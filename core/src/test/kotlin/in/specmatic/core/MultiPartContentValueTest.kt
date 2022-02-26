package `in`.specmatic.core

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import `in`.specmatic.core.pattern.ExactValuePattern
import `in`.specmatic.core.value.StringValue

internal class MultiPartContentValueTest {
    @Test
    fun `should generate a matching pattern`() {
        val pattern = MultiPartContentValue("some name", StringValue("data")).inferType() as MultiPartContentPattern
        assertThat(pattern.name).isEqualTo("some name")
        assertThat(pattern.content).isEqualTo(ExactValuePattern(StringValue("data")))
        assertThat(pattern.contentType).isEqualTo("text/plain")
    }

    @Test
    fun `should generate a matching pattern with contentType`() {
        val pattern = MultiPartContentValue("some name", StringValue("data"), specifiedContentType = "application/json").inferType() as MultiPartContentPattern
        assertThat(pattern.name).isEqualTo("some name")
        assertThat(pattern.content).isEqualTo(ExactValuePattern(StringValue("data")))
        assertThat(pattern.contentType).isEqualTo("application/json")
    }
}