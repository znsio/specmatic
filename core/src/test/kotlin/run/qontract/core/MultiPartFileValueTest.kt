package run.qontract.core

import org.assertj.core.api.Assertions
import org.junit.jupiter.api.Test

internal class MultiPartFileValueTest {
    @Test
    fun `should generate a matching pattern`() {
        val pattern = MultiPartFileValue("some name", "@customers.csv", "text/csv", "gzip").toPattern() as MultiPartFilePattern
        Assertions.assertThat(pattern.name).isEqualTo("some name")
        Assertions.assertThat(pattern.filename).isEqualTo("@customers.csv")
        Assertions.assertThat(pattern.contentType).isEqualTo("text/csv")
        Assertions.assertThat(pattern.contentEncoding).isEqualTo("gzip")
    }
}