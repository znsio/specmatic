package run.qontract.core

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import run.qontract.core.pattern.ExactValuePattern
import run.qontract.core.value.StringValue

internal class MultiPartContentValueTest {
    @Test
    fun `should generate a matching pattern`() {
        val pattern = MultiPartContentValue("some name", StringValue("data")).inferType() as MultiPartContentPattern
        assertThat(pattern.name).isEqualTo("some name")
        assertThat(pattern.content).isEqualTo(ExactValuePattern(StringValue("data")))
    }
}