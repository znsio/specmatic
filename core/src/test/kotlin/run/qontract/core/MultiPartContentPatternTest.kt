package run.qontract.core

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import run.qontract.core.Result.Success
import run.qontract.core.pattern.Row
import run.qontract.core.pattern.StringPattern
import run.qontract.core.value.StringValue

internal class MultiPartContentPatternTest {
    @Test
    fun `match a multi part non-file part` () {
        val value = MultiPartContentValue("employeeid", StringValue("10"))
        val pattern = MultiPartContentPattern("employeeid", StringPattern)

        assertThat(pattern.matches(value, Resolver())).isInstanceOf(Success::class.java)
    }

    @Test
    fun `it should generate a new pattern`() {
        val pattern = MultiPartContentPattern("employeeid", StringPattern)
        val newPattern = pattern.newBasedOn(Row(), Resolver())
        assertThat(newPattern.single()).isEqualTo(pattern)
    }

    @Test
    fun `it should generate a new part`() {
        val pattern = MultiPartContentPattern("employeeid", StringPattern)
        val value = MultiPartContentValue("employeeid", StringValue("data"))
        assertThat(pattern.matches(value, Resolver())).isInstanceOf(Success::class.java)
    }
}