package `in`.specmatic.stub

import `in`.specmatic.conversions.OpenApiSpecification
import `in`.specmatic.core.*
import `in`.specmatic.core.pattern.*
import `in`.specmatic.core.value.JSONObjectValue
import `in`.specmatic.core.value.NumberValue
import `in`.specmatic.core.value.StringValue
import `in`.specmatic.mock.DELAY_IN_SECONDS
import `in`.specmatic.mock.ScenarioStub
import `in`.specmatic.shouldMatch
import `in`.specmatic.test.HttpClient
import io.mockk.every
import io.mockk.mockk
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.RepeatedTest
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.condition.DisabledOnOs
import org.junit.jupiter.api.condition.OS
import org.junit.jupiter.api.fail
import org.springframework.http.HttpHeaders
import org.springframework.http.HttpMethod
import org.springframework.http.MediaType
import org.springframework.http.RequestEntity
import org.springframework.web.client.HttpClientErrorException
import org.springframework.web.client.RestTemplate
import org.springframework.web.client.getForEntity
import org.springframework.web.client.postForEntity
import java.net.URI
import java.nio.file.Paths
import java.util.*
import kotlin.io.path.absolutePathString
import kotlin.time.DurationUnit
import kotlin.time.ExperimentalTime
import kotlin.time.measureTime

internal class HttpStubTest {
    @Test
    fun `randomly generated HTTP response includes an HTTP header indicating that it was randomly generated`() {
        val gherkin = """
            Feature: API
            Scenario: API
                When GET /
                Then status 200
        """.trimIndent()

        val request = HttpRequest(method = "GET", path = "/")

        HttpStub(gherkin).use { stub ->
            val response = HttpClient(stub.endPoint).execute(request)
            assertThat(response.headers[SPECMATIC_TYPE_HEADER]).isEqualTo("random")
        }
    }

    @Test
    fun `should serve mocked data before stub`() {
        val gherkin = """
Feature: Math API

Scenario: Square of a number
  When POST /number
  And request-body (number)
  Then status 200
  And response-body (number)
""".trim()

        val request = HttpRequest(method = "POST", path = "/number", body = NumberValue(10))
        val response = HttpResponse(status = 200, body = "100")

        HttpStub(gherkin, listOf(ScenarioStub(request, response))).use { fake ->
            val postResponse = RestTemplate().postForEntity<String>(fake.endPoint + "/number", "10")
            assertThat(postResponse.body).isEqualTo("100")
        }
    }

    @Test
    fun `should accept mocks dynamically over http`() {
        val gherkin = """
Feature: Math API

Scenario: Get a number
  When GET /number
  Then status 200
  And response-body (number)
""".trim()

        HttpStub(gherkin).use { fake ->
            val mockData =
                """{"http-request": {"method": "GET", "path": "/number"}, "http-response": {"status": 200, "body": 10}}"""
            val stubSetupURL = "${fake.endPoint}/_specmatic/expectations"
            val headers = HttpHeaders()
            headers.contentType = MediaType.APPLICATION_JSON
            val stubRequest = RequestEntity(mockData, headers, HttpMethod.POST, URI.create(stubSetupURL))
            val stubResponse = RestTemplate().postForEntity<String>(stubSetupURL, stubRequest)
            assertThat(stubResponse.statusCodeValue).isEqualTo(200)

            val postResponse = RestTemplate().getForEntity<String>(fake.endPoint + "/number")
            assertThat(postResponse.body).isEqualTo("10")
        }
    }

    @Test
    @DisabledOnOs(OS.WINDOWS)
    fun `should accept mocks with externalised response command dynamically over http`() {
        val gherkin = """
Feature: Math API

Scenario: Multiply a number by 3
  When GET /multiply/(value:number)
  Then status 200
  And response-body (number)
""".trim()

        val testResourcesDir = Paths.get("src", "test", "resources")

        HttpStub(gherkin).use { fake ->
            val mockData =
                """{"http-request": {"method": "GET", "path": "/multiply/(value:number)"}, "http-response": {"status": 200, "body": 10, "externalisedResponseCommand": "${testResourcesDir.absolutePathString()}/response.sh 3"}}"""
            val stubSetupURL = "${fake.endPoint}/_specmatic/expectations"
            val headers = HttpHeaders()
            headers.contentType = MediaType.APPLICATION_JSON
            val stubRequest = RequestEntity(mockData, headers, HttpMethod.POST, URI.create(stubSetupURL))
            val stubResponse = RestTemplate().postForEntity<String>(stubSetupURL, stubRequest)
            assertThat(stubResponse.statusCodeValue).isEqualTo(200)

            val postResponse = RestTemplate().getForEntity<String>(fake.endPoint + "/multiply/5")
            assertThat(postResponse.body).isEqualTo("15")
        }
    }

