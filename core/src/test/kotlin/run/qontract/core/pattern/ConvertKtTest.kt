package run.qontract.core.pattern

import org.junit.jupiter.api.Test
import run.qontract.core.shouldMatch
import run.qontract.core.shouldNotMatch

internal class ConvertKtTest {
    @Test
    fun `nullable number in string should be match either number in string or null`() {
        val pattern = parsedPattern("""{"number": "(number in string?)"}""")

        parsedValue("""{"number": "10"}""") shouldMatch pattern
        parsedValue("""{"number": 10}""") shouldNotMatch pattern
    }
}