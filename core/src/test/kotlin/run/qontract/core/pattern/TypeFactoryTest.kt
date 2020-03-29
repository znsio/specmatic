package run.qontract.core.pattern

import run.qontract.core.Resolver
import run.qontract.core.value.XMLValue
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import run.qontract.core.value.JSONObjectValue

class TypeFactoryTest {
    @Test
    fun `should return JSON document for JSON string`() {
        val bodyContent = """ {hello: "world"} """
        val pattern = parsedPattern(bodyContent)

        assertTrue(pattern is JSONObjectPattern)
        val value = pattern.generate(Resolver())

        assertNotNull(value)

        assertTrue(value is JSONObjectValue)
    }

    @Test
    fun `should return XML document for XML string`() {
        val pattern = parsedPattern("<hello>world</hello>")
        assertTrue(pattern is XMLPattern)
        val value = pattern.generate(Resolver())

        assertNotNull(value)

        assertTrue(value is XMLValue)
    }
}