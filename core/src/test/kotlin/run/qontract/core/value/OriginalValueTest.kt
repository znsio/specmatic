package run.qontract.core.value

import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test
import run.qontract.core.value.OriginalValue

internal class OriginalValueTest {
    @Test
    fun `stringizing should yield the value provided in input, in string form`() {
        assertEquals("10", OriginalValue(10).toString())
    }
}