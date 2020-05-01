package run.qontract.mock

import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.fail
import run.qontract.core.ContractBehaviour
import run.qontract.core.Resolver
import run.qontract.core.Result
import run.qontract.core.pattern.ContractException
import run.qontract.core.pattern.parsedValue
import run.qontract.core.shouldMatch
import run.qontract.core.shouldNotMatch
import run.qontract.core.utilities.jsonStringToValueMap
import run.qontract.core.value.JSONObjectValue
import run.qontract.core.value.NumberValue
import run.qontract.core.value.StringValue

internal class MockScenarioKtTest {
    @Test
    fun `conversion of json string to mock should load form fields`() {
        val mockString = """
{
    "mock-http-request": {
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
  "mock-http-request": {
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
}