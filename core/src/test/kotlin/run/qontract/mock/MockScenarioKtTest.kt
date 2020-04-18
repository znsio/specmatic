package run.qontract.mock

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test
import run.qontract.core.utilities.jsonStringToValueMap

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
}