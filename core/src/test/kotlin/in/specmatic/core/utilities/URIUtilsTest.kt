package `in`.specmatic.core.utilities

import org.junit.jupiter.api.Test
import org.junit.jupiter.api.Assertions.*

class URIUtilsTest {
    @Test
    fun parseQuery() {

        val query0 = null
        val result0 = emptyMap<String, String>()
        assertEquals(result0, URIUtils.parseQuery(query0))

        val query1 = "pageSize=4"
        val result1 = mapOf("pageSize" to "4")
        assertEquals(result1, URIUtils.parseQuery(query1))

        val query2 = "offset=0&search=specmatic"
        val result2 = mapOf("offset" to "0", "search" to "specmatic")
        assertEquals(result2, URIUtils.parseQuery(query2))

        val query3 = "offset=0&pageSize"
        assertThrows(Exception::class.java) {
            URIUtils.parseQuery(query3)
        }
    }

    @Test
    fun parsePathParams() {

        val path0 = ""
        val result0 = emptyMap<String, String>()
        assertEquals(result0, URIUtils.parsePathParams(path0))

        val path1 = "users/{uuid:UUID}"
        val result1 = emptyMap<String, String>()
        assertEquals(result1, URIUtils.parsePathParams(path1))

        val path2 = "/users/(uuid:UUID)/posts/(name:String)/invalid"
        val result2 = mapOf("uuid" to "(UUID)", "name" to "(String)")
        assertEquals(result2, URIUtils.parsePathParams(path2))

        val path3 = "/users/(uuid:UUID/posts/(name:String)"
        val result3 = mapOf("name" to "(String)")
        assertEquals(result3, URIUtils.parsePathParams(path3))
    }

}
