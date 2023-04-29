package utilities

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import `in`.specmatic.core.utilities.*
import `in`.specmatic.core.utilities.URIUtils.parseQuery
import org.junit.jupiter.api.Assertions
import java.util.Collections.emptyMap

internal class URIUtilsTest {

    @Test
    fun given_ParseQuery_When_Null_Then_ThrowsException() {
        val resultMap = parseQuery(null)
        Assertions.assertEquals(resultMap, emptyMap<String, String>())
    }

    @Test
    fun `returns empty map when path params are empty`() {
        assertThat(URIUtils.parsePathParams("")).isEqualTo(emptyMap<String, String>())
    }

    @Test
    fun `returns map of path params`() {
        val url = "/product/(id:idTest)/variants/(variantId:variantIdTest)"
        assertThat(URIUtils.parsePathParams(url)).isEqualTo(mapOf("id" to "(idTest)", "variantId" to "(variantIdTest)"))
    }

    @Test
    fun `returns map of decoded query `() {
        val query = "status=(testStatus)&type=(testType)"
        assertThat(URIUtils.parseQuery(query)).isEqualTo(mapOf("status" to "(testStatus)", "type" to "(testType)"))
    }

    @Test
    fun `should throw exception when a part of query is not a key value pair `() {
        val exception = Assertions.assertThrows(Exception::class.java) {
            URIUtils.parseQuery("testQuery")
        }
        assertThat(exception.message).isEqualTo(
            """a part of the query string does not seem to be a key-value pair: testQuery"""
        )
    }
}
