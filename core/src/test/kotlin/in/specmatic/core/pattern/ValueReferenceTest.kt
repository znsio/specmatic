package `in`.specmatic.core.pattern

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test

internal class ValueReferenceTest {
    @Test
    fun `removes suffixes and prefixes leaving the refernce name alone`() {
        assertThat(ValueReference("(${DEREFERENCE_PREFIX}username)").name).isEqualTo("username")
    }
}
