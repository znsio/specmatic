package run.qontract.mock

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import run.qontract.core.*
import run.qontract.core.Result.Success
import run.qontract.core.pattern.*
import run.qontract.core.utilities.jsonStringToValueMap
import run.qontract.core.value.StringValue

internal class ScenarioStubKtTest {
    @Test
    fun `conversion of json string to mock should load form fields`() {
        val mockString = """
{
    "http-request": {
        "method": "POST",
        "form-fields": {
            "Data": "10"
        }
    },
    
    "mock-http-response": {
        "status": 200
    }
}
""".trimIndent()

        val mockData = jsonStringToValueMap(mockString)
        val mockScenario = mockFromJSON(mockData)

        assertThat(mockScenario.request.formFields.getValue("Data")).isEqualTo("10")
    }

    @Test
    fun `nullable number in string should load from a mock`() {
        val mockText = """
{
  "http-request": {
    "method": "POST",
    "path": "/square",
    "body": {
      "number": 10,
      "description": "(number in string?)"
    }
  },

  "mock-http-response": {
    "status": 200,
    "body": 100
  }
}
        """.trim()

        val mock = mockFromJSON(jsonStringToValueMap((mockText)))
        val pattern = mock.request.toPattern()

        parsedValue("""{"number": 10, "description": "10"}""") shouldMatch pattern.body
        parsedValue("""{"number": 10, "description": null}""") shouldMatch pattern.body

        assertThat(pattern.body.matches(parsedValue("""{"number": 10, "description": "test"}"""), Resolver())).isInstanceOf(Result.Failure::class.java)
    }

    @Test
    fun `should deserialize multipart content form data mock`() {
        val mockText = """
{
  "http-request": {
    "method": "POST",
    "path": "/square",
    "multipart-formdata": [
      {
        "name": "employeeid",
        "content": "10"
      }
    ]
  },

  "mock-http-response": {
    "status": 200,
    "body": 100
  }
}
        """.trim()

        val mock = mockFromJSON(jsonStringToValueMap((mockText)))
        assertThat(mock.request.multiPartFormData).hasSize(1)
        assertThat(mock.request.multiPartFormData.first()).isInstanceOf(MultiPartContentValue::class.java)
        assertThat(mock.request.multiPartFormData.first().name).isEqualTo("employeeid")

        val part = mock.request.multiPartFormData.first() as MultiPartContentValue
        assertThat(part.content.toStringValue()).isEqualTo("10")
    }

    @Test
    fun `should deserialize multipart file form data mock`() {
        val mockText = """
{
  "http-request": {
    "method": "POST",
    "path": "/square",
    "multipart-formdata": [
      {
        "name": "employees",
        "filename": "@employees.csv",
        "contentType": "text/csv",
        "contentEncoding": "gzip"
      }
    ]
  },

  "mock-http-response": {
    "status": 200,
    "body": 100
  }
}
        """.trim()

        val mock = mockFromJSON(jsonStringToValueMap((mockText)))
        assertThat(mock.request.multiPartFormData).hasSize(1)
        assertThat(mock.request.multiPartFormData.first()).isInstanceOf(MultiPartFileValue::class.java)
        assertThat(mock.request.multiPartFormData.first().name).isEqualTo("employees")

        val part = mock.request.multiPartFormData.first() as MultiPartFileValue
        assertThat(part.filename).isEqualTo("@employees.csv")
        assertThat(part.contentType).isEqualTo("text/csv")
        assertThat(part.contentEncoding).isEqualTo("gzip")
    }

    @Test
    fun `should generate request pattern containing multipart content from mock data`() {
        val mockText = """
{
  "http-request": {
    "method": "POST",
    "path": "/square",
    "multipart-formdata": [
      {
        "name": "employeeid",
        "content": "10"
      }
    ]
  },

  "mock-http-response": {
    "status": 200,
    "body": 100
  }
}
        """.trim()

        val mock = mockFromJSON(jsonStringToValueMap((mockText)))
        val pattern = mock.request.toPattern()
        assertThat(pattern.multiPartFormDataPattern).hasSize(1)
        assertThat(pattern.multiPartFormDataPattern.single().matches(MultiPartContentValue("employeeid", StringValue("10")), Resolver())).isInstanceOf(Success::class.java)
    }

    @Test
    fun `should load a kafka message from stub info`() {
        val mockText = """
{
  "kafka-message": {
    "topic": "the weather",
    "key": "status",
    "value": "cloudy"
  }
}
        """.trim()

        val mock = mockFromJSON(jsonStringToValueMap((mockText)))
        assertThat(mock.kafkaMessage).isNotNull
        assertThat(mock.kafkaMessage?.topic).isEqualTo("the weather")
        assertThat(mock.kafkaMessage?.key).isEqualTo(StringValue("status"))
        assertThat(mock.kafkaMessage?.value).isEqualTo(StringValue("cloudy"))
    }

