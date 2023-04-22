package `in`.specmatic.core.utilities

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Assertions
import org.junit.jupiter.api.Test

internal class URIUtilsTest {
    @Test
    fun `returns map of path params`() {
        val url = "/product/(id:testId)/variants/(variantId:testVariantId)"
        assertThat(URIUtils.parsePathParams(url)).isEqualTo(mapOf("id" to "(testId)", "variantId" to "(testVariantId)"))
    }

    @Test
    fun `returns empty map when path params are null`() {
        assertThat(URIUtils.parsePathParams("")).isEqualTo(emptyMap<String, String>())
    }

    @Test
    fun `returns empty map when query is null `() {
        assertThat(URIUtils.parseQuery(null)).isEqualTo(emptyMap<String, String>())
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