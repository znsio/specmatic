package `in`.specmatic.core

import org.assertj.core.api.Assertions.*
import org.junit.jupiter.api.Test
import `in`.specmatic.core.pattern.ContractException
import `in`.specmatic.core.pattern.ignoreUnexpectedKeys

internal class ResolverKtTest {
    private fun _checkAllKeys(pattern: Map<String, Any>, actual: Map<String, Any>, ignored: UnexpectedKeyCheck = ::validateUnexpectedKeys): KeyError? =
            checkAllKeys(pattern, actual, ignored)

    @Test
    fun `checkAllKeys should succeed when expected keys are all found`() {
        val expected = mapOf("expected" to "value")
        val actual = mapOf("expected" to "value")
        val missing = _checkAllKeys(expected, actual)

        assertThat(missing).isNull()
    }

    @Test
    fun `checkAllKeys should catch missing expected keys`() {
        val expected = mapOf("expected" to "value")
        val actual = mapOf("unexpected" to "value")
        val missing = _checkAllKeys(expected, actual)

        assertThat(missing).isEqualTo(MissingKeyError("expected"))
    }

    @Test
    fun `checkAllKeys should catch unexpected keys`() {
        val expected = mapOf("expected" to "value")
        val actual = mapOf("expected" to "value", "unexpected" to "value")
        val missing = _checkAllKeys(expected, actual)

        assertThat(missing).isEqualTo(UnexpectedKeyError("unexpected"))
    }

    @Test
    fun `checkAllKeys should allow missing optional expected keys`() {
        val expected = mapOf("expected-optional?" to "value")
        val actual = emptyMap<String, String>()
        val missing = _checkAllKeys(expected, actual)

        assertThat(missing).isEqualTo(null)
    }

    @Test
    fun `checkAllKeys should match actual keys with expected optional keys`() {
        val expected = mapOf("expected-optional?" to "value")
        val actual = mapOf("expected-optional" to "value")
        val missing = _checkAllKeys(expected, actual)

        assertThat(missing).isEqualTo(null)
    }

    @Test
    fun `checkAllKeys should match actual optional keys with expected optional keys`() {
        val expected = mapOf("expected-optional?" to "value")
        val actual = mapOf("expected-optional?" to "value")
        val missing = _checkAllKeys(expected, actual)

        assertThat(missing).isEqualTo(null)
    }

    @Test
    fun `checkAllKeys should return unexpected keys regardless of what strategy is passed to it`() {
        val expected = mapOf("expected" to "value")
        val actual = mapOf("expected" to "value", "unexpected" to "value")
        val unexpected = _checkAllKeys(expected, actual, ignoreUnexpectedKeys)

        assertThat(unexpected).isEqualTo(UnexpectedKeyError("unexpected"))
    }

    @Test
    fun `checkOnlyPatternKeys should succeed when expected keys are all found`() {
        val expected = mapOf("expected" to "value")
        val actual = mapOf("expected" to "value")
        val missing = checkOnlyPatternKeys(expected, actual)

        assertThat(missing).isNull()
    }

    @Test
    fun `checkOnlyPatternKeys should catch missing expected keys`() {
        val expected = mapOf("expected" to "value")
        val actual = mapOf("unexpected" to "value")
        val missing = checkOnlyPatternKeys(expected, actual)

        assertThat(missing).isEqualTo(MissingKeyError("expected"))
    }

    @Test
    fun `checkOnlyPatternKeys should allow unexpected keys given the ellipsis key`() {
        val expected = mapOf("expected" to "value", "..." to "")
        val actual = mapOf("expected" to "value", "unexpected" to "value")
        val missing = checkOnlyPatternKeys(expected, actual)

        assertThat(missing).isEqualTo(null)
    }

    @Test
    fun `checkOnlyPatternKeys should allow missing optional expected keys`() {
        val expected = mapOf("expected-optional?" to "value")
        val actual = emptyMap<String, String>()
        val missing = checkOnlyPatternKeys(expected, actual)

        assertThat(missing).isEqualTo(null)
    }

    @Test
    fun `checkOnlyPatternKeys should match actual keys with expected optional keys`() {
        val expected = mapOf("expected-optional?" to "value")
        val actual = mapOf("expected-optional" to "value")
        val missing = checkOnlyPatternKeys(expected, actual)

        assertThat(missing).isEqualTo(null)
    }

    @Test
    fun `checkOnlyPatternKeys should match actual optional keys with expected optional keys`() {
        val expected = mapOf("expected-optional?" to "value")
        val actual = mapOf("expected-optional?" to "value")
        val missing = checkOnlyPatternKeys(expected, actual)

        assertThat(missing).isEqualTo(null)
    }

    @Test
    fun `checkOnlyPatternKeys should run the unexpected key strategy`() {
        val expected = mapOf("expected" to "value")
        val actual = mapOf("expected" to "value", "unexpected" to "value")
        val unexpected = checkOnlyPatternKeys(expected, actual, ::validateUnexpectedKeys)

        assertThat(unexpected).isEqualTo(UnexpectedKeyError("unexpected"))

        val error = checkOnlyPatternKeys(expected, actual, ignoreUnexpectedKeys)
        assertThat(error).isNull()
    }

    @Test
    fun `it should throw an exception when the request pattern does not exist`() {
        assertThatThrownBy { Resolver().getPattern("(NonExistentPattern)") }.isInstanceOf(ContractException::class.java)
    }
}