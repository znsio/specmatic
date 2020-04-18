package run.qontract.core

import run.qontract.mock.MockException
import org.junit.jupiter.api.Assertions
import org.junit.jupiter.api.Test
import run.qontract.core.value.StringValue
import run.qontract.core.value.Value
import kotlin.test.assertEquals

class RequestResponseSerialisationTest {
    @Test
    @Throws(MockException::class)
    fun testRequestSerialisationWithTextBody() {
        val request =
                HttpRequest()
                    .updateMethod("GET")
                    .updateQueryParam("one", 1.toString())
                    .updateQueryParam("two", 2.toString())
                    .updatePath("/test")
                    .updateHeader("Content-Type", "text/plain")
                    .updateBody("hello world")
        val jsonRequest = request.toJSON()
        val requestDeserialised = requestFromJSON(jsonRequest)
        Assertions.assertEquals(request, requestDeserialised)
    }

    @Test
    @Throws(MockException::class)
    fun testRequestSerialisationWithJSONBody() {
        val request =
                HttpRequest()
                    .updateMethod("GET")
                    .updateQueryParam("one", 1.toString())
                    .updateQueryParam("two", 2.toString())
                    .updatePath("/test")
                    .updateHeader("Content-Type", "application/json")
                    .updateBody("{\"one\": 1}")
        val jsonRequest = request.toJSON()
        val requestDeserialised = requestFromJSON(jsonRequest)
        Assertions.assertEquals(request, requestDeserialised)
    }

    @Test
    @Throws(MockException::class)
    fun testRequestSerialisationWithXMLBody() {
        val request =
                HttpRequest()
                    .updateMethod("GET")
                    .updateQueryParam("one", 1.toString())
                    .updateQueryParam("two", 2.toString())
                    .updatePath("/test")
                    .updateHeader("Content-Type", "application/xml")
                    .updateBody("<one>1</one>")
        val json: Map<String, Value> = request.toJSON()

        assertEquals("GET", s(json, "method"))
        assertEquals("/test", s(json, "path"))
        val requestDeserialised = requestFromJSON(json)
        Assertions.assertEquals(request, requestDeserialised)
    }

    fun s(json: Map<String, Value>, key: String): String = (json.getValue(key) as StringValue).string

    @Test
    fun testResponseSerialisation() {
        val response = HttpResponse(200, "hello world", mutableMapOf("Content-Type" to "text/plain"))
        val json = response.toJSON()
        val responseDeserialised = HttpResponse.fromJSON(json.toMap())
        Assertions.assertEquals(response, responseDeserialised)
    }
}