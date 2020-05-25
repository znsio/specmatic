package run.qontract.core

import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.junit.jupiter.api.Test
import run.qontract.core.pattern.ContractException

internal class ResolverTest {
    @Test
    fun `checkAllKeys should succeed when expected keys are all found`() {
        val expected = mapOf("expected" to "value")
        val actual = mapOf("expected" to "value")
        val missing = checkAllKeys(expected, actual)

        assertThat(missing).isNull()
    }

    @Test
    fun `checkAllKeys should catch missing expected keys`() {
        val expected = mapOf("expected" to "value")
        val actual = mapOf("unexpected" to "value")
        val missing = checkAllKeys(expected, actual)

        assertThat(missing).isEqualTo(Pair("expected", null))
    }

    @Test
    fun `checkAllKeys should catch unexpected keys`() {
        val expected = mapOf("expected" to "value")
        val actual = mapOf("expected" to "value", "unexpected" to "value")
        val missing = checkAllKeys(expected, actual)

        assertThat(missing).isEqualTo(Pair(null, "unexpected"))
    }

    @Test
    fun `checkAllKeys should allow missing optional expected keys`() {
        val expected = mapOf("expected-optional?" to "value")
        val actual = emptyMap<String, String>()
        val missing = checkAllKeys(expected, actual)

        assertThat(missing).isEqualTo(null)
    }

    @Test
    fun `checkAllKeys should match actual keys with expected optional keys`() {
        val expected = mapOf("expected-optional?" to "value")
        val actual = mapOf("expected-optional" to "value")
        val missing = checkAllKeys(expected, actual)

        assertThat(missing).isEqualTo(null)
    }

    @Test
    fun `checkAllKeys should match actual optional keys with expected optional keys`() {
        val expected = mapOf("expected-optional?" to "value")
        val actual = mapOf("expected-optional?" to "value")
        val missing = checkAllKeys(expected, actual)

        assertThat(missing).isEqualTo(null)
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

        assertThat(missing).isEqualTo(Pair("expected", null))
    }

    @Test
    fun `checkOnlyPatternKeys should allow unexpected keys`() {
        val expected = mapOf("expected" to "value")
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
    fun `it should throw an exception when the request pattern does not exist`() {
        assertThatThrownBy { Resolver().getPattern("(NonExistentPattern)") }.isInstanceOf(ContractException::class.java)
    }
}