    @Test
    fun `last dynamic mock should override earlier dynamic mocks`() {
        val gherkin = """
Feature: Math API

Scenario: Get a number
  When GET /number
  Then status 200
  And response-body (number)
""".trim()

        HttpStub(gherkin).use { fake ->
            testMock(
                """{"http-request": {"method": "GET", "path": "/number"}, "http-response": {"status": 200, "body": 10}}""",
                "10",
                fake
            )
            testMock(
                """{"http-request": {"method": "GET", "path": "/number"}, "http-response": {"status": 200, "body": 20}}""",
                "20",
                fake
            )
        }
    }

    private fun testMock(mockData: String, output: String, fake: HttpStub) {
        val stubSetupURL = "${fake.endPoint}/_specmatic/expectations"
        val headers = HttpHeaders()
        headers.contentType = MediaType.APPLICATION_JSON

        val stubRequest = RequestEntity(mockData, headers, HttpMethod.POST, URI.create(stubSetupURL))
        val stubResponse = RestTemplate().postForEntity<String>(stubSetupURL, stubRequest)
        assertThat(stubResponse.statusCodeValue).isEqualTo(200)

        val postResponse = RestTemplate().getForEntity<String>(fake.endPoint + "/number")
        assertThat(postResponse.body).isEqualTo(output)
    }

    @Test
    fun `it should accept (datetime) as a value in the request, and match datetime values against that type`() {
        val gherkin = """Feature: Calendar
Scenario: Accept a date
When POST /date
And request-body (datetime)
Then status 200
And response-body (string)
        """.trim()

        val request = HttpRequest("POST", "/date", emptyMap(), StringValue("(datetime)"))
        val mock = ScenarioStub(request, HttpResponse(200, "done"))

        HttpStub(gherkin, listOf(mock)).use { fake ->
            val postResponse =
                RestTemplate().postForEntity<String>(fake.endPoint + "/date", "2020-04-12T00:00:00+05:00")
            assertThat(postResponse.statusCode.value()).isEqualTo(200)
            assertThat(postResponse.body).isEqualTo("done")
        }
    }

    @Test
    fun `it should accept (datetime) as a mock value in a json request, and match datetime values against that type`() {
        val gherkin = """Feature: Calendar
Scenario: Accept a date
When POST /date
And request-body
 | date | (datetime) |
Then status 200
And response-body (string)
        """.trim()

        val request = HttpRequest("POST", "/date", emptyMap(), parsedValue("""{"date": "(datetime)"}"""))
        val mock = ScenarioStub(request, HttpResponse(200, "done"))

        HttpStub(gherkin, listOf(mock)).use { fake ->
            val postResponse =
                RestTemplate().postForEntity<String>(
                    fake.endPoint + "/date",
                    """{"date": "2020-04-12T00:00:00+05:00"}"""
                )
            assertThat(postResponse.statusCode.value()).isEqualTo(200)
            assertThat(postResponse.body).isEqualTo("done")
        }
    }

    @Test
    fun `it should not accept an incorrectly formatted value`() {
        val gherkin = """Feature: Calendar
Scenario: Accept a date
When POST /date
And request-body
 | date | (datetime) |
Then status 200
And response-body (string)
        """.trim()

        val request = HttpRequest("POST", "/date", emptyMap(), parsedValue("""{"date": "(datetime)"}"""))
        val mock = ScenarioStub(request, HttpResponse(200, "done"))

        try {
            HttpStub(gherkin, listOf(mock)).use { fake ->
                val postResponse =
                    RestTemplate().postForEntity<String>(fake.endPoint + "/date", """2020-04-12T00:00:00""")
                assertThat(postResponse.statusCode.value()).isEqualTo(200)
                assertThat(postResponse.body).isEqualTo("done")
            }
        } catch (e: HttpClientErrorException) {
            return
        }

        fail("Should have thrown an exception")
    }

    @ExperimentalTime
    @Test
    fun `it should accept a stub with a delay and introduce the delay before returning the stubbed response`() {
        val gherkin = """Feature: Data API
Scenario: Return data
When GET /data
Then status 200
And response-body (string)
        """.trim()

        try {
            HttpStub(gherkin).use { fake ->
                val expectation = """ {
"http-request": {
    "method": "GET",
    "path": "/data"
}, 
"http-response": {
    "status": 200,
    "body": "123"
},
"$DELAY_IN_SECONDS": 1
}""".trimIndent()

                val stubResponse =
                    RestTemplate().postForEntity<String>(fake.endPoint + "/_specmatic/expectations", expectation)
                assertThat(stubResponse.statusCode.value()).isEqualTo(200)

                val duration = measureTime {
                    val postResponse = RestTemplate().getForEntity<String>(URI.create(fake.endPoint + "/data"))
                    assertThat(postResponse.statusCode.value()).isEqualTo(200)
                    assertThat(postResponse.body).isEqualTo("123")
                }

                assertThat(duration.toLong(DurationUnit.MILLISECONDS)).isGreaterThanOrEqualTo(1000L)
            }
        } catch (e: HttpClientErrorException) {
            fail("Threw an exception: ${e.message}")
        }
    }

