package `in`.specmatic.core.pattern

import org.junit.jupiter.api.Test
import `in`.specmatic.core.value.EmptyString
import `in`.specmatic.shouldMatch

internal class EmptyStringPatternTest {
    @Test
    fun `should match empty strings gracefully`() {
        EmptyString shouldMatch EmptyStringPattern
    }
}