package run.qontract.core

import run.qontract.mock.HttpMockException
import org.junit.jupiter.api.Assertions
import org.junit.jupiter.api.Test
import run.qontract.core.value.StringValue
import run.qontract.core.value.Value
import kotlin.test.assertEquals

class RequestResponseSerialisationTest {
    @Test
    @Throws(HttpMockException::class)
    fun testRequestSerialisationWithTextBody() {
        val request = HttpRequest()
        request.updateMethod("GET")
        request.updateQueryParam("one", 1.toString())
        request.updateQueryParam("two", 2.toString())
        request.updatePath("/test")
        request.updateHeader("Content-Type", "text/plain")
        request.updateBody("hello world")
        val jsonRequest = request.toJSON()
        val requestDeserialised = requestFromJSON(jsonRequest)
        Assertions.assertEquals(request, requestDeserialised)
    }

    @Test
    @Throws(HttpMockException::class)
    fun testRequestSerialisationWithJSONBody() {
        val request = HttpRequest()
        request.updateMethod("GET")
        request.updateQueryParam("one", 1.toString())
        request.updateQueryParam("two", 2.toString())
        request.updatePath("/test")
        request.updateHeader("Content-Type", "application/json")
        request.updateBody("{\"one\": 1}")
        val jsonRequest = request.toJSON()
        val requestDeserialised = requestFromJSON(jsonRequest)
        Assertions.assertEquals(request, requestDeserialised)
    }

    @Test
    @Throws(HttpMockException::class)
    fun testRequestSerialisationWithXMLBody() {
        val request = HttpRequest()
        request.updateMethod("GET")
        request.updateQueryParam("one", 1.toString())
        request.updateQueryParam("two", 2.toString())
        request.updatePath("/test")
        request.updateHeader("Content-Type", "application/xml")
        request.updateBody("<one>1</one>")
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