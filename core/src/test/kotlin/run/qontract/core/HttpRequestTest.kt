package run.qontract.core

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import run.qontract.core.value.EmptyString
import run.qontract.core.value.JSONObjectValue
import run.qontract.core.value.StringValue
import kotlin.test.assertEquals

internal class HttpRequestTest {
    @Test
    fun `it should serialise the request correctly`() {
        val request = HttpRequest("GET", "/", HashMap(), EmptyString, HashMap(mapOf("one" to "two")))
        val expectedString = """GET /?one=two

"""

        assertEquals(expectedString, request.toLogString(""))
    }

    @Test
    fun `when serialised to json, the request should contain form fields`() {
        val json = HttpRequest("POST", "/").copy(formFields = mapOf("Data" to "10")).toJSON()
        val value = json.getValue("form-fields") as JSONObjectValue
        assertThat(value.jsonObject.getValue("Data")).isEqualTo(StringValue("10"))
    }

    @Test
    fun `when serialised to log string, the log should contain form fields`() {
        val logString = HttpRequest("POST", "/").copy(formFields = mapOf("Data" to "10")).toLogString()

        assertThat(logString).contains("Data=10")
    }
}
