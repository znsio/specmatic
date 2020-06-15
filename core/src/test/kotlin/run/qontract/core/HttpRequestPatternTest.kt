package run.qontract.core

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import run.qontract.core.Result.*
import run.qontract.core.pattern.*
import run.qontract.core.value.JSONArrayValue
import run.qontract.core.value.NumberValue
import run.qontract.core.value.StringValue
import run.qontract.mock.ScenarioStub
import java.net.URI
import kotlin.test.assertEquals

internal class HttpRequestPatternTest {
    @Test
    fun `should not match when url does not match`() {
        val httpRequestPattern = HttpRequestPattern(
                urlMatcher = toURLMatcher(URI("/matching_path")))
        val httpRequest = HttpRequest().updateWith(URI("/unmatched_path"))
        httpRequestPattern.matches(httpRequest, Resolver()).let {
            assertThat(it).isInstanceOf(Failure::class.java)
            assertThat((it as Failure).report()).isEqualTo(FailureReport(listOf("REQUEST", "URL", "PATH (/unmatched_path)"), listOf("""Expected string: "matching_path", actual was string: "unmatched_path"""")))
        }
    }

    @Test
    fun `should not match when method does not match`() {
        val httpRequestPattern = HttpRequestPattern(
                urlMatcher = toURLMatcher(URI("/matching_path")),
                method = "POST")
        val httpRequest = HttpRequest()
            .updateWith(URI("/matching_path"))
            .updateMethod("GET")
        httpRequestPattern.matches(httpRequest, Resolver()).let {
            assertThat(it is Failure).isTrue()
            assertThat((it as Failure).report()).isEqualTo(FailureReport(listOf("REQUEST", "METHOD"), listOf("Expected POST, actual was GET")))
        }
    }

    @Test
    fun `should not match when body does not match`() {
        val httpRequestPattern =
                HttpRequestPattern(
                        urlMatcher = toURLMatcher(URI("/matching_path")),
                        method = "POST",
                        body = parsedPattern("""{"name": "Hari"}"""))
        val httpRequest = HttpRequest()
            .updateWith(URI("/matching_path"))
            .updateMethod("POST")
            .updateBody("""{"unmatchedKey": "unmatchedValue"}""")
        httpRequestPattern.matches(httpRequest, Resolver()).let {
            assertThat(it).isInstanceOf(Failure::class.java)
            assertThat((it as Failure).report()).isEqualTo(FailureReport(listOf("REQUEST", "BODY"), listOf("Expected key name was missing")))
        }
    }

    @Test
    fun `should match when request matches url, method and body`() {
        val httpRequestPattern = HttpRequestPattern(
                urlMatcher =  toURLMatcher(URI("/matching_path")),
                method = "POST",
                body = parsedPattern("""{"name": "Hari"}"""))
        val httpRequest = HttpRequest()
            .updateWith(URI("/matching_path"))
            .updateMethod("POST")
            .updateBody("""{"name": "Hari"}""")
        httpRequestPattern.matches(httpRequest, Resolver()).let {
            assertThat(it).isInstanceOf(Success::class.java)
        }
    }

    @Test
    fun `a clone request pattern request should include the headers specified`() {
        val pattern = HttpRequestPattern(
                headersPattern = HttpHeadersPattern(mapOf("Test-Header" to stringToPattern("(string)", "Test-Header"))),
                urlMatcher = toURLMatcher(URI("/")),
                method = "GET"
        )

        val newPatterns = pattern.newBasedOn(Row(), Resolver())
        assertEquals("(string)", newPatterns[0].headersPattern.pattern.get("Test-Header").toString())
    }

    @Test
    fun `a request with an optional header should result in 2 options for newBasedOn`() {
        val requests = HttpRequestPattern(method = "GET",
                urlMatcher = toURLMatcher(URI("/")),
                headersPattern = HttpHeadersPattern(mapOf("X-Optional?" to StringPattern))).newBasedOn(Row(), Resolver())

        assertThat(requests).hasSize(2)

        val flags = requests.map {
            when {
                it.headersPattern.pattern.containsKey("X-Optional") -> "with"
                else -> "without"
            }
        }

        flagsContain(flags, listOf("with", "without"))
    }

    @Test
    fun `number bodies should match numerical strings`() {
        val requestPattern = HttpRequestPattern(method = "GET", urlMatcher = toURLMatcher("/"), body = NumberPattern)
        val request = HttpRequest("GET", path = "/", body = StringValue("10"))

        assertThat(requestPattern.matches(request, Resolver())).isInstanceOf(Success::class.java)
    }

    @Test
    fun `boolean bodies should match boolean strings`() {
        val requestPattern = HttpRequestPattern(method = "GET", urlMatcher = toURLMatcher("/"), body = BooleanPattern)
        val request = HttpRequest("GET", path = "/", body = StringValue("true"))

        assertThat(requestPattern.matches(request, Resolver())).isInstanceOf(Success::class.java)
    }

    @Test
    fun `boolean bodies should not match non-boolean strings`() {
        val requestPattern = HttpRequestPattern(method = "GET", urlMatcher = toURLMatcher("/"), body = BooleanPattern)
        val request = HttpRequest("GET", path = "/", body = StringValue("10"))

        assertThat(requestPattern.matches(request, Resolver())).isInstanceOf(Failure::class.java)
    }

    @Test
    fun `integer bodies should not match non-integer strings`() {
        val requestPattern = HttpRequestPattern(method = "GET", urlMatcher = toURLMatcher("/"), body = NumberPattern)
        val request = HttpRequest("GET", path = "/", body = StringValue("not a number"))

        assertThat(requestPattern.matches(request, Resolver())).isInstanceOf(Failure::class.java)
    }

    @Test
    fun `request with multiple parts and no optional values should result in just one test for the whole`() {
        val parts = listOf(MultiPartContentPattern("data1", StringPattern), MultiPartContentPattern("data2", StringPattern))
        val requestPattern = HttpRequestPattern(method = "GET", urlMatcher = toURLMatcher("/"), multiPartFormDataPattern = parts)
        val patterns = requestPattern.newBasedOn(Row(), Resolver())

        assertThat(patterns).hasSize(1)

        assertThat(patterns.single().multiPartFormDataPattern).isEqualTo(parts)
    }

    @Test
    fun `request with an optional part should result in two requests`() {
        val part = MultiPartContentPattern("data?", StringPattern)

        val requestPattern = HttpRequestPattern(method = "GET", urlMatcher = toURLMatcher("/"), multiPartFormDataPattern = listOf(part))
        val patterns = requestPattern.newBasedOn(Row(), Resolver())

        assertThat(patterns).hasSize(2)

        assertThat(patterns).contains(HttpRequestPattern(method = "GET", urlMatcher = toURLMatcher("/"), multiPartFormDataPattern = emptyList()))
        assertThat(patterns).contains(HttpRequestPattern(method = "GET", urlMatcher = toURLMatcher("/"), multiPartFormDataPattern = listOf(part.nonOptional())))
    }

    @Test
    fun `request with a part json body with a key in a row should result in a request with the row value`() {
        val part = MultiPartContentPattern("data", parsedPattern("""{"name": "(string)"}"""))
        val example = Row(listOf("name"), listOf("John Doe"))

        val requestPattern = HttpRequestPattern(method = "GET", urlMatcher = toURLMatcher("/"), multiPartFormDataPattern = listOf(part))
        val patterns = requestPattern.newBasedOn(example, Resolver())

        assertThat(patterns).hasSize(1)

        val expectedPattern = HttpRequestPattern(method = "GET", urlMatcher = toURLMatcher("/"), multiPartFormDataPattern = listOf(MultiPartContentPattern("data", JSONObjectPattern(mapOf("name" to ExactValuePattern(StringValue("John Doe")))))))
        assertThat(patterns.single()).isEqualTo(expectedPattern)
    }

    @Test
    fun `request having a part name the same as a key in a row should result in a request with a part having the specified value`() {
        val part = MultiPartContentPattern("name", StringPattern)
        val example = Row(listOf("name"), listOf("John Doe"))

        val requestPattern = HttpRequestPattern(method = "GET", urlMatcher = toURLMatcher("/"), multiPartFormDataPattern = listOf(part))
        val patterns = requestPattern.newBasedOn(example, Resolver())

        assertThat(patterns).hasSize(1)

        val expectedPattern = HttpRequestPattern(method = "GET", urlMatcher = toURLMatcher("/"), multiPartFormDataPattern = listOf(MultiPartContentPattern("name", ExactValuePattern(StringValue("John Doe")))))
        assertThat(patterns.single()).isEqualTo(expectedPattern)
    }

    @Test
    fun `request having an optional part name the same as a key in a row should result in a request with a part having the specified value`() {
        val part = MultiPartContentPattern("name?", StringPattern)
        val example = Row(listOf("name"), listOf("John Doe"))

        val requestPattern = HttpRequestPattern(method = "GET", urlMatcher = toURLMatcher("/"), multiPartFormDataPattern = listOf(part))
        val patterns = requestPattern.newBasedOn(example, Resolver())

        assertThat(patterns).hasSize(1)

        val expectedPattern = HttpRequestPattern(method = "GET", urlMatcher = toURLMatcher("/"), multiPartFormDataPattern = listOf(MultiPartContentPattern("name", ExactValuePattern(StringValue("John Doe")))))
        assertThat(patterns.single()).isEqualTo(expectedPattern)
    }

    @Test
    fun `request type having an optional part name should match a request in which the part is missing`() {
        val part = MultiPartContentPattern("name?", StringPattern)

        val requestType = HttpRequestPattern(method = "GET", urlMatcher = toURLMatcher("/"), multiPartFormDataPattern = listOf(part))

        val request = HttpRequest("GET", "/")

        assertThat(requestType.matches(request, Resolver())).isInstanceOf(Success::class.java)
    }

    @Test
    fun `should generate a request with an array value if the array is in an example`() {
        val example = Row(listOf("csv"), listOf("[1, 2, 3]"))

        val type = parsedPattern("""{"csv": "(number*)"}""")
        val newTypes = type.newBasedOn(example, Resolver())

        assertThat(newTypes).hasSize(1)

        val newType = newTypes.single() as JSONObjectPattern

        assertThat(newType.pattern.getValue("csv")).isEqualTo(ExactValuePattern(JSONArrayValue(listOf(NumberValue(1), NumberValue(2), NumberValue(3)))))
    }
}
