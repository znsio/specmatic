package `in`.specmatic.core

import `in`.specmatic.core.pattern.NullPattern
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test

internal class ValidateUnexpectedKeysTest {
    @Test
    fun `flags unexpected keys`() {
        val expected = mapOf("hello" to NullPattern)
        val actual = mapOf("world" to NullPattern)

        val error = ValidateUnexpectedKeys.validate(expected, actual)
        assertThat(error?.name).isEqualTo("world")
        assertThat(error).isInstanceOf(UnexpectedKeyError::class.java)
    }

    @Test
    fun `flags unexpected keys and leaves expxected alone`() {
        val expected = mapOf("hello" to NullPattern)
        val actual = mapOf("hello" to NullPattern, "world" to NullPattern)

        val error = ValidateUnexpectedKeys.validate(expected, actual)
        assertThat(error?.name).isEqualTo("world")
        assertThat(error).isInstanceOf(UnexpectedKeyError::class.java)
    }

    @Test
    fun `does not return any error when all the keys are expected`() {
        val expected = mapOf("hello" to NullPattern)
        val actual = mapOf("hello" to NullPattern)

        val error = ValidateUnexpectedKeys.validate(expected, actual)
        assertThat(error).isNull()
    }
}
