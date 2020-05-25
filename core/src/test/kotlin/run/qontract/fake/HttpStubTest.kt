package run.qontract.fake

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.fail
import org.springframework.http.HttpHeaders
import org.springframework.http.HttpMethod
import org.springframework.http.MediaType
import org.springframework.http.RequestEntity
import org.springframework.web.client.HttpClientErrorException
import org.springframework.web.client.RestTemplate
import org.springframework.web.client.getForEntity
import org.springframework.web.client.postForEntity
import run.qontract.core.HttpRequest
import run.qontract.core.HttpResponse
import run.qontract.core.pattern.parsedValue
import run.qontract.core.value.NumberValue
import run.qontract.core.value.StringValue
import run.qontract.mock.MockScenario
import java.net.URI

internal class HttpStubTest {
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

        HttpStub(gherkin, listOf(MockScenario(request, response))).use { fake ->
            val postResponse = RestTemplate().postForEntity<String>(fake.endPoint + "/number", "10")
            assertThat(postResponse.body).isEqualTo("100")
        }
    }

    @Test
    fun `should accept mocks over http`() {
        val gherkin = """
Feature: Math API

Scenario: Get a number
  When GET /number
  Then status 200
  And response-body (number)
""".trim()

        HttpStub(gherkin).use { fake ->
            val mockData = """{"mock-http-request": {"method": "GET", "path": "/number"}, "mock-http-response": {"status": 200, "body": 10}}"""
            val stubSetupURL = "${fake.endPoint}/_stub_setup"
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
    fun `it should accept (datetime) as a value in the request, and match datetime values against that type`() {
        val gherkin = """Feature: Calendar
Scenario: Accept a date
When POST /date
And request-body (datetime)
Then status 200
And response-body (string)
        """.trim()

        val request = HttpRequest("POST", "/date", emptyMap(), StringValue("(datetime)"))
        val mock = MockScenario(request, HttpResponse(200, "done"))

        HttpStub(gherkin, listOf(mock)).use { fake ->
            val postResponse = RestTemplate().postForEntity<String>(fake.endPoint + "/date", "2020-04-12T00:00:00")
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
        val mock = MockScenario(request, HttpResponse(200, "done"))

        HttpStub(gherkin, listOf(mock)).use { fake ->
            val postResponse = RestTemplate().postForEntity<String>(fake.endPoint + "/date", """{"date": "2020-04-12T00:00:00"}""")
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
        val mock = MockScenario(request, HttpResponse(200, "done"))

        try {
            HttpStub(gherkin, listOf(mock)).use { fake ->
                val postResponse = RestTemplate().postForEntity<String>(fake.endPoint + "/date", """2020-04-12T00:00:00""")
                assertThat(postResponse.statusCode.value()).isEqualTo(200)
                assertThat(postResponse.body).isEqualTo("done")
            }
        } catch(e: HttpClientErrorException) {
            return
        }

        fail("Should have thrown an exception")
    }
}
