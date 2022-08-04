package `in`.specmatic.core

import `in`.specmatic.core.pattern.IgnoreUnexpectedKeys
import `in`.specmatic.core.pattern.Pattern
import `in`.specmatic.core.value.StringValue
import io.mockk.every
import io.mockk.mockk
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test

internal class KeyCheckTest {
    @Test
    fun `should invoke the pattern key check`() {
        val result = KeyCheck(patternKeyCheck = object: KeyErrorCheck {
            override fun validate(pattern: Map<String, Any>, actual: Map<String, Any>): KeyError {
                return MissingKeyError("test")
            }

            override fun validateList(pattern: Map<String, Any>, actual: Map<String, Any>): List<KeyError> {
                return listOf(MissingKeyError("test"))
            }

            override fun validateListCaseInsensitive(
                pattern: Map<String, Pattern>,
                actual: Map<String, StringValue>
            ): List<KeyError> {
                TODO("Not yet implemented")
            }

        }).validate(emptyMap(), emptyMap())

        assertThat(result?.name).isEqualTo("test")
    }

    @Test
    fun `should invoke the unexpected key check if the pattern key check returns nothing`() {
        val result = KeyCheck(patternKeyCheck = object: KeyErrorCheck {
            override fun validate(pattern: Map<String, Any>, actual: Map<String, Any>): KeyError? {
                return null
            }

            override fun validateList(pattern: Map<String, Any>, actual: Map<String, Any>): List<KeyError> {
                return emptyList()
            }

            override fun validateListCaseInsensitive(
                pattern: Map<String, Pattern>,
                actual: Map<String, StringValue>
            ): List<KeyError> {
                TODO("Not yet implemented")
            }

        }, unexpectedKeyCheck = object: UnexpectedKeyCheck {
            override fun validate(pattern: Map<String, Any>, actual: Map<String, Any>): UnexpectedKeyError {
                return UnexpectedKeyError("test")
            }

            override fun validateList(pattern: Map<String, Any>, actual: Map<String, Any>): List<UnexpectedKeyError> {
                return listOf(UnexpectedKeyError("test"))
            }

            override fun validateListCaseInsensitive(
                pattern: Map<String, Pattern>,
                actual: Map<String, StringValue>
            ): List<UnexpectedKeyError> {
                TODO("Not yet implemented")
            }

        }).validate(emptyMap(), emptyMap())

        assertThat(result?.name).isEqualTo("test")
    }

    @Test
    fun `override the unexpected key check`() {
        val checker = KeyCheck(patternKeyCheck = object: KeyErrorCheck {
            override fun validate(pattern: Map<String, Any>, actual: Map<String, Any>): KeyError? {
                return null
            }

            override fun validateList(pattern: Map<String, Any>, actual: Map<String, Any>): List<KeyError> {
                return emptyList()
            }

            override fun validateListCaseInsensitive(
                pattern: Map<String, Pattern>,
                actual: Map<String, StringValue>
            ): List<KeyError> {
                TODO("Not yet implemented")
            }

        }, unexpectedKeyCheck = object: UnexpectedKeyCheck {
            override fun validate(pattern: Map<String, Any>, actual: Map<String, Any>): UnexpectedKeyError {
                return UnexpectedKeyError("test")
            }

            override fun validateList(pattern: Map<String, Any>, actual: Map<String, Any>): List<UnexpectedKeyError> {
                return listOf(UnexpectedKeyError("test"))
            }

            override fun validateListCaseInsensitive(
                pattern: Map<String, Pattern>,
                actual: Map<String, StringValue>
            ): List<UnexpectedKeyError> {
                TODO("Not yet implemented")
            }

        }).withUnexpectedKeyCheck(IgnoreUnexpectedKeys)

        val result = checker.validate(emptyMap(), emptyMap())

        assertThat(result?.name).isNull()
    }

    @Test
    fun `prevent overriding of the unexpected key check`() {
        val checker = KeyCheck(patternKeyCheck = object: KeyErrorCheck {
            override fun validate(pattern: Map<String, Any>, actual: Map<String, Any>): KeyError? {
                return null
            }

            override fun validateList(pattern: Map<String, Any>, actual: Map<String, Any>): List<KeyError> {
                return emptyList()
            }

            override fun validateListCaseInsensitive(
                pattern: Map<String, Pattern>,
                actual: Map<String, StringValue>
            ): List<KeyError> {
                TODO("Not yet implemented")
            }

        }, unexpectedKeyCheck = object: UnexpectedKeyCheck {
            override fun validate(pattern: Map<String, Any>, actual: Map<String, Any>): UnexpectedKeyError {
                return UnexpectedKeyError("test")
            }

            override fun validateList(pattern: Map<String, Any>, actual: Map<String, Any>): List<UnexpectedKeyError> {
                return listOf(UnexpectedKeyError("test"))
            }

            override fun validateListCaseInsensitive(
                pattern: Map<String, Pattern>,
                actual: Map<String, StringValue>
            ): List<UnexpectedKeyError> {
                TODO("Not yet implemented")
            }

        }).disableOverrideUnexpectedKeycheck().withUnexpectedKeyCheck(IgnoreUnexpectedKeys)

        val result = checker.validate(emptyMap(), emptyMap())

        assertThat(result?.name).isEqualTo("test")
    }

    @Test
    fun `should invoke case insensitive matchers`() {
        val keyErrorCheck = mockk<KeyErrorCheck>()

        every {
            keyErrorCheck.validateListCaseInsensitive(any(), any())
        }.returns(listOf(MissingKeyError("key1")))

        val unexpectedKeyCheck = mockk<UnexpectedKeyCheck>()

        every {
            unexpectedKeyCheck.validateListCaseInsensitive(any(), any())
        }.returns(listOf(UnexpectedKeyError("key2")))

        val errors = KeyCheck(keyErrorCheck, unexpectedKeyCheck).validateAllCaseInsensitive(emptyMap(), emptyMap())

        assertThat(errors[0].name).isEqualTo("key1")
        assertThat(errors[1].name).isEqualTo("key2")
    }
}
