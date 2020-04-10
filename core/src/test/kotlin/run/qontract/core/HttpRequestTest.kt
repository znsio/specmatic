package run.qontract.core

import org.junit.jupiter.api.Test
import run.qontract.core.value.EmptyString
import kotlin.test.assertEquals

internal class HttpRequestTest {
    @Test
    fun `it should serialise the request correctly`() {
        val request = HttpRequest("GET", "/", HashMap(), EmptyString, HashMap(mapOf("one" to "two")))
        val expectedString = """GET /?one=two

"""

        assertEquals(expectedString, request.toLogString(""))
    }
}