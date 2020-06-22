package run.qontract.core.pattern

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import run.qontract.core.value.StringValue

internal class GrammarKtTest {
    @Test
    fun `value starting with a brace which is not json parses to string value`() {
        assertThat(parsedValue("""{one""")).isInstanceOf(StringValue::class.java)
    }

    @Test
    fun `value starting with a square bracket which is not json parses to string value`() {
        assertThat(parsedValue("""[one""")).isInstanceOf(StringValue::class.java)
    }

    @Test
    fun `value starting with an angular bracket which is not json parses to string value`() {
        assertThat(parsedValue("""<one""")).isInstanceOf(StringValue::class.java)
    }

    @Test
    fun `pattern in string is parsed as such`() {
        val type = parsedPattern("(name:string)")
        assertThat(type).isEqualTo(LookupRowPattern(StringPattern, "name"))
    }

    @Test
    fun `unknown pattern is parsed as deferred`() {
        val type = parsedPattern("(name string)")
        assertThat(type).isEqualTo(DeferredPattern("(name string)"))
    }
}