    @Test
    fun `should generate request pattern containing multipart file from mock data`() {
        val mockText = """
{
  "http-request": {
    "method": "POST",
    "path": "/square",
    "multipart-formdata": [
      {
        "name": "employees",
        "filename": "@employees.csv",
        "contentType": "text/csv",
        "contentEncoding": "gzip"
      }
    ]
  },

  "mock-http-response": {
    "status": 200,
    "body": 100
  }
}
        """.trim()

        val mock = mockFromJSON(jsonStringToValueMap((mockText)))
        val pattern = mock.request.toPattern()
        assertThat(pattern.multiPartFormDataPattern).hasSize(1)
        assertThat(pattern.multiPartFormDataPattern.single().matches(MultiPartFileValue("employees", "@employees.csv", "text/csv", "gzip"), Resolver())).isInstanceOf(Success::class.java)
    }

    @Test
    fun `request-response with json body in request to gherkin string`() {
        val request = HttpRequest(method = "POST", path = "/customer", headers = emptyMap(), body = parsedValue("""{"name": "John Doe", "address": {"street": "High Street", "city": "Manchester"}}"""), queryParams = emptyMap(), formFields = emptyMap(), multiPartFormData = emptyList())
        val response = HttpResponse(status = 200, body = parsedValue("""{"id": 10}"""))

        convertAndTest(request, response)
    }

    @Test
    fun `request-response with headers to gherkin string`() {
        val request = HttpRequest(method = "POST", path="/customer", headers = mapOf("X-Header1" to "value 1", "X-Header2" to "value 2"), body = parsedValue("""{"name": "John Doe", "address": {"street": "High Street", "city": "Manchester"}}"""), queryParams = emptyMap(), formFields = emptyMap(), multiPartFormData = emptyList())
        val response = HttpResponse(status = 200, headers = mapOf("X-Required" to "this is a must", "X-Extra" to "something more"), body = parsedValue("""{"id": 10}"""))

        convertAndTest(request, response)
    }

    @Test
    fun `request-response with form fields to gherkin string`() {
        val request = HttpRequest(method = "POST", path = "/customer", headers = emptyMap(), formFields = mapOf("X-FormData1" to "some value", "X-FormData1" to "some value"), multiPartFormData = emptyList())
        val response = HttpResponse(status = 200, body = parsedValue("""{"id": 10}"""))

        convertAndTest(request, response)
    }

    @Test
    fun `request-response with multipart form data content to gherkin string`() {
        val request = HttpRequest(method = "POST", path = "/customer", headers = emptyMap(), formFields = emptyMap(), multiPartFormData = listOf(MultiPartContentValue("name", StringValue("John Doe"))))
        val response = HttpResponse(status = 200, body = parsedValue("""{"id": 10}"""))

        convertAndTest(request, response)
    }

    @Test
    fun `request-response with multipart form data file to gherkin string`() {
        val request = HttpRequest(method = "POST", path = "/customer", headers = emptyMap(), formFields = emptyMap(), multiPartFormData = listOf(MultiPartFileValue("customer_csv", "@customer.csv", "text/csv", "identity")))
        val response = HttpResponse(status = 200, body = parsedValue("""{"id": 10}"""))

        convertAndTest(request, response)
    }

    @Test
    fun `converts mock json to gherkin`() {
        val mockText = """
{
  "http-request": {
    "method": "POST",
    "path": "/square",
    "multipart-formdata": [
      {
        "name": "employees",
        "filename": "@employees.csv",
        "contentType": "text/csv",
        "contentEncoding": "gzip"
      }
    ]
  },

  "http-response": {
    "status": 200,
    "body": 100
  }
}
        """.trim()

        val mock = mockFromJSON(jsonStringToValueMap((mockText)))
        convertAndTest(mock.request, mock.response)
    }
}

fun convertAndTest(request: HttpRequest, response: HttpResponse) {
    try {
        val cleanedUpResponse = dropContentAndCORSResponseHeaders(response)
        val behaviour = toBehaviour(request, cleanedUpResponse)
        behaviour.matchingMockResponse(request, cleanedUpResponse)
    } catch (e: Throwable) {
        println(e.localizedMessage)
        throw e
    }
}

fun toBehaviour(request: HttpRequest, response: HttpResponse): ContractBehaviour =
        ContractBehaviour(toGherkinFeature(NamedStub("New scenario", ScenarioStub(request, response))).also { println(it) })
