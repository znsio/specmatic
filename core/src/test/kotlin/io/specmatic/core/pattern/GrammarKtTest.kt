package io.specmatic.core.pattern

import io.specmatic.core.value.StringValue
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.fail
import java.util.function.Consumer

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
        assertThat(type).isEqualTo(LookupRowPattern(StringPattern(), "name"))
    }

    @Test
    fun `unknown pattern is parsed as deferred`() {
        val type = parsedPattern("(name string)")
        assertThat(type).isEqualTo(DeferredPattern("(name string)"))
    }

    @Test
    fun `The type contained in the string should be used as is as the type name`() {
        val type: Pattern = getBuiltInPattern("(JSONDataStructure in string)")

        if(type !is PatternInStringPattern)
            fail("Expected pattern in string")

        assertThat(type.pattern.typeName).isEqualTo("JSONDataStructure")
    }

    @Nested
    inner class CustomEmptyStringMessage {
        @Test
        fun `should show custom empty string message when trying to parse it as json object`() {
            assertThatThrownBy {
                parsedJSONObject("")
            }.satisfies(Consumer {
                it as ContractException
                assertThat(it.report()).contains("an empty string")
            })
        }

        @Test
        fun `should show custom empty string message when trying to parse it as json array`() {
            assertThatThrownBy {
                parsedJSONArray("")
            }.satisfies(Consumer {
                it as ContractException
                assertThat(it.report()).contains("an empty string")
            })
        }

        @Test
        fun `should show custom empty string message when trying to parse it as json value`() {
            assertThatThrownBy {
                parsedJSON("")
            }.satisfies(Consumer {
                it as ContractException
                assertThat(it.report()).contains("an empty string")
            })
        }
    }
}