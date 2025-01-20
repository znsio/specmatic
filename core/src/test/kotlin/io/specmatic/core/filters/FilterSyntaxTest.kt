package io.specmatic.core.filters

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test

class FilterSyntaxTest {

    @Test
    fun `parse should return empty list for blank filter`() {
        val filterSyntax = FilterSyntax("")
        val result = filterSyntax.parse()
        assertTrue(result.isEmpty())
    }

    @Test
    fun `parse should handle simple equality condition`() {
        val filterSyntax = FilterSyntax("METHOD=POST")
        val result = filterSyntax.parse()

        assertEquals(1, result.size)
        val filterGroup = result[0]

        assertEquals(1, filterGroup.filters.size)
        assertTrue(filterGroup.filters[0] is FilterExpression.Equals)
        assertEquals("METHOD", (filterGroup.filters[0] as FilterExpression.Equals).key)
        assertEquals("POST", (filterGroup.filters[0] as FilterExpression.Equals).filterVal)
    }

    @Test
    fun `parse should handle simple inequality condition`() {
        val filterSyntax = FilterSyntax("STATUS!=200")
        val result = filterSyntax.parse()

        assertEquals(1, result.size)
        val filterGroup = result[0]

        assertEquals(1, filterGroup.filters.size)
        assertTrue(filterGroup.filters[0] is FilterExpression.NotEquals)
        assertEquals("STATUS", (filterGroup.filters[0] as FilterExpression.NotEquals).key)
        assertEquals("200", (filterGroup.filters[0] as FilterExpression.NotEquals).filterVal)
    }

    @Test
    fun `parse should handle multiple conditions with AND`() {
        val filterSyntax = FilterSyntax("METHOD=POST && PATH=/users")
        val result = filterSyntax.parse()

        assertEquals(1, result.size)
        val filterGroup = result[0]

        assertEquals(2, filterGroup.filters.size)
    }

    @Test
    fun `parse should handle multiple conditions with OR`() {
        val filterSyntax = FilterSyntax("METHOD=POST || PATH=/users")
        val result = filterSyntax.parse()

        assertEquals(2, result.size)
        assertEquals("METHOD", (result[0].filters[0] as FilterExpression.Equals).key)
        assertEquals("PATH", (result[1].filters[0] as FilterExpression.Equals).key)
    }

    @Test
    fun `parse should handle nested conditions`() {
        val filterSyntax = FilterSyntax("(METHOD=POST && PATH=/users) || STATUS=202")
        val result = filterSyntax.parse()

        assertEquals(2, result.size)

        val group1 = result[0]
        assertEquals(2, group1.filters.size)

        val group2 = result[1]
        assertEquals(1, group2.filters.size)
    }

    @Test
    fun `parse should handle negation`() {
        val filterSyntax = FilterSyntax("!(METHOD=POST)")
        val result = filterSyntax.parse()

        assertEquals(1, result.size)
        val group = result[0]

        assertTrue(group.isNegated)
        assertEquals(1, group.filters.size)
    }

    @Test
    fun `parse should handle range-based condition`() {
        val filterSyntax = FilterSyntax("STATUS=2xx")
        val result = filterSyntax.parse()

        assertEquals(1, result.size)
        val group = result[0]

        assertTrue(group.filters[0] is FilterExpression.Range)
        val rangeFilter = group.filters[0] as FilterExpression.Range
        assertEquals("STATUS", rangeFilter.key)
        assertEquals(200, rangeFilter.start)
        assertEquals(299, rangeFilter.end)
    }

    @Test
    fun `isValidFilter should return false for invalid logical operator usage`() {
        val filterSyntax = FilterSyntax("key=value && || key2=value2")
        val exception = org.junit.jupiter.api.assertThrows<Exception> {
            filterSyntax.validateFilter()
        }
        assertThat(exception.message).isEqualTo("Expression is incorrect")
    }

    @Test
    fun `isValidFilter should return true for valid filter`() {
        val filterSyntax = FilterSyntax("METHOD=POST && PATH!=/users")
        assertDoesNotThrow {
            filterSyntax.validateFilter()
        }
    }

    @Test
    fun `evalEx filter validate test 1`() {
        val filterSyntax = FilterSyntax("((METHOD=\"POST\" && STATUS=200) || PATH!=\"/users\")")
        assertDoesNotThrow {
            filterSyntax.validateFilter()
        }
    }

    @Test
    fun `evalEx filter validate test 1 failure (missing curly braces)`() {
        val filterSyntax = FilterSyntax("((METHOD=\"POST\" && STATUS=200) || PATH!=\"/users\"")
        val exception = org.junit.jupiter.api.assertThrows<Exception> {
            filterSyntax.validateFilter()
        }
        assertThat(exception.message).isEqualTo("Expression is incorrect")
    }

    @Test
    fun `evalEx filter validate test 2 (Status code wildcard)`(){
        val filterSyntax = FilterSyntax("STATUS!=50x")
        assertDoesNotThrow {
            filterSyntax.validateFilter()
        }
    }
    @Test
    fun `evalEx filter validate test 4 (multiple checks)`(){
        val filterSyntax = FilterSyntax("(STATUS!=202 || STATUS!=400) && !(PATH=\"/users\" && METHOD=\"POST\") && !(PATH=\"/products\" && METHOD=\"POST\") && STATUS!=5xx")
        assertDoesNotThrow {
            filterSyntax.validateFilter()
        }
    }


    @Test
    fun `evalEx filter evaluate t1`(){
        val filterSyntax = FilterSyntax("STATUS!=50x")
        val exception = org.junit.jupiter.api.assertThrows<Exception> {
            filterSyntax.evaluateFilter()
        }
        assertThat(exception.message).isEqualTo("Expression is incorrect")
    }

    @Test
    fun `evalEx filter evaluate t2`(){
        val filterSyntax = FilterSyntax("STATUS=200,400")
        val exception = org.junit.jupiter.api.assertThrows<Exception> {
            filterSyntax.evaluateFilter()
        }
        assertThat(exception.message).isEqualTo("Expression is incorrect")
    }

    @Test
    fun `evalEx filter evaluate t3`(){
        val filterSyntax = FilterSyntax("METHOD=\"POST\" && STATUS=200")
        assertDoesNotThrow {
            filterSyntax.evaluateFilter()
        }
    }

    @Test
    fun `parse should handle conditions with special characters in value`() {
        val filterSyntax = FilterSyntax("PATH=/users!@#")
        val result = filterSyntax.parse()

        assertEquals(1, result.size)
        val group = result[0]

        assertTrue(group.filters[0] is FilterExpression.Equals)
        val equalsFilter = group.filters[0] as FilterExpression.Equals
        assertEquals("PATH", equalsFilter.key)
        assertEquals("/users!@#", equalsFilter.filterVal)
    }

    @Test
    fun `parse should handle logical operators without spaces`() {
        val filterSyntax = FilterSyntax("METHOD=POST&&PATH=/users||STATUS=200")
        val result = filterSyntax.parse()

        assertEquals(2, result.size)
        assertEquals("STATUS", (result[1].filters[0] as FilterExpression.Equals).key)
    }

    @Test
    fun `parse should return empty list for unsupported operators`() {
        val filterSyntax = FilterSyntax("key1=value1 >> key2=value2")
        val result = filterSyntax.parse()
        assertTrue(result.isEmpty())
    }
}