    @ExperimentalTime
    @RepeatedTest(2)
    fun `the delay in one expectation should not block other expectations responses`() {
        val gherkin = """Feature: Data API
Scenario: Return data
When GET /data/(id:number)
Then status 200
And response-body (string)
        """.trim()

        try {
            val delayInSeconds = 3
            val delayInMilliseconds = delayInSeconds * 1000

            HttpStub(gherkin).use { fake ->
                val expectation = """ {
"http-request": {
    "method": "GET",
    "path": "/data/1"
}, 
"http-response": {
    "status": 200,
    "body": "123"
},
"$DELAY_IN_SECONDS": $delayInSeconds
}""".trimIndent()

                val stubResponse =
                    RestTemplate().postForEntity<String>(fake.endPoint + "/_specmatic/expectations", expectation)
                assertThat(stubResponse.statusCode.value()).isEqualTo(200)

                data class TimedResponse(val id: Int, val duration: Long, val body: String)

                val responses = Vector<TimedResponse>()

                val threads = (1..10).map {
                    Thread {
                        var response: String

                        val duration = measureTime {
                            val postResponse =
                                RestTemplate().getForEntity<String>(URI.create(fake.endPoint + "/data/${it}"))
                            assertThat(postResponse.statusCode.value()).isEqualTo(200)
                            response = postResponse.body ?: ""
                        }

                        val durationAsLong = duration.toLong(DurationUnit.MILLISECONDS)
                        responses.add(TimedResponse(it, durationAsLong, response))
                    }
                }

                threads.forEach { it.start() }
                threads.forEach { it.join() }

                responses.forEach {
                    println(it)
                }

                assertThat(responses.size).isEqualTo(10)

                val delayedResponses = responses.filter { it.duration >= delayInMilliseconds }
                assertThat(delayedResponses.size).isOne

                delayedResponses.single().let { response ->
                    assertThat(response.id).isEqualTo(1)
                    assertThat(response.body).isEqualTo("123")
                }
            }
        } catch (e: HttpClientErrorException) {
            fail("Threw an exception: ${e.message}")
        }
    }

    @Test
    fun `it should be able to stub out xml`() {
        val gherkin = """Feature: Number
Scenario: Accept a number
When POST /number
And request-body <data>(number)</data>
Then status 200
        """.trim()

        val request = HttpRequest("POST", "/number", emptyMap(), parsedValue("""<data>10</data>"""))
        val mock = ScenarioStub(request, HttpResponse.OK)

        val postResponse = HttpStub(gherkin, listOf(mock)).use { fake ->
            RestTemplate().postForEntity<String>(fake.endPoint + "/number", """<data>10</data>""")
        }

        assertThat(postResponse.statusCode.value()).isEqualTo(200)
    }

    @Test
    fun `it should be able to stub out xml containing an optional number value`() {
        val gherkin = """Feature: Number
Scenario: Accept a number
When POST /number
And request-body <data>(number?)</data>
Then status 200
        """.trim()

        val request = HttpRequest("POST", "/number", emptyMap(), parsedValue("""<data>10</data>"""))
        val mock = ScenarioStub(request, HttpResponse.OK)

        val postResponse = HttpStub(gherkin, listOf(mock)).use { fake ->
            RestTemplate().postForEntity<String>(fake.endPoint + "/number", """<data>10</data>""")
        }

        assertThat(postResponse.statusCode.value()).isEqualTo(200)
    }

    @Test
    fun `it should be able to stub out xml containing an optional number value using a type`() {
        val gherkin = """Feature: Number
Scenario: Accept a number
When POST /number
And request-body <data>(number?)</data>
Then status 200
        """.trim()

        val request = HttpRequest("POST", "/number", emptyMap(), parsedValue("""<data>(number)</data>"""))
        val mock = ScenarioStub(request, HttpResponse.OK)

        val postResponse = HttpStub(gherkin, listOf(mock)).use { fake ->
            RestTemplate().postForEntity<String>(fake.endPoint + "/number", """<data>10</data>""")
        }

        assertThat(postResponse.statusCode.value()).isEqualTo(200)
    }

