package io.specmatic.core

import com.jayway.jsonpath.JsonPath
import com.jayway.jsonpath.PathNotFoundException
import io.specmatic.core.examples.server.findLineNumber
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import java.io.File

class FindLineNumberTest {

    @Test
    fun `should return line number for valid jsonPath and file`() {
        val jsonFile = File("src/test/resources/openapi/find_line_number_tests/test.json")
        val jsonPath = JsonPath.compile("$.store.book[0].author")
        val lineNumber = findLineNumber(jsonFile, jsonPath)
        assertNotNull(lineNumber)
        assertEquals(6, lineNumber)
    }

    @Test
    fun `should return null when no matching jsonPath is found`() {
        val jsonFile = File("src/test/resources/openapi/find_line_number_tests/test.json")
        val jsonPath = JsonPath.compile("$.store.book[1000]")
        val lineNumber = findLineNumber(jsonFile, jsonPath)
        assertNull(lineNumber)
    }

    @Test
    fun `should handle invalid jsonPath expression gracefully`() {
        val jsonFile = File("src/test/resources/openapi/find_line_number_tests/test.json")
        val invalidJsonPath = JsonPath.compile("$.store.books")
        assertThrows<PathNotFoundException> {
            findLineNumber(jsonFile, invalidJsonPath)
        }
    }

    @Test
    fun `should handle an empty JSON file`() {
        val jsonFile = File("src/test/resources/openapi/find_line_number_tests/empty.json")
        val jsonPath = JsonPath.compile("$.store.book[0].author")
        assertThrows<PathNotFoundException> {
            findLineNumber(jsonFile, jsonPath)
        }
    }
}
