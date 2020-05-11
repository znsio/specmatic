package run.qontract.core.pattern

import org.junit.jupiter.api.Test
import run.qontract.core.Resolver
import run.qontract.core.shouldMatch
import run.qontract.core.shouldNotMatch
import run.qontract.core.value.NullValue
import run.qontract.core.value.XMLValue

internal class XMLPatternTest {
    @Test
    fun `should fail to match nulls gracefully`() {
        NullValue shouldNotMatch XMLPattern("<data></data>")
    }

    @Test
    fun `should match a number within a structure`() {
        XMLValue("<outer><inner>1</inner></outer>") shouldMatch XMLPattern("<outer><inner>(number)</inner></outer>")
    }

    @Test
    fun `should not match a value that doesn't conform to the specified type`() {
        XMLValue("<outer><inner>abc</inner></outer>") shouldNotMatch XMLPattern("<outer><inner>(number)</inner></outer>")
    }

    @Test
    fun temp() {
        val numberInfoPattern = XMLPattern("<number>(number)</number>")
        val resolver = Resolver(newPatterns = mapOf("(NumberInfo)" to numberInfoPattern))
        val answerPattern = XMLPattern("<answer>(NumberInfo...)</answer>")
        val value = XMLValue("<answer><number>10</number></answer>")

        resolver.matchesPattern(null, answerPattern, value)
    }
}