    @Test
    fun `if the xml request value does not match but structure does then it returns a fake response`() {
        val gherkin = """Feature: Number
Scenario: Accept a number
When POST /number
And request-body <data>(number?)</data>
Then status 200
And response-body (number)
        """.trim()

        val request = HttpRequest("POST", "/number", emptyMap(), parsedValue("""<data>10</data>"""))
        val expectedNumber = 100000
        val mock = ScenarioStub(request, HttpResponse.ok(NumberValue(expectedNumber)))

        HttpStub(gherkin, listOf(mock)).use { fake ->
            RestTemplate().postForEntity<String>(fake.endPoint + "/number", """<data>10</data>""")
        }.let { postResponse ->
            assertThat(postResponse.statusCode.value()).isEqualTo(200)
            assertThat(postResponse.body?.toString()?.toInt() == expectedNumber)
        }

        HttpStub(gherkin, listOf(mock)).use { fake ->
            RestTemplate().postForEntity<String>(fake.endPoint + "/number", """<data>20</data>""")
        }.let { postResponse ->
            assertThat(postResponse.statusCode.value()).isEqualTo(200)
            assertThat(postResponse.body?.toString()?.toInt() != expectedNumber)
        }
    }

    @Test
    fun `it should be able to stub out xml containing an optional number value with an empty string`() {
        val gherkin = """Feature: Number
Scenario: Accept a number
When POST /number
And request-body <data>(number?)</data>
Then status 200
        """.trim()

        val request = HttpRequest("POST", "/number", emptyMap(), parsedValue("""<data></data>"""))
        val mock = ScenarioStub(request, HttpResponse.OK)

        val postResponse = HttpStub(gherkin, listOf(mock)).use { fake ->
            RestTemplate().postForEntity<String>(fake.endPoint + "/number", """<data></data>""")
        }

        assertThat(postResponse.statusCode.value()).isEqualTo(200)
    }

    @Test
    fun `it should be able to stub out xml containing an optional number value with an empty node`() {
        val gherkin = """Feature: Number
Scenario: Accept a number
When POST /number
And request-body <data>(number?)</data>
Then status 200
        """.trim()

        val request = HttpRequest("POST", "/number", emptyMap(), parsedValue("""<data></data>"""))
        val mock = ScenarioStub(request, HttpResponse.OK)

        val postResponse = HttpStub(gherkin, listOf(mock)).use { fake ->
            RestTemplate().postForEntity<String>(fake.endPoint + "/number", """<data/>""")
        }

        assertThat(postResponse.statusCode.value()).isEqualTo(200)
    }

    @Test
    fun `it should be able to stub out xml with an attribute`() {
        val gherkin = """Feature: Number
Scenario: Accept a number
When POST /number
And request-body <data number="(number)"/>
Then status 200
        """.trim()

        val request = HttpRequest("POST", "/number", emptyMap(), parsedValue("""<data number="10"/>"""))
        val mock = ScenarioStub(request, HttpResponse.OK)

        val postResponse = HttpStub(gherkin, listOf(mock)).use { fake ->
            RestTemplate().postForEntity<String>(fake.endPoint + "/number", """<data number="10"/>""")
        }

        assertThat(postResponse.statusCode.value()).isEqualTo(200)
    }

    @Test
    fun `it should be able to stub out xml with an attribute using a type`() {
        val gherkin = """Feature: Number
Scenario: Accept a number
When POST /number
And request-body <data number="(number)"/>
Then status 200
        """.trim()

        val request = HttpRequest("POST", "/number", emptyMap(), parsedValue("""<data number="(number)"/>"""))
        val mock = ScenarioStub(request, HttpResponse.OK)

        val postResponse = HttpStub(gherkin, listOf(mock)).use { fake ->
            RestTemplate().postForEntity<String>(fake.endPoint + "/number", """<data number="10"/>""")
        }

        assertThat(postResponse.statusCode.value()).isEqualTo(200)
    }

    @Test
    fun `it should be able to stub out xml with an optional attribute when specifying an attribute value in the stub`() {
        val gherkin = """Feature: Number
Scenario: Accept a number
When POST /number
And request-body <data number$XML_ATTR_OPTIONAL_SUFFIX="(number)"/>
Then status 200
        """.trim()

        val request = HttpRequest("POST", "/number", emptyMap(), parsedValue("""<data number="10"/>"""))
        val mock = ScenarioStub(request, HttpResponse.OK)

        val postResponse = HttpStub(gherkin, listOf(mock)).use { fake ->
            RestTemplate().postForEntity<String>(fake.endPoint + "/number", """<data number="10"/>""")
        }

        assertThat(postResponse.statusCode.value()).isEqualTo(200)
    }

    @Test
    fun `it should be able to stub out xml with an optional attribute using no attribute in the stub`() {
        val gherkin = """Feature: Number
Scenario: Accept a number
When POST /number
And request-body <data number$XML_ATTR_OPTIONAL_SUFFIX="(number)"/>
Then status 200
        """.trim()

        val request = HttpRequest("POST", "/number", emptyMap(), parsedValue("""<data/>"""))
        val mock = ScenarioStub(request, HttpResponse.OK)

        val postResponse = HttpStub(gherkin, listOf(mock)).use { fake ->
            RestTemplate().postForEntity<String>(fake.endPoint + "/number", """<data/>""")
        }

        assertThat(postResponse.statusCode.value()).isEqualTo(200)
    }

