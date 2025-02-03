package io.specmatic.stub

import io.specmatic.conversions.OpenApiSpecification
import io.specmatic.core.*
import io.specmatic.core.pattern.*
import io.specmatic.core.value.JSONObjectValue
import io.specmatic.core.value.NumberValue
import io.specmatic.core.value.StringValue
import io.specmatic.mock.DELAY_IN_SECONDS
import io.specmatic.mock.ScenarioStub
import io.specmatic.osAgnosticPath
import io.specmatic.shouldMatch
import io.specmatic.test.HttpClient
import io.specmatic.test.TestExecutor
import io.mockk.every
import io.mockk.mockk
import io.specmatic.core.utilities.ContractPathData
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.RepeatedTest
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.condition.DisabledOnOs
import org.junit.jupiter.api.condition.OS
import org.junit.jupiter.api.fail
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.CsvSource
import org.springframework.http.HttpHeaders
import org.springframework.http.HttpMethod
import org.springframework.http.MediaType
import org.springframework.http.RequestEntity
import org.springframework.web.client.HttpClientErrorException
import org.springframework.web.client.RestTemplate
import org.springframework.web.client.getForEntity
import org.springframework.web.client.postForEntity
import java.io.File
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
    fun `a proxied response should contain the header X-Specmatic-Source`() {
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
            text/plain:
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
    fun `should return health status as UP if the actuator health endpoint is hit`() {
        val specification =
            OpenApiSpecification.fromFile("src/test/resources/openapi/spec_with_space_in_path.yaml").toFeature()

        HttpStub(specification).use { stub ->
            val request = HttpRequest("GET", "/actuator/health")

            val response = stub.client.execute(request)

            assertThat(response.status).isEqualTo(200)
            response.body.let {
                assertThat(it).isInstanceOf(JSONObjectValue::class.java)
                it as JSONObjectValue

                assertThat(it.jsonObject["status"]?.toStringLiteral()).isEqualTo("UP")
            }
        }
    }

    @Test
    fun `should load a stub with a space in the path and return the stubbed response`() {
        val pathWithSpace = "/da ta"

        createStubFromContracts(listOf("src/test/resources/openapi/spec_with_space_in_path.yaml"), timeoutMillis = 0).use { stub ->
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

        createStubFromContracts(listOf("src/test/resources/openapi/spec_with_query_and_space_in_path.yaml"), timeoutMillis = 0).use { stub ->
            val request = HttpRequest("GET", pathWithSpace, queryParametersMap = mapOf("id" to "5"))

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
            val request = HttpRequest("GET", "/data", queryParametersMap = mapOf(queryParamWithSpace to "5"))

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
        val specification = createStubFromContracts(listOf("src/test/resources/openapi/spec_with_boolean_query.yaml"), timeoutMillis = 0)

        specification.use { stub ->
            val request = HttpRequest("GET", "/data", queryParametersMap = mapOf("enabled" to "True"))

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
            val request = HttpRequest("GET", "/data", queryParametersMap = mapOf("enabled" to "True"))

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


    @Test
    fun `should return randomized response with Content-Type as per the specification`() {
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
                  responses:
                    "200":
                      description: Random
                      content:
                        text/html:
                          schema:
                            type: string
        """.trimIndent(), ""
        ).toFeature()

        HttpStub(specification).use { stub ->
            val request = HttpRequest("GET", "/data", body = NoBodyValue)

            val response = stub.client.execute(request)

            assertThat(response.headers["Content-Type"]).isEqualTo("text/html")
        }
    }

    @Test
    fun `should return stubbed response with Content-Type as per the specification`() {
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
                  responses:
                    "200":
                      description: Random
                      content:
                        text/html:
                          schema:
                            type: string
        """.trimIndent(), ""
        ).toFeature()

        HttpStub(specification).use { stub ->
            val request = HttpRequest("GET", "/data", body = NoBodyValue)

            val htmlContent = "<html><body>hi</body></html>"
            val expectation = ScenarioStub(
                request,
                HttpResponse(200, headers = mapOf("Content-Type" to "text/html"), body = htmlContent)
            )

            val expectationResponse = stub.client.execute(HttpRequest("POST", "/_specmatic/expectations", body = expectation.toJSON()))

            assertThat(expectationResponse.status).isEqualTo(200)

            val response = stub.client.execute(request)

            assertThat(response.headers["Content-Type"]).isEqualTo("text/html")
            assertThat(response.body.toStringLiteral()).isEqualTo(htmlContent)
        }
    }

    @Test
    fun `should reject expectation with Content-Type which is not as per the specification`() {
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
                  responses:
                    "200":
                      description: Random
                      content:
                        text/html:
                          schema:
                            type: string
        """.trimIndent(), ""
        ).toFeature()

        HttpStub(specification).use { stub ->
            val request = HttpRequest("GET", "/data", body = NoBodyValue)

            val htmlContent = "<html><body>hi</body></html>"

            val incorrectContentType = "text/plain"
            val expectationWithIncorrectContentType =
                ScenarioStub(
                    request,
                    HttpResponse(200, headers = mapOf("Content-Type" to incorrectContentType), body = htmlContent)
                )

            stub.client.execute(HttpRequest("POST", "/_specmatic/expectations", body = expectationWithIncorrectContentType.toJSON())).let { response ->
                assertThat(response.status).isEqualTo(400)
            }

            val correctContentType = "text/html"
            val expectationWithValidContentType =
                ScenarioStub(
                    request,
                    HttpResponse(200, headers = mapOf("Content-Type" to correctContentType), body = htmlContent)
                )

            stub.client.execute(HttpRequest("POST", "/_specmatic/expectations", body = expectationWithValidContentType.toJSON())).let { response ->
                assertThat(response.status).isEqualTo(200)
            }
        }
    }

    @Test
    fun `should fail request with incorrect response Content-Type`() {
        val feature = OpenApiSpecification.fromYAML(
            """
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
                        text/html:
                          schema:
                            type: string
        """.trimIndent(), ""
        ).toFeature()

        val results = feature.executeTests(object : TestExecutor {
            override fun execute(request: HttpRequest): HttpResponse {
                return HttpResponse(200, headers = mapOf("Content-Type" to "text/plain"), body = "hi")
            }
        })

        assertThat(results.success()).withFailMessage(results.report()).isFalse()
        assertThat(results.report()).withFailMessage(results.report()).contains("Content-Type")
    }

    @Test
    fun `should be able to set expectations for an API with a security scheme`() {
        val feature = OpenApiSpecification.fromYAML(
            """
openapi: 3.0.0
info:
  title: Sample API
  description: Optional multiline or single-line description in [CommonMark](http://commonmark.org/help/) or HTML.
  version: 0.1.9
servers:
  - url: http://api.example.com/v1
    description: Optional server description, e.g. Main (production) server
  - url: http://staging-api.example.com
    description: Optional server description, e.g. Internal staging server for testing
paths:
  /hello:
    post:
      security:
        - BearerAuth: []
      summary: hello world
      description: Optional extended description in CommonMark or HTML.
      requestBody:
        content:
          application/json:
            schema:
              type: object
              required:
                - message
              properties:
                message:
                  type: string
            examples:
              SUCCESS:
                value:
                  message: Hello World!
      responses:
        '200':
          description: Says hello
          content:
            text/plain:
              schema:
                type: string
              examples:
                SUCCESS:
                  value:
                    Hello to you!
components:
  securitySchemes:
    BearerAuth:
      type: http
      scheme: bearer
         """.trimIndent(), ""
        ).toFeature()

        val credentials = "Basic " + Base64.getEncoder().encodeToString("user:password".toByteArray())

        HttpStub(feature).use { stub ->
            val request = HttpRequest(
                "POST",
                "/hello",
                mapOf("Authorization" to "Bearer $credentials", "Content-Type" to "application/json"),
                parsedJSONObject("""{"message": "Hello there!"}""")
            )

            val expectedResponse = HttpResponse.ok("success")

            stub.setExpectation(ScenarioStub(request, expectedResponse))

            val response = stub.client.execute(request)

            assertThat(response.body).isEqualTo(StringValue("success"))
        }
    }

    @Test
    fun `should be able to load an externalized stub with an auth key`() {
        val feature = OpenApiSpecification.fromFile(osAgnosticPath("src/test/resources/openapi/apiKeyAuthStub.yaml")).toFeature()

        val examples = File(osAgnosticPath("src/test/resources/openapi/apiKeyAuthStub_examples")).listFiles().orEmpty().map { file ->
            ScenarioStub.parse(file.readText())
        }

        HttpStub(feature, examples).use { stub ->
            val request = HttpRequest(
                "GET",
                "/hello/10",
                mapOf("X-API-KEY" to "abc123")
            )

            val response = stub.client.execute(request)

            assertThat(response.body.toStringLiteral()).isEqualTo("Hello, World!")
        }
    }

    @Test
    fun `a generative stub should respond to an invalid request with a 400 per the spec with the error in the message key`() {
        val spec = """
            openapi: 3.0.0
            info:
              title: Sample API
              description: Optional multiline or single-line description in [CommonMark](http://commonmark.org/help/) or HTML.
              version: 0.1.9
            servers:
              - url: http://api.example.com/v1
                description: Optional server description, e.g. Main (production) server
              - url: http://staging-api.example.com
                description: Optional server description, e.g. Internal staging server for testing
            paths:
              /hello:
                post:
                  summary: hello world
                  description: Optional extended description in CommonMark or HTML.
                  requestBody:
                    content:
                      application/json:
                        schema:
                          type: object
                          required:
                            - data
                          properties:
                            data:
                              type: string
                  responses:
                    '200':
                      description: Says hello
                      content:
                        text/plain:
                          schema:
                            type: string
                    '400':
                      description: Bad request
                      content:
                        application/json:
                          schema:
                            type: object
                            required:
                              - message
                            properties:
                              message:
                                type: string
        """.trimIndent()

        val feature = OpenApiSpecification.fromYAML(spec, "").toFeature()

        HttpStub(listOf(feature), specmaticConfigPath = "src/test/resources/specmatic_config_wtih_generate_stub.yaml").use { stub ->
            val request = HttpRequest("POST", path = "/hello", body = parsedJSONObject("""{"data": 10}"""))
            val response = stub.client.execute(request)

            assertThat(response.status).isEqualTo(400)
            assertThat(response.body).isInstanceOf(JSONObjectValue::class.java)

            val responseBody = response.body as JSONObjectValue
            assertThat(responseBody.jsonObject["message"]?.toStringLiteral()).contains("REQUEST.BODY.data")
        }
    }

    @Test
    fun `a generative stub should respond to an invalid request with a 422 with the error in the message key`() {
        val spec = """
            openapi: 3.0.0
            info:
              title: Sample API
              description: Optional multiline or single-line description in [CommonMark](http://commonmark.org/help/) or HTML.
              version: 0.1.9
            servers:
              - url: http://api.example.com/v1
                description: Optional server description, e.g. Main (production) server
              - url: http://staging-api.example.com
                description: Optional server description, e.g. Internal staging server for testing
            paths:
              /hello:
                post:
                  summary: hello world
                  description: Optional extended description in CommonMark or HTML.
                  requestBody:
                    content:
                      application/json:
                        schema:
                          type: object
                          required:
                            - data
                          properties:
                            data:
                              type: string
                  responses:
                    '200':
                      description: Says hello
                      content:
                        text/plain:
                          schema:
                            type: string
                    '422':
                      description: Bad request
                      content:
                        application/json:
                          schema:
                            type: object
                            required:
                              - message
                            properties:
                              message:
                                type: string
        """.trimIndent()

        val feature = OpenApiSpecification.fromYAML(spec, "").toFeature()

        HttpStub(listOf(feature), specmaticConfigPath = "src/test/resources/specmatic_config_wtih_generate_stub.yaml").use { stub ->
            val request = HttpRequest("POST", path = "/hello", body = parsedJSONObject("""{"data": 10}"""))
            val response = stub.client.execute(request)

            assertThat(response.status).isEqualTo(422)
            assertThat(response.body).isInstanceOf(JSONObjectValue::class.java)

            val responseBody = response.body as JSONObjectValue
            assertThat(responseBody.jsonObject["message"]?.toStringLiteral()).contains("REQUEST.BODY.data")
        }
    }

    @Test
    fun `a generative stub should respond to an invalid request with a 422 with the error in the message key 2 levels deep`() {
        val spec = """
            openapi: 3.0.0
            info:
              title: Sample API
              description: Optional multiline or single-line description in [CommonMark](http://commonmark.org/help/) or HTML.
              version: 0.1.9
            servers:
              - url: http://api.example.com/v1
                description: Optional server description, e.g. Main (production) server
              - url: http://staging-api.example.com
                description: Optional server description, e.g. Internal staging server for testing
            paths:
              /hello:
                post:
                  summary: hello world
                  description: Optional extended description in CommonMark or HTML.
                  requestBody:
                    content:
                      application/json:
                        schema:
                          type: object
                          required:
                            - data
                          properties:
                            data:
                              type: string
                  responses:
                    '200':
                      description: Says hello
                      content:
                        text/plain:
                          schema:
                            type: string
                    '422':
                      description: Bad request
                      content:
                        application/json:
                          schema:
                            type: object
                            required:
                              - error_info
                            properties:
                              error_info:
                                type: object
                                required:
                                  - message
                                properties:
                                  message:
                                    type: string
        """.trimIndent()

        val feature = OpenApiSpecification.fromYAML(spec, "").toFeature()

        HttpStub(listOf(feature), specmaticConfigPath = "src/test/resources/specmatic_config_wtih_generate_stub.yaml").use { stub ->
            val request = HttpRequest("POST", path = "/hello", body = parsedJSONObject("""{"data": 10}"""))
            val response = stub.client.execute(request)

            assertThat(response.status).isEqualTo(422)
            assertThat(response.body).isInstanceOf(JSONObjectValue::class.java)

            val responseBody = response.body as JSONObjectValue
            assertThat(responseBody.findFirstChildByPath("error_info.message")?.toStringLiteral()).contains("REQUEST.BODY.data")
        }
    }

    @Test
    fun `a generative stub should return the error in any available string key in the 4xx response if message is not found`() {
        val spec = """
            openapi: 3.0.0
            info:
              title: Sample API
              description: Optional multiline or single-line description in [CommonMark](http://commonmark.org/help/) or HTML.
              version: 0.1.9
            servers:
              - url: http://api.example.com/v1
                description: Optional server description, e.g. Main (production) server
              - url: http://staging-api.example.com
                description: Optional server description, e.g. Internal staging server for testing
            paths:
              /hello:
                post:
                  summary: hello world
                  description: Optional extended description in CommonMark or HTML.
                  requestBody:
                    content:
                      application/json:
                        schema:
                          type: object
                          required:
                            - data
                          properties:
                            data:
                              type: string
                  responses:
                    '200':
                      description: Says hello
                      content:
                        text/plain:
                          schema:
                            type: string
                    '422':
                      description: Bad request
                      content:
                        application/json:
                          schema:
                            type: object
                            required:
                              - error_info
                            properties:
                              error_info:
                                type: string
        """.trimIndent()

        val feature = OpenApiSpecification.fromYAML(spec, "").toFeature()

        HttpStub(listOf(feature), specmaticConfigPath = "src/test/resources/specmatic_config_wtih_generate_stub.yaml").use { stub ->
            val request = HttpRequest("POST", path = "/hello", body = parsedJSONObject("""{"data": 10}"""))
            val response = stub.client.execute(request)

            assertThat(response.status).isEqualTo(422)
            assertThat(response.body).isInstanceOf(JSONObjectValue::class.java)

            val responseBody = response.body as JSONObjectValue
            assertThat(responseBody.jsonObject["error_info"]?.toStringLiteral()).contains("REQUEST.BODY.data")
        }
    }

    @Test
    fun `a generative stub should return a randomized 4xx response when it cannot find a string key`() {
        val spec = """
            openapi: 3.0.0
            info:
              title: Sample API
              description: Optional multiline or single-line description in [CommonMark](http://commonmark.org/help/) or HTML.
              version: 0.1.9
            servers:
              - url: http://api.example.com/v1
                description: Optional server description, e.g. Main (production) server
              - url: http://staging-api.example.com
                description: Optional server description, e.g. Internal staging server for testing
            paths:
              /hello:
                post:
                  summary: hello world
                  description: Optional extended description in CommonMark or HTML.
                  requestBody:
                    content:
                      application/json:
                        schema:
                          type: object
                          required:
                            - data
                          properties:
                            data:
                              type: string
                  responses:
                    '200':
                      description: Says hello
                      content:
                        text/plain:
                          schema:
                            type: string
                    '422':
                      description: Bad request
                      content:
                        application/json:
                          schema:
                            type: object
                            required:
                              - message
                            properties:
                              message:
                                type: integer
        """.trimIndent()

        val feature = OpenApiSpecification.fromYAML(spec, "").toFeature()

        HttpStub(listOf(feature), specmaticConfigPath = "src/test/resources/specmatic_config_wtih_generate_stub.yaml").use { stub ->
            val request = HttpRequest("POST", path = "/hello", body = parsedJSONObject("""{"data": 10}"""))
            val response = stub.client.execute(request)

            assertThat(response.status).isEqualTo(422)
            assertThat(response.body).isInstanceOf(JSONObjectValue::class.java)

            val responseBody = response.body as JSONObjectValue
            assertThat(responseBody.jsonObject["message"]).isInstanceOf(NumberValue::class.java)
        }
    }

    @Test
    fun `stub should load an example for a spec with pattern as a path param`() {
        createStubFromContracts(listOf(("src/test/resources/openapi/spec_with_path_param.yaml")), timeoutMillis = 0).use { stub ->
            val request = HttpRequest("GET", "/users/abc123", queryParametersMap = mapOf("item" to "10"))
            val response = stub.client.execute(request)

            assertThat(response.status).isEqualTo(200)
            val responseBody = response.body as JSONObjectValue
            assertThat(responseBody.findFirstChildByPath("id")).isEqualTo(NumberValue(10))
        }
    }

    @Test
    fun `stub should flag an error when a path param in an external example has an invalid type`() {
        val (output, _) = captureStandardOutput {
            val stub = createStubFromContracts(listOf(("src/test/resources/openapi/spec_with_invalid_path_param_example.yaml")), timeoutMillis = 0)
            stub.close()
        }

        assertThat(output).contains(">> REQUEST.PATH.userId")
    }

    @Test
    fun `should stub out a template with only templated request`() {
        createStubFromContracts(listOf(("src/test/resources/openapi/substitutions/spec_with_template_in_response_body.yaml")), timeoutMillis = 0).use { stub ->
            val request = HttpRequest(
                "POST",
                "/person",
                body = parsedJSONObject("""{"name": "Jodie", "department": "engineering"}""")
            )

            val response = stub.client.execute(request)

            assertThat(response.status).isEqualTo(200)
            val responseBody = response.body as JSONObjectValue
            assertThat(responseBody.findFirstChildByPath("location")?.toStringLiteral()).isEqualTo("Mumbai")
        }
    }

    @Test
    fun `example should favour concrete value over dictionary value`() {
        createStubFromContracts(listOf(("src/test/resources/openapi/substitutions/spec_with_dictionary_conflict.yaml")), timeoutMillis = 0).use { stub ->
            val request = HttpRequest(
                "POST",
                "/person",
                body = parsedJSONObject("""{"name": "Jodie"}""")
            )

            val response = stub.client.execute(request)

            assertThat(response.status).isEqualTo(200)
            val responseBody = response.body as JSONObjectValue
            assertThat(responseBody.findFirstChildByPath("id")).isEqualTo(NumberValue(20))
        }
    }

    @Test
    fun `should intercept the request and response as configured`() {
        createStubFromContracts(
            contractPaths = listOf("src/test/resources/openapi/hello.yaml"),
            dataDirPaths = emptyList(),
            timeoutMillis = 0
        ).use { stub ->

            stub.registerRequestInterceptor(object: RequestInterceptor {
                override fun interceptRequest(httpRequest: HttpRequest): HttpRequest {
                    val id = httpRequest.path?.split("/")?.last()?.toInt() ?: 0
                    val updatedPath = httpRequest.path?.split("/")?.map {
                        if(it == "$id") return@map "${id * 10}"
                        it
                    }?.joinToString("/") ?: ""
                    return httpRequest.updatePath(updatedPath)
                }
            })

            stub.registerResponseInterceptor(object: ResponseInterceptor {
                override fun interceptResponse(
                    httpRequest: HttpRequest,
                    httpResponse: HttpResponse
                ): HttpResponse {
                    val id = httpRequest.path?.split("/")?.last()?.toInt() ?: 0
                    return httpResponse.copy(body = StringValue("This is a response for id : $id"))
                }
            })

            val request = HttpRequest(
                "GET",
                "/hello/10",
                headers = mapOf("Authorization" to "Bearer token")
            )

            val response = stub.client.execute(request).body.toStringLiteral()

            assertThat(response).isEqualTo("This is a response for id : 100")
        }
    }

    @Test
    fun `should return the first non-null response returned by a request handler in a list of request handlers`() {
        createStubFromContracts(
            contractPaths = listOf("src/test/resources/openapi/hello.yaml"),
            dataDirPaths = emptyList(),
            timeoutMillis = 0
        ).use { stub ->

            stub.registerHandler(object: RequestHandler {
                override val name: String
                    get() = "POST request handler"

                override fun handleRequest(httpRequest: HttpRequest): HttpStubResponse? {
                    if(httpRequest.method != "POST") return null
                    return HttpStubResponse(
                        response = HttpResponse(body = "POST response", status = 200)
                    )
                }
            })
            stub.registerHandler(object: RequestHandler {
                override val name: String
                    get() = "GET request handler"

                override fun handleRequest(httpRequest: HttpRequest): HttpStubResponse? {
                    if(httpRequest.method != "GET") return null
                    return HttpStubResponse(
                        response = HttpResponse(body = "GET response", status = 200)
                    )
                }

            })

            val request = HttpRequest(
                "GET",
                "/hello/10",
                headers = mapOf("Authorization" to "Bearer token")
            )

            val response = stub.client.execute(request).body.toStringLiteral()

            assertThat(response).isEqualTo("GET response")
        }
    }

    @Test
    fun `should use the 400 response code based externalised example and respond accordingly`() {
        createStubFromContracts(
            contractPaths = listOf("src/test/resources/openapi/has_400_example_for_stub.yaml"),
            dataDirPaths = listOf("src/test/resources/openapi/has_400_example_for_stub_examples")
        ).use { stub ->
            val request = HttpRequest(
                "GET",
                "/items",
                queryParametersMap = mapOf(
                    "optional-param" to "optional"
                )
            )
            val response = (stub.client.execute(request).body as JSONObjectValue).jsonObject

            assertThat(response["message"]?.toStringLiteral()).isEqualTo("required-param is missing. Please provide.")
        }
    }

    @ParameterizedTest
    @CsvSource(
        "Expected, Actual, Status",
        "abc123, abc123, 204",
        "abc123, pqrxyz, 400",
        useHeadersInDisplayName = true
    )
    fun `should be able to stub out a 204 with no response headers in strict mode when an example is provided`(expectedFieldValue: String, fieldValue: String, status: Int) {
        val feature = OpenApiSpecification.fromYAML(
            """
            openapi: 3.0.3
            info:
              title: Simple API
              version: 1.0.0
            paths:
              /:
                post:
                  summary: Simple POST endpoint
                  requestBody:
                    content:
                      application/json:
                        schema:
                          type: object
                          required:
                            - field
                          properties:
                            field:
                              type: string
                        examples:
                          SUCCESS:
                            value:
                              field: "$expectedFieldValue"
                  responses:
                    '204':
                      description: A simple string response
            """.trimIndent(), ""
        ).toFeature()

        HttpStub(listOf(feature), strictMode = true).use { stub ->
            val request = HttpRequest("POST", "/", body = parsedJSONObject("""{"field": "$fieldValue"}"""))
            val response = stub.client.execute(request)

            assertThat(response.status).isEqualTo(status)
        }
    }

    @ParameterizedTest
    @CsvSource(
        "Field value, Expected Status",
        "abc123, 204",
        "xyz789, 500",
        "pqr123, 400",
        useHeadersInDisplayName = true
    )
    fun `should be able to stub out a 204 with no headers in the presence of a 500 in strict mode`(fieldValue: String, expectedStatus: Int) {
        val feature: Feature = OpenApiSpecification.fromYAML(
            """
            openapi: 3.0.3
            info:
              title: Simple API
              version: 1.0.0
            paths:
              /:
                post:
                  summary: Simple POST endpoint
                  requestBody:
                    content:
                      application/json:
                        schema:
                          type: object
                          required:
                            - field
                          properties:
                            field:
                              type: string
                        examples:
                          SUCCESS:
                            value:
                              field: "abc123"
                          FAILED:
                            value:
                              field: "xyz789"
                  responses:
                    '204':
                      description: A simple string response
                    '500':
                      description: An internal server error occurred
                      content:
                        text/plain:
                          schema:
                            type: string
                          examples:
                            FAILED:
                              value: "Internal server error"
            """.trimIndent(), ""
        ).toFeature()

        HttpStub(listOf(feature), strictMode = true).use { stub ->
            val request = HttpRequest("POST", "/", body = parsedJSONObject("""{"field": "$fieldValue"}"""))
            val response = stub.client.execute(request)

            assertThat(response.status).isEqualTo(expectedStatus)
        }
    }

    @Test
    fun `should serve requests from multiple ports as configured in specmatic config`() {
        val specmaticConfigFile = File("src/test/resources/multi_port_stub/specmatic.yaml")
        val specmaticConfig = loadSpecmaticConfig(specmaticConfigFile.absolutePath)

        val scenarioStubs = specmaticConfig.stubContracts(specmaticConfigFile).map { specPath ->
            OpenApiSpecification.fromFile(specPath).toFeature() to loadContractStubsFromImplicitPaths(
                contractPathDataList = listOf(ContractPathData("", specPath)),
                specmaticConfig = specmaticConfig
            ).flatMap { it.second }
        }

        HttpStub(
            features = specmaticConfig.stubContracts(specmaticConfigFile).map {
                OpenApiSpecification.fromFile(it).toFeature()
            },
            rawHttpStubs = contractInfoToHttpExpectations(scenarioStubs),
            specmaticConfigPath = specmaticConfigFile.canonicalPath
        ).use { stub ->
            val request = HttpRequest(
                method = "POST",
                path = "/products",
                body = parsedJSONObject("""{"name": "Xiaomi", "category": "Mobile"}""")
            )
            val importedProductResponse = HttpClient(
                endPointFromHostAndPort("localhost", 9000, null)
            ).execute(request)
            assertThat(
                (importedProductResponse.body as JSONObjectValue).findFirstChildByPath("id")?.toStringLiteral()
            ).isEqualTo("100")


            val exportedProductResponse = HttpClient(
                endPointFromHostAndPort("localhost", 9001, null)
            ).execute(request)
            assertThat(
                (exportedProductResponse.body as JSONObjectValue).findFirstChildByPath("id")?.toStringLiteral()
            ).isEqualTo("200")


            val anotherExportedProductResponse = HttpClient(
                endPointFromHostAndPort("localhost", 9002, null)
            ).execute(request)
            assertThat(
                (anotherExportedProductResponse.body as JSONObjectValue).findFirstChildByPath("id")?.toStringLiteral()
            ).isEqualTo("300")
        }
    }
}
