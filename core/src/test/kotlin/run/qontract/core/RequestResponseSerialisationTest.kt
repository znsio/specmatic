package run.qontract.core

import run.qontract.mock.HttpMockException
import org.junit.jupiter.api.Assertions
import org.junit.jupiter.api.Test
import java.util.*

class RequestResponseSerialisationTest {
    @Test
    @Throws(HttpMockException::class)
    fun testRequestSerialisationWithTextBody() {
        val request = HttpRequest()
        request.setMethod("GET")
        request.setQueryParam("one", 1.toString())
        request.setQueryParam("two", 2.toString())
        request.updatePath("/test")
        request.setHeader("Content-Type", "text/plain")
        request.setBody("hello world")
        val jsonRequest = request.toJSON()
        val requestDeserialised = HttpRequest.fromJSON(jsonRequest)
        Assertions.assertEquals(request, requestDeserialised)
    }

    @Test
    @Throws(HttpMockException::class)
    fun testRequestSerialisationWithJSONBody() {
        val request = HttpRequest()
        request.setMethod("GET")
        request.setQueryParam("one", 1.toString())
        request.setQueryParam("two", 2.toString())
        request.updatePath("/test")
        request.setHeader("Content-Type", "application/json")
        request.setBody("{\"one\": 1}")
        val jsonRequest = request.toJSON()
        val requestDeserialised = HttpRequest.fromJSON(jsonRequest)
        Assertions.assertEquals(request, requestDeserialised)
    }

    @Test
    @Throws(HttpMockException::class)
    fun testRequestSerialisationWithXMLBody() {
        val request = HttpRequest()
        request.setMethod("GET")
        request.setQueryParam("one", 1.toString())
        request.setQueryParam("two", 2.toString())
        request.updatePath("/test")
        request.setHeader("Content-Type", "application/xml")
        request.setBody("<one>1</one>")
        val jsonRequest = request.toJSON()
        val requestDeserialised = HttpRequest.fromJSON(jsonRequest)
        Assertions.assertEquals(request, requestDeserialised)
    }

    @Test
    fun testResponseSerialisation() {
        val response = HttpResponse(200, "hello world", object : HashMap<String, String?>() {
            init {
                put("Content-Type", "text/plain")
            }
        })
        val jsonResponse = response.toJSON()
        val responseDeserialised = HttpResponse.fromJSON(jsonResponse.toMap())
        Assertions.assertEquals(response, responseDeserialised)
    }
}