    @Test
    fun `generate a bad request from an error message`() {
        val expectedResponse = HttpResponse(
            status = 400,
            headers = mapOf(SPECMATIC_RESULT_HEADER to "failure"),
            body = StringValue("error occurred")
        )
        assertThat(badRequest("error occurred")).isEqualTo(expectedResponse)
    }

    @Test
    fun `should be able to query stub logs`() {
        val feature = parseGherkinStringToFeature(
            """
Feature: Math API

Scenario: Square of a number
  When POST /number
  And request-body (number)
  Then status 200
  And response-body (number)
""".trim()
        )

        HttpStub(listOf(feature, feature)).use { stub ->
            val client = HttpClient(stub.endPoint)
            val request = HttpRequest(method = "POST", path = "/wrong_path", body = NumberValue(10))
            val squareResponse = client.execute(request)
            assertThat(squareResponse.status).isEqualTo(400)
            assertThat(squareResponse.body.toStringLiteral()).isEqualTo(request.requestNotRecognized())
        }
    }

    @Test
    fun `it should proxy all unstubbed requests to the specified end point`() {
        val feature = parseGherkinStringToFeature(
            """
Feature: Math API

Scenario: Square of a number
  When POST /
  And request-body (number)
  Then status 200
  And response-body (string)
""".trim()
        )

        val httpClient = mockk<HttpClient>()
        every { httpClient.execute(any()) } returns (HttpResponse.ok("it worked"))

        val httpClientFactory = mockk<HttpClientFactory>()
        every { httpClientFactory.client(any()) } returns (httpClient)

        HttpStub(
            listOf(feature),
            passThroughTargetBase = "http://example.com",
            httpClientFactory = httpClientFactory
        ).use { stub ->
            val client = HttpClient(stub.endPoint)
            val response = client.execute(HttpRequest(method = "POST", path = "/", body = NumberValue(10)))

            assertThat(response.status).isEqualTo(200)
            assertThat(response.body.toStringLiteral()).isEqualTo("it worked")
            assertThat(response.headers[SPECMATIC_SOURCE_HEADER]).isEqualTo("proxy")
        }
    }

    @Test
    fun `a proxied response should contain the header X-Qontract-Source`() {
        val feature = parseGherkinStringToFeature(
            """
Feature: Math API

Scenario: Square of a number
  When POST /
  And request-body (number)
  Then status 200
  And response-body (string)
""".trim()
        )

        val httpClient = mockk<HttpClient>()
        every { httpClient.execute(any()) } returns (HttpResponse.ok("it worked"))

        val httpClientFactory = mockk<HttpClientFactory>()
        every { httpClientFactory.client(any()) } returns (httpClient)

        HttpStub(
            listOf(feature),
            passThroughTargetBase = "http://example.com",
            httpClientFactory = httpClientFactory
        ).use { stub ->
            val client = HttpClient(stub.endPoint)
            val response = client.execute(HttpRequest(method = "POST", path = "/", body = NumberValue(10)))

            assertThat(response.headers[SPECMATIC_SOURCE_HEADER]).isEqualTo("proxy")
        }
    }

    @Test
    fun `it should not proxy stubbed requests`() {
        val feature = parseGherkinStringToFeature(
            """
Feature: Math API

Scenario: Square of a number
  When POST /
  And request-body (number)
  Then status 200
  And response-body (string)
""".trim()
        )

        val httpClient = mockk<HttpClient>()
        every { httpClient.execute(any()) } returns (HttpResponse.ok("should not get here"))

        val httpClientFactory = mockk<HttpClientFactory>()
        every { httpClientFactory.client(any()) } returns (httpClient)

        HttpStub(
            listOf(feature),
            passThroughTargetBase = "http://example.com",
            httpClientFactory = httpClientFactory
        ).use { stub ->
            stub.setExpectation(
                ScenarioStub(
                    HttpRequest("POST", "/", body = NumberValue(10)),
                    HttpResponse.ok("success")
                )
            )
            val client = HttpClient(stub.endPoint)
            val response = client.execute(HttpRequest(method = "POST", path = "/", body = NumberValue(10)))

            assertThat(response.status).isEqualTo(200)
            assertThat(response.body.toStringLiteral()).isEqualTo("success")
        }
    }

