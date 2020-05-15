package run.qontract.core

import org.junit.jupiter.api.Assertions
import org.junit.jupiter.api.Test
import run.qontract.core.pattern.parsedValue
import run.qontract.core.value.EmptyString
import run.qontract.core.value.JSONObjectValue
import run.qontract.core.value.StringValue
import java.util.*
import kotlin.test.assertEquals

internal class HttpResponseTest {
    @Test
    fun createANewResponseObjectWithInitialValues() {
        val response = HttpResponse(500, "ERROR", HashMap())
        Assertions.assertEquals(500, response.status)
        Assertions.assertEquals(StringValue("ERROR"), response.body)
    }

    @Test
    fun createANewResponseObjectWithoutInitialValues() {
        val response = HttpResponse.EMPTY
        Assertions.assertEquals(0, response.status)
        Assertions.assertEquals(EmptyString, response.body)
    }

    @Test
    fun `updating body with value should automatically set Content-Type header`() {
        HttpResponse.EMPTY.updateBodyWith(parsedValue("""{"name": "John Doe"}""")).let {
            val responseBody = it.body

            if(responseBody !is JSONObjectValue)
                throw AssertionError("Expected responseBody to be a JSON object, but got ${responseBody?.javaClass?.name}")

            assertEquals("John Doe", responseBody.jsonObject.getValue("name").toStringValue())
            assertEquals("application/json", it.headers.getOrDefault("Content-Type", ""))
        }
    }
}