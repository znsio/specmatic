package `in`.specmatic.core

import `in`.specmatic.core.pattern.IgnoreUnexpectedKeys
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test

internal class CheckOnlyPatternKeysTest {
    @Test
    fun `checkOnlyPatternKeys should succeed when expected keys are all found`() {
        val expected = mapOf("expected" to "value")
        val actual = mapOf("expected" to "value")
        val missing = CheckOnlyPatternKeys.validate(expected, actual)

        assertThat(missing).isNull()
    }

    @Test
    fun `checkOnlyPatternKeys should catch missing expected keys`() {
        val expected = mapOf("expected" to "value")
        val actual = mapOf("unexpected" to "value")
        val missing = CheckOnlyPatternKeys.validate(expected, actual)

        assertThat(missing).isEqualTo(MissingKeyError("expected"))
    }

    @Test
    fun `checkOnlyPatternKeys should allow unexpected keys given the ellipsis key`() {
        val expected = mapOf("expected" to "value", "..." to "")
        val actual = mapOf("expected" to "value", "unexpected" to "value")
        val missing = CheckOnlyPatternKeys.validate(expected, actual)

        assertThat(missing).isEqualTo(null)
    }

    @Test
    fun `checkOnlyPatternKeys should allow missing optional expected keys`() {
        val expected = mapOf("expected-optional?" to "value")
        val actual = emptyMap<String, String>()
        val missing = CheckOnlyPatternKeys.validate(expected, actual)

        assertThat(missing).isEqualTo(null)
    }

    @Test
    fun `checkOnlyPatternKeys should match actual keys with expected optional keys`() {
        val expected = mapOf("expected-optional?" to "value")
        val actual = mapOf("expected-optional" to "value")
        val missing = CheckOnlyPatternKeys.validate(expected, actual)

        assertThat(missing).isEqualTo(null)
    }

    @Test
    fun `checkOnlyPatternKeys should match actual optional keys with expected optional keys`() {
        val expected = mapOf("expected-optional?" to "value")
        val actual = mapOf("expected-optional?" to "value")
        val missing = CheckOnlyPatternKeys.validate(expected, actual)

        assertThat(missing).isEqualTo(null)
    }

    @Test
    fun `checkOnlyPatternKeys should run the unexpected key strategy`() {
        val expected = mapOf("expected" to "value")
        val actual = mapOf("expected" to "value", "unexpected" to "value")
        val unexpected = CheckOnlyPatternKeys.validate(expected, actual, ValidateUnexpectedKeys)

        assertThat(unexpected).isEqualTo(UnexpectedKeyError("unexpected"))

        val error = CheckOnlyPatternKeys.validate(expected, actual, IgnoreUnexpectedKeys)
        assertThat(error).isNull()
    }
}