    @Test
    fun `should stub out a request with body string matching a regex`() {
        val contract = OpenApiSpecification.fromYAML(
            """
openapi: 3.0.1
info:
  title: Data API
  version: "1"
paths:
  /:
    post:
      summary: Data
      parameters: []
      requestBody:
        content:
          text/plain:
            schema:
              type: string
      responses:
        "200":
          description: Data
          content:
            text/plain:
              schema:
                type: string
""".trim(), ""
        ).toFeature()

        HttpStub(contract).use { stub ->
            val stubData = """
                {
                    "http-request": {
                        "method": "POST",
                        "path": "/",
                        "body": "(string)",
                        "bodyRegex": "^hello (.*)$"
                    },
                    "http-response": {
                        "status": 200,
                        "body": "Hi!"
                    }
                }
            """.trimIndent()

            val stubRequest = HttpRequest("POST", "/_specmatic/expectations", emptyMap(), StringValue(stubData))
            val stubResponse = stub.client.execute(stubRequest)

            assertThat(stubResponse.status).isEqualTo(200)

            HttpRequest("POST", "/", emptyMap(), StringValue("hello world")).let { actualRequest ->
                val actualResponse = stub.client.execute(actualRequest)
                assertThat(actualResponse.body.toStringLiteral()).isEqualTo("Hi!")
            }

            HttpRequest("POST", "/", emptyMap(), StringValue("hi world")).let { actualRequest ->
                val actualResponse = stub.client.execute(actualRequest)
                assertThat(actualResponse.headers).containsEntry("X-Specmatic-Type", "random")
            }
        }
    }

    @Test
    fun `persistent stubs should not populate the transient stub queue`() {
        val contract = OpenApiSpecification.fromYAML(
            """
openapi: 3.0.1
info:
  title: Data API
  version: "1"
paths:
  /:
    post:
      summary: Data
      parameters: []
      requestBody:
        content:
          text/plain:
            schema:
              type: string
      responses:
        "200":
          description: Data
          content:
            text/plain:
              schema:
                type: string
""".trim(), ""
        ).toFeature()

        HttpStub(
            contract, listOf(
                ScenarioStub(
                    HttpRequest(
                        method = "POST",
                        path = "/",
                        body = StringValue("(string)")
                    ),
                    HttpResponse(
                        status = 200,
                        body = "Hi!"
                    )
                )
            )
        ).use { stub ->
            assertThat(stub.stubCount).isEqualTo(1)
            assertThat(stub.transientStubCount).isEqualTo(0)
        }
    }

