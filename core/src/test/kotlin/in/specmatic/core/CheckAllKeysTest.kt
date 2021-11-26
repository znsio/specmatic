package `in`.specmatic.core

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test

internal class CheckAllKeysTest {
    private fun _checkAllKeys(pattern: Map<String, Any>, actual: Map<String, Any>): KeyError? =
        CheckAllKeys.validate(pattern, actual)

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
        val unexpected = _checkAllKeys(expected, actual)

        assertThat(unexpected).isEqualTo(UnexpectedKeyError("unexpected"))
    }
}