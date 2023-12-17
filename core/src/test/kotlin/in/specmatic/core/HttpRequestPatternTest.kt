package `in`.specmatic.core

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import `in`.specmatic.core.Result.Failure
import `in`.specmatic.core.Result.Success
import `in`.specmatic.core.pattern.*
import `in`.specmatic.core.value.JSONArrayValue
import `in`.specmatic.core.value.JSONObjectValue
import `in`.specmatic.core.value.NumberValue
import `in`.specmatic.core.value.StringValue
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Nested
import java.net.URI

internal class HttpRequestPatternTest {
    @Test
    fun `should not match when url does not match`() {
        val httpRequestPattern = HttpRequestPattern(
                urlMatcher = toURLMatcherWithOptionalQueryParams(URI("/matching_path")))
        val httpRequest = HttpRequest().updateWith(URI("/unmatched_path"))
        httpRequestPattern.matches(httpRequest, Resolver()).let {
            assertThat(it).isInstanceOf(Failure::class.java)
            assertThat((it as Failure).toMatchFailureDetails()).isEqualTo(MatchFailureDetails(listOf("REQUEST", "PATH (/unmatched_path)"), listOf("""Expected "matching_path", actual was "unmatched_path"""")))
        }
    }

    @Test
    fun `should not match when method does not match`() {
        val httpRequestPattern = HttpRequestPattern(
                urlMatcher = toURLMatcherWithOptionalQueryParams(URI("/matching_path")),
                method = "POST")
        val httpRequest = HttpRequest()
            .updateWith(URI("/matching_path"))
            .updateMethod("GET")
        httpRequestPattern.matches(httpRequest, Resolver()).let {
            assertThat(it is Failure).isTrue()
            assertThat((it as Failure).toMatchFailureDetails()).isEqualTo(MatchFailureDetails(listOf("REQUEST", "METHOD"), listOf("Expected POST, actual was GET")))
        }
    }

    @Test
    fun `should not match when body does not match`() {
        val httpRequestPattern =
                HttpRequestPattern(
                        urlMatcher = toURLMatcherWithOptionalQueryParams(URI("/matching_path")),
                        method = "POST",
                        body = parsedPattern("""{"name": "Hari"}"""))
        val httpRequest = HttpRequest()
            .updateWith(URI("/matching_path"))
            .updateMethod("POST")
            .updateBody("""{"unmatchedKey": "unmatchedValue"}""")
        httpRequestPattern.matches(httpRequest, Resolver()).let {
            assertThat(it).isInstanceOf(Failure::class.java)
            assertThat((it as Failure).toMatchFailureDetails()).isEqualTo(MatchFailureDetails(listOf("REQUEST", "BODY", "name"), listOf("Expected key named \"name\" was missing")))
        }
    }

    @Test
    fun `should match when request matches url, method and body`() {
        val httpRequestPattern = HttpRequestPattern(
                urlMatcher =  toURLMatcherWithOptionalQueryParams(URI("/matching_path")),
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
                urlMatcher = toURLMatcherWithOptionalQueryParams(URI("/")),
                method = "GET"
        )

        val newPatterns = pattern.newBasedOn(Row(), Resolver())
        assertEquals("(string)", newPatterns[0].headersPattern.pattern.get("Test-Header").toString())
    }

    @Test
    fun `clone request pattern with example of body type should pick up the example`() {
        val pattern = HttpRequestPattern(
                urlMatcher = toURLMatcherWithOptionalQueryParams(URI("/")),
                method = "POST",
                body = DeferredPattern("(Data)")
        )

        val resolver = Resolver(newPatterns = mapOf("(Data)" to TabularPattern(mapOf("id" to NumberPattern()))))
        val data = """{"id": 10}"""
        val row = Row(columnNames = listOf("(Data)"), values = listOf(data))
        val newPatterns = pattern.newBasedOn(row, resolver)

        assertThat((newPatterns.single().body as ExactValuePattern).pattern as JSONObjectValue).isEqualTo(parsedValue(data))
    }

    @Test
    fun `a request with an optional header should result in 2 options for newBasedOn`() {
        val requests = HttpRequestPattern(method = "GET",
                urlMatcher = toURLMatcherWithOptionalQueryParams(URI("/")),
                headersPattern = HttpHeadersPattern(mapOf("X-Optional?" to StringPattern()))).newBasedOn(Row(), Resolver())

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
        val requestPattern = HttpRequestPattern(method = "GET", urlMatcher = toURLMatcherWithOptionalQueryParams("/"), body = NumberPattern())
        val request = HttpRequest("GET", path = "/", body = StringValue("10"))

        assertThat(requestPattern.matches(request, Resolver())).isInstanceOf(Success::class.java)
    }

    @Test
    fun `boolean bodies should match boolean strings`() {
        val requestPattern = HttpRequestPattern(method = "GET", urlMatcher = toURLMatcherWithOptionalQueryParams("/"), body = BooleanPattern())
        val request = HttpRequest("GET", path = "/", body = StringValue("true"))

        assertThat(requestPattern.matches(request, Resolver())).isInstanceOf(Success::class.java)
    }

    @Test
    fun `boolean bodies should not match non-boolean strings`() {
        val requestPattern = HttpRequestPattern(method = "GET", urlMatcher = toURLMatcherWithOptionalQueryParams("/"), body = BooleanPattern())
        val request = HttpRequest("GET", path = "/", body = StringValue("10"))

        assertThat(requestPattern.matches(request, Resolver())).isInstanceOf(Failure::class.java)
    }

    @Test
    fun `integer bodies should not match non-integer strings`() {
        val requestPattern = HttpRequestPattern(method = "GET", urlMatcher = toURLMatcherWithOptionalQueryParams("/"), body = NumberPattern())
        val request = HttpRequest("GET", path = "/", body = StringValue("not a number"))

        assertThat(requestPattern.matches(request, Resolver())).isInstanceOf(Failure::class.java)
    }

    @Test
    fun `request with multiple parts and no optional values should result in just one test for the whole`() {
        val parts = listOf(MultiPartContentPattern(
            "data1",
            StringPattern(),
        ), MultiPartContentPattern("data2", StringPattern()))
        val requestPattern = HttpRequestPattern(method = "GET", urlMatcher = toURLMatcherWithOptionalQueryParams("/"), multiPartFormDataPattern = parts)
        val patterns = requestPattern.newBasedOn(Row(), Resolver())

        assertThat(patterns).hasSize(1)

        assertThat(patterns.single().multiPartFormDataPattern).isEqualTo(parts)
    }

    @Test
    fun `request with an optional part should result in two requests`() {
        val part = MultiPartContentPattern("data?", StringPattern())

        val requestPattern = HttpRequestPattern(method = "GET", urlMatcher = toURLMatcherWithOptionalQueryParams("/"), multiPartFormDataPattern = listOf(part))
        val patterns = requestPattern.newBasedOn(Row(), Resolver())

        assertThat(patterns).hasSize(2)

        assertThat(patterns).contains(HttpRequestPattern(method = "GET", urlMatcher = toURLMatcherWithOptionalQueryParams("/"), multiPartFormDataPattern = emptyList()))
        assertThat(patterns).contains(HttpRequestPattern(method = "GET", urlMatcher = toURLMatcherWithOptionalQueryParams("/"), multiPartFormDataPattern = listOf(part.nonOptional())))
    }

    @Test
    fun `request with a part json body with a key in a row should result in a request with the row value`() {
        val part = MultiPartContentPattern("data", parsedPattern("""{"name": "(string)"}"""))
        val example = Row(listOf("name"), listOf("John Doe"))

        val requestPattern = HttpRequestPattern(method = "GET", urlMatcher = toURLMatcherWithOptionalQueryParams("/"), multiPartFormDataPattern = listOf(part))
        val patterns = requestPattern.newBasedOn(example, Resolver())

        assertThat(patterns).hasSize(1)

        val expectedPattern = HttpRequestPattern(method = "GET", urlMatcher = toURLMatcherWithOptionalQueryParams("/"), multiPartFormDataPattern = listOf(MultiPartContentPattern(
            "data",
            toJSONObjectPattern(mapOf("name" to ExactValuePattern(StringValue("John Doe")))),
        )))
        assertThat(patterns.single()).isEqualTo(expectedPattern)
    }

    @Test
    fun `request having a part name the same as a key in a row should result in a request with a part having the specified value`() {
        val part = MultiPartContentPattern("name", StringPattern())
        val example = Row(listOf("name"), listOf("John Doe"))

        val requestPattern = HttpRequestPattern(method = "GET", urlMatcher = toURLMatcherWithOptionalQueryParams("/"), multiPartFormDataPattern = listOf(part))
        val patterns = requestPattern.newBasedOn(example, Resolver())

        assertThat(patterns).hasSize(1)

        val expectedPattern = HttpRequestPattern(method = "GET", urlMatcher = toURLMatcherWithOptionalQueryParams("/"), multiPartFormDataPattern = listOf(MultiPartContentPattern(
            "name",
            ExactValuePattern(StringValue("John Doe")),
        )))
        assertThat(patterns.single()).isEqualTo(expectedPattern)
    }

    @Test
    fun `request having an optional part name the same as a key in a row should result in a request with a part having the specified value`() {
        val part = MultiPartContentPattern("name?", StringPattern())
        val example = Row(listOf("name"), listOf("John Doe"))

        val requestPattern = HttpRequestPattern(method = "GET", urlMatcher = toURLMatcherWithOptionalQueryParams("/"), multiPartFormDataPattern = listOf(part))
        val patterns = requestPattern.newBasedOn(example, Resolver())

        assertThat(patterns).hasSize(1)

        val expectedPattern = HttpRequestPattern(method = "GET", urlMatcher = toURLMatcherWithOptionalQueryParams("/"), multiPartFormDataPattern = listOf(MultiPartContentPattern(
            "name",
            ExactValuePattern(StringValue("John Doe")),
        )))
        assertThat(patterns.single()).isEqualTo(expectedPattern)
    }

    @Test
    fun `request type having an optional part name should match a request in which the part is missing`() {
        val part = MultiPartContentPattern("name?", StringPattern())

        val requestType = HttpRequestPattern(method = "GET", urlMatcher = toURLMatcherWithOptionalQueryParams("/"), multiPartFormDataPattern = listOf(part))

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

    @Test
    fun `should generate a request with an object value if the object is in an example`() {
        val example = Row(listOf("data"), listOf("""{"one": 1}"""))

        val type = parsedPattern("""{"data": "(Data)"}""")
        val newTypes = type.newBasedOn(example, Resolver(newPatterns = mapOf("(Data)" to toTabularPattern(mapOf("one" to NumberPattern())))))

        assertThat(newTypes).hasSize(1)

        val newType = newTypes.single() as JSONObjectPattern

        assertThat(newType.pattern.getValue("data")).isEqualTo(ExactValuePattern(JSONObjectValue(mapOf("one" to NumberValue(1)))))
    }

    @Test
    fun `should generate a request with an array body if the array is in an example`() {
        val example = Row(listOf("body"), listOf("[1, 2, 3]"))

        val requestType = HttpRequestPattern(urlMatcher = toURLMatcherWithOptionalQueryParams("/"), body = parsedPattern("(body: RequestBody)"))
        val newRequestTypes = requestType.newBasedOn(example, Resolver(newPatterns = mapOf("(RequestBody)" to parsedPattern("""(number*)"""))))

        assertThat(newRequestTypes).hasSize(1)

        val newRequestType = newRequestTypes.single()
        val requestBodyType = newRequestType.body as ExactValuePattern

        assertThat(requestBodyType).isEqualTo(ExactValuePattern(JSONArrayValue(listOf(NumberValue(1), NumberValue(2), NumberValue(3)))))
    }

    @Test
    fun `should generate a request with an object body if the object is in an example`() {
        val example = Row(listOf("body"), listOf("""{"one": 1}"""))

        val requestType = HttpRequestPattern(urlMatcher = toURLMatcherWithOptionalQueryParams("/"), body = parsedPattern("(body: RequestBody)"))
        val newRequestTypes = requestType.newBasedOn(example, Resolver(newPatterns = mapOf("(RequestBody)" to toTabularPattern(mapOf("one" to NumberPattern())))))

        assertThat(newRequestTypes).hasSize(1)

        val newRequestType = newRequestTypes.single()
        val requestBodyType = newRequestType.body as ExactValuePattern

        assertThat(requestBodyType).isEqualTo(ExactValuePattern(JSONObjectValue(mapOf("one" to NumberValue(1)))))
    }

    @Test
    fun `should generate a stub request pattern from an http request in which the query params are not optional`() {
        val requestType = HttpRequestPattern(method = "GET", urlMatcher = URLMatcher(mapOf("status" to StringPattern()), pathToPattern("/"), "/"))
        val newRequestType = requestType.generate(HttpRequest("GET", "/", queryParams = mapOf("status" to "available")), Resolver())

        assertThat(newRequestType.urlMatcher?.queryPattern?.keys?.sorted()).isEqualTo(listOf("status"))

    }

    @Test
    fun `form field of type json in string should match a form field value of type json in string`() {
        val customerType: Pattern = TabularPattern(mapOf("id" to NumberPattern()))
        val request = HttpRequest(method = "POST", path = "/", formFields = mapOf("Customer" to """{"id": 10}"""))

        HttpRequestPattern(
            method = "POST",
            urlMatcher = URLMatcher(emptyMap(), emptyList(), "/"),
            formFieldsPattern = mapOf("Customer" to PatternInStringPattern(customerType, "(customer)"))
        ).generate(request, Resolver()).let { requestType ->
            val customerFieldType = requestType.formFieldsPattern.getValue("Customer")
            assertThat(customerFieldType).isInstanceOf(PatternInStringPattern::class.java)

            val patternInStringPattern = customerFieldType as PatternInStringPattern
            assertThat(patternInStringPattern.pattern).isInstanceOf(JSONObjectPattern::class.java)

            assertThat(patternInStringPattern.matches(parsedJSON("""{"id": 10}""").toStringValue(), Resolver())).isInstanceOf(
                Success::class.java)
        }
    }

    @Test
    fun `optional form field can be omitted from request`() {
        val request = HttpRequest(method = "POST", path = "/", formFields = mapOf("hello" to """10"""))

        val result = HttpRequestPattern(
            method = "POST",
            urlMatcher = URLMatcher(emptyMap(), emptyList(), "/"),
            formFieldsPattern = mapOf("hello" to NumberPattern(), "world?" to NumberPattern())
        ).matches(request, Resolver())

        assertThat(result).isInstanceOf(Success::class.java)
    }

    @Test
    fun `match errors across the request including header and body will be returned`()  {
        val type = HttpRequestPattern(method = "POST", urlMatcher = toURLMatcherWithOptionalQueryParams("http://helloworld.com/data"), headersPattern = HttpHeadersPattern(mapOf("X-Data" to NumberPattern())), body = JSONObjectPattern(mapOf("id" to NumberPattern())))
        val request = HttpRequest("POST", "/data", headers = mapOf("X-Data" to "abc123"), body = parsedJSON("""{"id": "abc123"}"""))

        val result = type.matches(request, Resolver())
        val reportText = result.reportString()
        assertThat(reportText).contains(">> REQUEST.HEADERS.X-Data")
        assertThat(reportText).contains(">> REQUEST.BODY.id")
    }

    @Test
    fun `should lower case header keys while loading stub data`()  {
        val type = HttpRequestPattern(method = "POST", urlMatcher = toURLMatcherWithOptionalQueryParams("http://helloworld.com/data"), headersPattern = HttpHeadersPattern(mapOf("x-data" to StringPattern())), body = JSONObjectPattern(mapOf("id" to NumberPattern())))
        val request = HttpRequest("POST", "/data", headers = mapOf("X-Data" to "abc123"), body = parsedJSON("""{"id": "abc123"}"""))

        val httpRequestPattern = type.generate(request, Resolver())
        assertThat(httpRequestPattern.headersPattern.pattern["x-data"].toString()).isEqualTo("abc123")
    }

    @Nested
    inner class FormFieldMatchReturnsAllErrors {
        val request = HttpRequest(method = "POST", path = "/", formFields = mapOf("hello" to "abc123"))

        val result = HttpRequestPattern(
            method = "POST",
            urlMatcher = URLMatcher(emptyMap(), emptyList(), "/"),
            formFieldsPattern = mapOf("hello" to NumberPattern(), "world" to NumberPattern())
        ).matches(request, Resolver())

        val reportText = result.reportString()

        @Test
        fun `returns all form field errors`() {
            result as Failure
            assertThat(result.toMatchFailureDetailList()).hasSize(2)
        }

        @Test
        fun `error fields are referenced in the report`() {
            assertThat(reportText).contains(""">> REQUEST.FORM-FIELDS.hello""")
            assertThat(reportText).contains(""">> REQUEST.FORM-FIELDS.world""")
        }

        @Test
        fun `presence errors appear before the payload errors`() {
            assertThat(reportText.indexOf(""">> REQUEST.FORM-FIELDS.world""")).isLessThan(reportText.indexOf(""">> REQUEST.FORM-FIELDS.hello"""))
        }
    }

    @Nested
    inner class MultiPartMatchReturnsAllErrors {
        val parts = listOf(
            MultiPartContentPattern("data1", NumberPattern()),
            MultiPartContentPattern("data2", NumberPattern()))
        val requestPattern = HttpRequestPattern(method = "POST", urlMatcher = toURLMatcherWithOptionalQueryParams("/"), multiPartFormDataPattern = parts)
        val request = HttpRequest("POST", "/", multiPartFormData = listOf(MultiPartContentValue("data1", StringValue("abc123"))))

        val result = requestPattern.matches(request, Resolver())
        val reportText = result.reportString()

        @Test
        fun `returns all multipart field errors`() {
            result as Failure
            assertThat(result.toMatchFailureDetailList()).hasSize(2)
        }

        @Test
        fun `error fields are referenced in the report`() {
            assertThat(reportText).contains(""">> REQUEST.MULTIPART-FORMDATA.data1""")
            assertThat(reportText).contains(""">> REQUEST.MULTIPART-FORMDATA.data2""")
        }

        @Test
        fun `presence errors appear before the payload errors`() {
            assertThat(reportText.indexOf(""">> REQUEST.MULTIPART-FORMDATA.data2""")).isLessThan(reportText.indexOf(""">> REQUEST.MULTIPART-FORMDATA.data1"""))
        }
    }

    @Test
    fun `should not generate test request for generative tests more than once`()  {
        val pattern = HttpRequestPattern(method = "POST", urlMatcher = toURLMatcherWithOptionalQueryParams("http://helloworld.com/data"), body = JSONObjectPattern(mapOf("id" to NumberPattern())))

        val row = Row(listOf("(REQUEST-BODY)"), listOf("""{ "id": 10 }"""))
        val patterns = pattern.newBasedOn(row, Resolver(generation = GenerativeTestsEnabled()))

        assertThat(patterns).hasSize(1)
    }
}