    @Nested
    inner class ExpectationPriorities {
        private val featureWithBodyExamples = OpenApiSpecification.fromYAML(
            """
openapi: 3.0.1
info:
  title: Data API
  version: "1"
paths:
  /:
    post:
      summary: Data
      parameters: []
      requestBody:
        content:
          application/json:
            schema:
              type: object
              properties:
                id:
                  type: integer
              required:
                - id
            examples:
              200_OK:
                value:
                  id: 10
      responses:
        "200":
          description: Data
          content:
            application/json:
              schema:
                type: object
                properties:
                  message:
                    type: string
                required:
                  - message
              examples:
                200_OK:
                  value:
                    message: example_expectation
""".trim(), ""
        ).toFeature()

        @Test
        fun `expectations for payload from examples`() {
            HttpStub(featureWithBodyExamples).use { stub ->
                stub.client.execute(HttpRequest("POST", "/", emptyMap(), parsedJSONObject("""{"id": 10}""")))
                    .let { response ->
                        assertThat(response.status).isEqualTo(200)
                        assertThat(response.body).isEqualTo(parsedJSONObject("""{"message":"example_expectation"}"""))
                    }
            }
        }

        @Test
        fun `expectations from examples should have less priority than file expectations`() {
            HttpStub(
                featureWithBodyExamples, listOf(
                    ScenarioStub(
                        HttpRequest(
                            method = "POST",
                            path = "/",
                            body = parsedJSONObject("""{"id": 10}""")
                        ),
                        HttpResponse(
                            status = 200,
                            body = parsedJSONObject("""{"message":"file_overrides_example_expectation"}""")
                        )
                    )
                )
            ).use { stub ->
                stub.client.execute(HttpRequest("POST", "/", emptyMap(), parsedJSONObject("""{"id": 10}""")))
                    .let { response ->
                        assertThat(response.status).isEqualTo(200)
                        assertThat(response.body).isEqualTo(parsedJSONObject("""{"message":"file_overrides_example_expectation"}"""))
                    }
            }
        }

        @Test
        fun `expectations from examples should have less priority than dynamic expectations`() {
            HttpStub(featureWithBodyExamples).use { stub ->
                stub.setExpectation(
                    ScenarioStub(
                        HttpRequest(
                            method = "POST",
                            path = "/",
                            body = parsedJSONObject("""{"id": 10}""")
                        ),
                        HttpResponse(
                            status = 200,
                            body = parsedJSONObject("""{"message":"dynamic_overrides_example_expectation"}""")
                        )
                    )
                )
                stub.client.execute(HttpRequest("POST", "/", emptyMap(), parsedJSONObject("""{"id": 10}""")))
                    .let { response ->
                        assertThat(response.status).isEqualTo(200)
                        assertThat(response.body).isEqualTo(parsedJSONObject("""{"message":"dynamic_overrides_example_expectation"}"""))
                    }
            }
        }

        @Test
        fun `should generate response as a json object with strings keys with values of any type when additional properties is set as true`() {
            val openAPI =
                """
---
openapi: 3.0.1
info:
  title: API
  version: 1
paths:
  /data:
    get:
      summary: Retrieve data
      responses:
        '200':
          description: Successful response
          content:
            application/json:
              schema:
                type: object
                additionalProperties: true
""".trimIndent()
            val feature = OpenApiSpecification.fromYAML(openAPI, "").toFeature()
            HttpStub(feature).use { stub ->
                stub.client.execute(HttpRequest("GET", "/data")).let { response ->
                    assertThat(response.status).isEqualTo(200)
                    val responseValue = parsedJSON(response.body.toStringLiteral())
                    responseValue shouldMatch DictionaryPattern(StringPattern(), AnythingPattern)
                }
            }

        }

        @Test
        fun `should return stubbed response based on expectations set when additional properties is set as true`() {
            val openAPI =
                """
---
openapi: 3.0.1
info:
  title: API
  version: 1
paths:
  /data:
    post:
      summary: API
      parameters: []
      requestBody:
        content:
          application/json:
            schema:
              type: object
              additionalProperties: true
      responses:
        '200':
          description: Successful response
          content:
            application/json:
              schema:
                type: string
""".trimIndent()
            val feature = OpenApiSpecification.fromYAML(openAPI, "").toFeature()
            HttpStub(feature).use { stub ->
                stub.setExpectation(
                    ScenarioStub(
                        HttpRequest(
                            method = "POST",
                            path = "/data",
                            body = parsedJSONObject("""{"id": 10}""")
                        ),
                        HttpResponse(
                            status = 200,
                            body = StringValue("response data")
                        )
                    )
                )
                stub.client.execute(HttpRequest("POST", "/data", emptyMap(), parsedJSONObject("""{"id": 10}""")))
                    .let { response ->
                        assertThat(response.status).isEqualTo(200)
                        assertThat(response.body).isEqualTo(StringValue("response data"))
                    }
            }
        }

        @Test
        fun `should return descriptive error message when there are no valid specifications loaded`() {
            val httpStub = HttpStub(emptyList())
            httpStub.use { stub ->
                val response =
                    stub.client.execute(HttpRequest("POST", "/data", emptyMap(), parsedJSONObject("""{"id": 10}""")))
                assertThat(response.status).isEqualTo(400)
                assertThat(response.body).isEqualTo(StringValue("No valid API specifications loaded"))
            }
        }
    }

    @Test
    fun `should stub out a path having a space and return a randomised response`() {
        val pathWithSpace = "/da ta"

        val specification =
            OpenApiSpecification.fromFile("src/test/resources/openapi/spec_with_space_in_path.yaml").toFeature()

        HttpStub(specification).use { stub ->
            val request = HttpRequest("GET", pathWithSpace)

            val response = stub.client.execute(request)

            assertThat(response.status).isEqualTo(200)
            response.body.let {
                assertThat(it).isInstanceOf(JSONObjectValue::class.java)
                it as JSONObjectValue

                assertThat(it.jsonObject).containsKey("id")
                assertThat(it.jsonObject["id"]).isInstanceOf(NumberValue::class.java)
            }
        }
    }

    @Test
    fun `should load a stub with a space in the path and return the stubbed response`() {
        val pathWithSpace = "/da ta"

        createStubFromContracts(listOf("src/test/resources/openapi/spec_with_space_in_path.yaml")).use { stub ->
            val request = HttpRequest("GET", pathWithSpace)

            val response = stub.client.execute(request)

            assertThat(response.status).isEqualTo(200)
            response.body.let {
                assertThat(it).isInstanceOf(JSONObjectValue::class.java)
                it as JSONObjectValue

                assertThat(it.jsonObject).containsEntry("id", NumberValue(10))
            }
        }
    }

    @Test
    fun `should load a stub with query params and a space in the path and return the stubbed response`() {
        val pathWithSpace = "/da ta"

        createStubFromContracts(listOf("src/test/resources/openapi/spec_with_query_and_space_in_path.yaml")).use { stub ->
            val request = HttpRequest("GET", pathWithSpace, queryParams = mapOf("id" to "5"))

            val response = stub.client.execute(request)

            assertThat(response.status).isEqualTo(200)
            response.body.let {
                assertThat(it).isInstanceOf(JSONObjectValue::class.java)
                it as JSONObjectValue

                assertThat(it.jsonObject).containsEntry("id", NumberValue(10))
            }
        }
    }

