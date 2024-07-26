import io.specmatic.core.HttpRequest
import io.specmatic.core.HttpResponse
import io.specmatic.core.log.HttpLogMessage
import io.specmatic.core.value.JSONObjectValue
import io.specmatic.core.value.NumberValue
import io.specmatic.core.value.StringValue
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test

internal class HttpLogMessageTest {
    private val httpLog = HttpLogMessage("start-time", HttpRequest("GET", "/"), "end-time", HttpResponse.OK, "/path/to/file")

    @Test
    fun `render an http log message as JSON`() {
        val json: JSONObjectValue = httpLog.toJSONObject()
        assertThat(json.getString("requestTime")).isEqualTo("start-time")
        assertThat(json.getString("responseTime")).isEqualTo("end-time")
        assertThat(json.getString("contractMatched")).isEqualTo("/path/to/file")

        assertThat(json.findFirstChildByPath("http-request.path")).isEqualTo(StringValue("/"))
        assertThat(json.findFirstChildByPath("http-request.method")).isEqualTo(StringValue("GET"))
        assertThat(json.findFirstChildByPath("http-request.body")).isEqualTo(StringValue(""))

        assertThat(json.findFirstChildByPath("http-response.status")).isEqualTo(NumberValue(200))
        assertThat(json.findFirstChildByPath("http-response.status-text")).isEqualTo(StringValue("OK"))
        assertThat(json.findFirstChildByPath("http-response.body")).isEqualTo(StringValue(""))
    }

    @Test
    fun `render an http log message as Text`() {
        val text: String = httpLog.toLogString()

        println(text)

        assertThat(text).contains("start-time")
        assertThat(text).contains("GET /")

        assertThat(text).contains("200 OK")
        assertThat(text).contains("end-time")

        assertThat(text).contains("/path/to/file")
    }
}