package run.qontract.core

import run.qontract.core.pattern.parsedValue
import org.junit.jupiter.api.Assertions
import org.junit.jupiter.api.Test
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
            assertEquals("John Doe", (parsedValue(it.body).value as HashMap<String, Any?>)["name"])
            assertEquals("application/json", it.headers.getOrDefault("Content-Type", ""))
        }
    }
}