package run.qontract.core

import org.junit.jupiter.api.Assertions
import org.junit.jupiter.api.Test
import run.qontract.core.pattern.parsedValue
import run.qontract.core.value.JSONObjectValue
import java.util.*
import kotlin.test.assertEquals

internal class HttpResponseTest {
    @Test
    fun createANewResponseObjectWithInitialValues() {
        val response = HttpResponse(500, "ERROR", HashMap())
        Assertions.assertEquals(500, response.status)
        Assertions.assertEquals("ERROR", response.body)
    }

    @Test
    fun createANewResponseObjectWithoutInitialValues() {
        val response = HttpResponse()
        Assertions.assertEquals(0, response.status)
        Assertions.assertEquals("", response.body)
    }

    @Test
    fun `updating body with value should automatically set Content-Type header`() {
        HttpResponse().updateBodyWith(parsedValue("""{"name": "John Doe"}""")).let {
            val responseBody = parsedValue(it.body)

            if(responseBody !is JSONObjectValue)
                throw AssertionError("Expected responseBody to be a JSON object, but got ${responseBody.javaClass.name}")

            assertEquals("John Doe", responseBody.jsonObject.getValue("name").value)
            assertEquals("application/json", it.headers.getOrDefault("Content-Type", ""))
        }
    }
}