    @Test
    fun `should load a stub with a space in query params and return the stubbed response`() {
        val queryParamWithSpace = "id entifier"

        val specification = OpenApiSpecification.fromYAML("""
            openapi: 3.0.1
            info:
              title: Random
              version: "1"
            paths:
              /data:
                get:
                  summary: Random
                  parameters:
                    - name: $queryParamWithSpace
                      in: query
                      schema:
                        type: integer
                  responses:
                    "200":
                      description: Random
                      content:
                        application/json:
                          schema:
                            type: object
                            properties:
                              id:
                                type: integer
        """.trimIndent(), ""
        ).toFeature()

        HttpStub(specification).use { stub ->
            val request = HttpRequest("GET", "/data", queryParams = mapOf(queryParamWithSpace to "5"))

            val response = stub.client.execute(request)

            assertThat(response.status).isEqualTo(200)
            response.body.let {
                assertThat(it).isInstanceOf(JSONObjectValue::class.java)
                it as JSONObjectValue

                assertThat(it.jsonObject["id"]).isInstanceOf(NumberValue::class.java)
            }
        }
    }

    @Test
    fun `should stub out a request for boolean query param with capital T or F in the incoming request`() {
        val specification = createStubFromContracts(listOf("src/test/resources/openapi/spec_with_boolean_query.yaml"))

        specification.use { stub ->
            val request = HttpRequest("GET", "/data", queryParams = mapOf("enabled" to "True"))

            val response = stub.client.execute(request)

            assertThat(response.status).isEqualTo(200)
            response.body.let {
                assertThat(it).isInstanceOf(JSONObjectValue::class.java)
                it as JSONObjectValue

                assertThat(it.jsonObject["id"]).isInstanceOf(NumberValue::class.java)
            }
        }
    }

    @Test
    fun `should recognize a request for boolean query param with capital T or F in the incoming request`() {
        val specification = OpenApiSpecification.fromYAML(
            """
            openapi: 3.0.1
            info:
              title: Random
              version: "1"
            paths:
              /data:
                get:
                  summary: Random
                  parameters:
                    - name: enabled
                      in: query
                      schema:
                        type: boolean
                  responses:
                    "200":
                      description: Random
                      content:
                        application/json:
                          schema:
                            type: object
                            properties:
                              id:
                                type: integer
        """.trimIndent(), ""
        ).toFeature()

        HttpStub(specification).use { stub ->
            val request = HttpRequest("GET", "/data", queryParams = mapOf("enabled" to "True"))

            val response = stub.client.execute(request)

            assertThat(response.status).isEqualTo(200)
            response.body.let {
                assertThat(it).isInstanceOf(JSONObjectValue::class.java)
                it as JSONObjectValue

                assertThat(it.jsonObject["id"]).isInstanceOf(NumberValue::class.java)
            }
        }
    }

    @Test
    fun `stub out a spec with no request body and respond to a request which has no body`() {
        val specification = OpenApiSpecification.fromYAML("""
            openapi: 3.0.1
            info:
              title: Random
              version: "1"
            paths:
              /data:
                get:
                  summary: Random
                  responses:
                    "200":
                      description: Random
                      content:
                        application/json:
                          schema:
                            type: object
                            properties:
                              id:
                                type: integer
        """.trimIndent(), "").toFeature()

        HttpStub(specification).use { stub ->
            val request = HttpRequest("GET", "/data", body = NoBodyValue)

            val response = stub.client.execute(request)

            assertThat(response.status).isEqualTo(200)
            response.body.let {
                assertThat(it).isInstanceOf(JSONObjectValue::class.java)
                it as JSONObjectValue

                assertThat(it.jsonObject["id"]).isInstanceOf(NumberValue::class.java)
            }
        }
    }

    @Test
    fun `stub should load an expectation for a spec with no request body and respond to a request in the expectation`() {
        val specification = OpenApiSpecification.fromYAML("""
            openapi: 3.0.1
            info:
              title: Random
              version: "1"
            paths:
              /data:
                get:
                  summary: Random
                  responses:
                    "200":
                      description: Random
                      content:
                        application/json:
                          schema:
                            type: object
                            properties:
                              id:
                                type: integer
        """.trimIndent(), "").toFeature()

        HttpStub(specification).use { stub ->
            val request = HttpRequest("GET", "/data", body = NoBodyValue)

            val response = stub.client.execute(request)

            assertThat(response.status).isEqualTo(200)
            response.body.let {
                assertThat(it).isInstanceOf(JSONObjectValue::class.java)
                it as JSONObjectValue

                assertThat(it.jsonObject["id"]).isInstanceOf(NumberValue::class.java)
            }
        }
    }
}
