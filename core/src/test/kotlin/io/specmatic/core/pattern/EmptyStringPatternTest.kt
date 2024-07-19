package io.specmatic.core.pattern

import io.specmatic.core.value.EmptyString
import io.specmatic.shouldMatch
import org.junit.jupiter.api.Test

internal class EmptyStringPatternTest {
    @Test
    fun `should match empty strings gracefully`() {
        EmptyString shouldMatch EmptyStringPattern
    }
}