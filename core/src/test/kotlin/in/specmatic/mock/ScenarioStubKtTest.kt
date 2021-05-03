package `in`.specmatic.mock

import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.junit.jupiter.api.Test
import `in`.specmatic.core.*
import `in`.specmatic.core.Result.Success
import `in`.specmatic.core.pattern.ContractException
import `in`.specmatic.core.pattern.parsedValue
import `in`.specmatic.core.utilities.jsonStringToValueMap
import `in`.specmatic.core.value.StringValue
import `in`.specmatic.shouldMatch

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
        assertThat(part.filename).isEqualTo("employees.csv")
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
        assertThat(pattern.multiPartFormDataPattern.single().matches(MultiPartFileValue("employees", "employees.csv", "text/csv", "gzip"), Resolver())).isInstanceOf(Success::class.java)
    }

    @Test
    fun `request-response with json body in request to gherkin string`() {
        val request = HttpRequest(method = "POST", path = "/customer", headers = emptyMap(), body = parsedValue("""{"name": "John Doe", "address": {"street": "High Street", "city": "Manchester"}}"""), queryParams = emptyMap(), formFields = emptyMap(), multiPartFormData = emptyList())
        val response = HttpResponse(status = 200, body = parsedValue("""{"id": 10}"""))

        validateStubAndQontract(request, response, """Feature: New Feature
  Scenario: New scenario
    Given type Address
      | street | (string) |
      | city | (string) |
    And type RequestBody
      | name | (string) |
      | address | (Address) |
    And type ResponseBody
      | id | (number) |
    When POST /customer
    And request-body (RequestBody)
    Then status 200
    And response-body (ResponseBody)
  
    Examples:
    | name | street | city |
    | John Doe | High Street | Manchester |""")
    }

    @Test
    fun `request-response with headers to gherkin string`() {
        val request = HttpRequest(method = "POST", path="/customer", headers = mapOf("X-Header1" to "value 1", "X-Header2" to "value 2"), body = parsedValue("""{"name": "John Doe", "address": {"street": "High Street", "city": "Manchester"}}"""), queryParams = emptyMap(), formFields = emptyMap(), multiPartFormData = emptyList())
        val response = HttpResponse(status = 200, headers = mapOf("X-Required" to "this is a must", "X-Extra" to "something more"), body = parsedValue("""{"id": 10}"""))

        validateStubAndQontract(request, response, """Feature: New Feature
  Scenario: New scenario
    Given type Address
      | street | (string) |
      | city | (string) |
    And type RequestBody
      | name | (string) |
      | address | (Address) |
    And type ResponseBody
      | id | (number) |
    When POST /customer
    And request-header X-Header1 (string)
    And request-header X-Header2 (string)
    And request-body (RequestBody)
    Then status 200
    And response-header X-Required (string)
    And response-header X-Extra (string)
    And response-body (ResponseBody)
  
    Examples:
    | X-Header1 | X-Header2 | name | street | city |
    | value 1 | value 2 | John Doe | High Street | Manchester |""")
    }

    @Test
    fun `request-response with form fields to gherkin string`() {
        val request = HttpRequest(method = "POST", path = "/customer", headers = emptyMap(), formFields = mapOf("X-FormData1" to "some value"), multiPartFormData = emptyList())
        val response = HttpResponse(status = 200, body = parsedValue("""{"id": 10}"""))

        validateStubAndQontract(request, response, """Feature: New Feature
  Scenario: New scenario
    Given type ResponseBody
      | id | (number) |
    When POST /customer
    And form-field X-FormData1 (string)
    Then status 200
    And response-body (ResponseBody)
  
    Examples:
    | X-FormData1 |
    | some value |""")
    }

    @Test
    fun `request-response with multipart form data content to gherkin string`() {
        val request = HttpRequest(method = "POST", path = "/customer", headers = emptyMap(), formFields = emptyMap(), multiPartFormData = listOf(MultiPartContentValue("name", StringValue("John Doe"))))
        val response = HttpResponse(status = 200, body = parsedValue("""{"id": 10}"""))

        validateStubAndQontract(request, response, """Feature: New Feature
  Scenario: New scenario
    Given type ResponseBody
      | id | (number) |
    When POST /customer
    And request-part name (string)
    Then status 200
    And response-body (ResponseBody)
  
    Examples:
    | name |
    | John Doe |""")
    }

    @Test
    fun `request-response with multipart form data file to gherkin string`() {
        val request = HttpRequest(method = "POST", path = "/customer", headers = emptyMap(), formFields = emptyMap(), multiPartFormData = listOf(MultiPartFileValue("customer_csv", "customer.csv", "text/csv", "identity")))
        val response = HttpResponse(status = 200, body = parsedValue("""{"id": 10}"""))

        validateStubAndQontract(request, response, """Feature: New Feature
  Scenario: New scenario
    Given type ResponseBody
      | id | (number) |
    When POST /customer
    And request-part customer_csv @(string) text/csv identity
    Then status 200
    And response-body (ResponseBody)
  
    Examples:
    | customer_csv_filename |
    | customer.csv |""")
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
        validateStubAndQontract(mock.request, mock.response, """Feature: New Feature
  Scenario: New scenario
    When POST /square
    And request-part employees @(string) text/csv gzip
    Then status 200
    And response-body (number)
  
    Examples:
    | employees_filename |
    | employees.csv |"""
        )
    }

    @Test
    fun `converts array in request body to gherkin`() {
        val mockText = """
{
  "http-request": {
    "method": "POST",
    "path": "/square",
    "body": [ "one", "two", "three" ]
  },

  "http-response": {
    "status": 200,
    "body": 100
  }
}
        """.trim()

        val mock = mockFromJSON(jsonStringToValueMap((mockText)))
        validateStubAndQontract(mock.request, mock.response, """Feature: New Feature
  Scenario: New scenario
    When POST /square
    And request-body (string*)
    Then status 200
    And response-body (number)""")
    }

    @Test
    fun `converts array of identically structured json objects in request body to gherkin`() {
        val mockText = """
{
  "http-request": {
    "method": "POST",
    "path": "/square",
    "body": [
      {
        "name": "John Doe"
      },
      {
        "name": "John Doe"
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
        validateStubAndQontract(mock.request, mock.response, """Feature: New Feature
  Scenario: New scenario
    Given type RequestBody
      | name | (string) |
    When POST /square
    And request-body (RequestBody*)
    Then status 200
    And response-body (number)
  
    Examples:
    | name |
    | John Doe |""")
    }

    @Test
    fun `converts array of json objects in the request body where the first contains a key not in the other to gherkin`() {
        val mockText = """
{
  "http-request": {
    "method": "POST",
    "path": "/square",
    "body": [
      {
        "name": "John Doe",
        "address": "High Street"
      },
      {
        "name": "John Doe"
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
        validateStubAndQontract(mock.request, mock.response, """Feature: New Feature
  Scenario: New scenario
    Given type RequestBody
      | name | (string) |
      | address? | (string) |
    When POST /square
    And request-body (RequestBody*)
    Then status 200
    And response-body (number)
  
    Examples:
    | name | address |
    | John Doe | High Street |""")
    }

    @Test
    fun `converts array of json objects in the request body where the second contains a key not in the other to gherkin`() {
        val mockText = """
{
  "http-request": {
    "method": "POST",
    "path": "/square",
    "body": [
      {
        "name": "John Doe"
      },
      {
        "name": "John Doe",
        "address": "High Street"
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
        validateStubAndQontract(mock.request, mock.response, """Feature: New Feature
  Scenario: New scenario
    Given type RequestBody
      | name | (string) |
      | address? | (string) |
    When POST /square
    And request-body (RequestBody*)
    Then status 200
    And response-body (number)
  
    Examples:
    | name |
    | John Doe |""")
    }

    @Test
    fun `converts array of 2 json objects in the request body where a value in the second is null to gherkin`() {
        val mockText = """
{
  "http-request": {
    "method": "POST",
    "path": "/square",
    "body": [
      {
        "name": "John Doe",
        "address": "High Street"
      },
      {
        "name": null
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
        validateStubAndQontract(mock.request, mock.response, """Feature: New Feature
  Scenario: New scenario
    Given type RequestBody
      | name | (string?) |
      | address? | (string) |
    When POST /square
    And request-body (RequestBody*)
    Then status 200
    And response-body (number)
  
    Examples:
    | name | address |
    | John Doe | High Street |""")
    }

    @Test
    fun `converts array of 3 json objects in the request body where a value in the first is null to gherkin`() {
        val mockText = """
{
  "http-request": {
    "method": "POST",
    "path": "/square",
    "body": [
      {
        "name": null
      },
      {
        "name": "John Doe",
        "address": null
      },
      {
        "name": "John Doe",
        "address": "High Street"
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
        validateStubAndQontract(mock.request, mock.response, """Feature: New Feature
  Scenario: New scenario
    Given type RequestBody
      | name | (string?) |
      | address? | (string?) |
    When POST /square
    And request-body (RequestBody*)
    Then status 200
    And response-body (number)
  
    Examples:
    | name |
    | (null) |""")
    }

    @Test
    fun `converts array of 3 json objects in the request body where a value in the first and second is null to gherkin`() {
        val mockText = """
{
  "http-request": {
    "method": "POST",
    "path": "/square",
    "body": [
      {
        "name": null
      },
      {
        "name": null,
        "address": null
      },
      {
        "name": "John Doe",
        "address": "High Street"
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
        validateStubAndQontract(mock.request, mock.response, """Feature: New Feature
  Scenario: New scenario
    Given type RequestBody
      | name | (string?) |
      | address? | (string?) |
    When POST /square
    And request-body (RequestBody*)
    Then status 200
    And response-body (number)
  
    Examples:
    | name |
    | (null) |""")
    }
    @Test
    fun `converts array of 3 json objects in the request body where a value in the first and third is null to gherkin`() {
        val mockText = """
{
  "http-request": {
    "method": "POST",
    "path": "/square",
    "body": [
      {
        "name": null
      },
      {
        "name": "John Doe"
      },
      {
        "name": null
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
        validateStubAndQontract(mock.request, mock.response, """Feature: New Feature
  Scenario: New scenario
    Given type RequestBody
      | name | (string?) |
    When POST /square
    And request-body (RequestBody*)
    Then status 200
    And response-body (number)
  
    Examples:
    | name |
    | (null) |""")
    }

    @Test
    fun `converts array of json objects in the request body where the keys are identical but a value in the second is to gherkin`() {
        val mockText = """
{
  "http-request": {
    "method": "POST",
    "path": "/square",
    "body": [
      {
        "name": "John Doe"
      },
      {
        "name": null,
        "address": "High Street"
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
        validateStubAndQontract(mock.request, mock.response, """Feature: New Feature
  Scenario: New scenario
    Given type RequestBody
      | name | (string?) |
      | address? | (string) |
    When POST /square
    And request-body (RequestBody*)
    Then status 200
    And response-body (number)
  
    Examples:
    | name |
    | John Doe |""")
    }

    @Test
    fun `empty array in request body`() {
        val stubText = """
{
  "http-request": {
    "method": "POST",
    "path": "/square",
    "body": []
  },

  "http-response": {
    "status": 200,
    "body": 100
  }
}
        """.trim()

        val mock = mockFromJSON(jsonStringToValueMap(stubText))
        validateStubAndQontract(mock.request, mock.response, """Feature: New Feature
  Scenario: New scenario
    When POST /square
    And request-body []
    Then status 200
    And response-body (number)""")
    }

    @Test
    fun `null in request body throws an exception`() {
        val stubText = """
{
  "http-request": {
    "method": "POST",
    "path": "/square",
    "body": null
  },

  "http-response": {
    "status": 200
  }
}
        """.trim()

        assertThatThrownBy { mockFromJSON(jsonStringToValueMap(stubText)) }.isInstanceOf(ContractException::class.java)
    }

    @Test
    fun `null in response body throws an exception`() {
        val stubText = """
{
  "http-request": {
    "method": "POST",
    "path": "/square"
  },

  "http-response": {
    "status": 200,
    "body": null
  }
}
        """.trim()

        assertThatThrownBy { mockFromJSON(jsonStringToValueMap(stubText)) }.isInstanceOf(ContractException::class.java)
    }

    @Test
    fun `load delay from stub info`() {
        val stubText = """
{
  "http-request": {
    "method": "POST",
    "path": "/square"
  },

  "http-response": {
    "status": 200
  },
  
  "$DELAY_IN_SECONDS": 10
}
        """.trim()

        val scenarioStub = mockFromJSON(jsonStringToValueMap(stubText))
        assertThat(scenarioStub.delayInSeconds).isEqualTo(10)
    }

    @Test
    fun `stub with no delay should result in empty delay value in loaded stub`() {
        val stubText = """
{
  "http-request": {
    "method": "POST",
    "path": "/square"
  },

  "http-response": {
    "status": 200
  }
}
        """.trim()

        val scenarioStub = mockFromJSON(jsonStringToValueMap(stubText))
        assertThat(scenarioStub.delayInSeconds).isNull()
    }
}

fun validateStubAndQontract(request: HttpRequest, response: HttpResponse, expectedGherkin: String? = null) {
    try {
        val cleanedUpResponse = dropContentAndCORSResponseHeaders(response)
        val gherkin = toGherkinFeature(NamedStub("New scenario", ScenarioStub(request, cleanedUpResponse))).also { println(it) }

        if(expectedGherkin != null) {
            assertThat(gherkin.trim()).isEqualTo(expectedGherkin.trim())
        } else {
            println(gherkin)
        }

        val behaviour = parseGherkinStringToFeature(gherkin)
        behaviour.matchingStub(request, cleanedUpResponse)
    } catch (e: Throwable) {
        println(e.localizedMessage)
        throw e
    }
}
