package `in`.specmatic.core.utilities

import `in`.specmatic.core.value.StringValue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.Assertions.*

class UtilitiesTest {

    @Test
    fun parseXML() {
        val xml = """
            <root>
                <element>Specmatic</element>
            </root>
        """.trimIndent()
        val result = parseXML(xml)
        assertEquals("root", result.documentElement.nodeName)
        assertEquals("Specmatic", result.documentElement.firstChild.textContent)
    }


    @Test
    fun strings() {
        val input0 = listOf<StringValue>()
        val result0 = listOf<String>()
        assertEquals(result0, strings(input0))

        val input1 = listOf(StringValue("hello"), StringValue("world"), StringValue("123"))
        val result1 = listOf("hello", "world", "123")
        assertEquals(result1, strings(input1))
    }

    @Test
    fun capitalizeFirstChar() {
        val string0 = ""
        val result0 = ""
        assertEquals(result0, string0.capitalizeFirstChar())

        val string1 = "F"
        val result1 = "F"
        assertEquals(result1, string1.capitalizeFirstChar())

        val string2 = "f"
        val result2 = "F"
        assertEquals(result2, string2.capitalizeFirstChar())

        val string3 = "3irst"
        val result3 = "3irst"
        assertEquals(result3, string3.capitalizeFirstChar())
    }
}