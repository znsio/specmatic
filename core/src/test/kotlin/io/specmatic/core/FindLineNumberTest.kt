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
        val jsonPath = "http-response.body.text"
        val lineNumber = findLineNumber(jsonFile, jsonPath)
        assertNotNull(lineNumber)
        assertEquals(10, lineNumber)
    }

    @Test
    fun `should return parent node line number when no matching jsonPath is found`() {
        val jsonFile = File("src/test/resources/openapi/find_line_number_tests/test.json")
        val jsonPath = "http-response.body.texts"
        val lineNumber = findLineNumber(jsonFile, jsonPath)
        assertEquals(9, lineNumber)
    }

    @Test
    fun `should handle invalid jsonPath expression gracefully`() {
        val jsonFile = File("src/test/resources/openapi/find_line_number_tests/test.json")
        val invalidJsonPath = ("http-response.hello")
        val lineNumber = findLineNumber(jsonFile, invalidJsonPath)
        assertEquals(7, lineNumber)
    }

//    @Test
//    fun `should handle an empty JSON file`() {
//        val jsonFile = File("src/test/resources/openapi/find_line_number_tests/empty.json")
//        val jsonPath = JsonPath.compile("$.store.book[0].author")
//        assertThrows<PathNotFoundException> {
//            findLineNumber(jsonFile, jsonPath)
//        }

}
