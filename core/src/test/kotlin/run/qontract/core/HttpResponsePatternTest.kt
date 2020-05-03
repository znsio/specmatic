package run.qontract.core

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test
import run.qontract.core.pattern.Row
import run.qontract.core.pattern.StringPattern

internal class HttpResponsePatternTest {
    @Test
    fun temp () {
        val list = HttpResponsePattern(status = 200, headersPattern = HttpHeadersPattern(mapOf("X-Optional?" to StringPattern))).newBasedOn(Row(), Resolver())

        assertThat(list).hasSize(2)

        val flags = list.map {
            when {
                it.headersPattern.pattern.containsKey("X-Optional") -> "with"
                else -> "without"
            }
        }

        flagsContain(flags, listOf("with", "without"))
    }
}