import org.junit.jupiter.api.Test
import org.springframework.web.client.RestTemplate
import org.springframework.web.client.getForEntity
import run.qontract.stub.HttpStub
import org.assertj.core.api.Assertions.assertThat

class SpringCompatibilityTest {
    @Test
    fun twoPointThree() {
        val simpleContract = """
Feature: Simple
  Scenario: Simpler
    When GET /
    Then status 200
    And response-body success
""".trimIndent()

        HttpStub(simpleContract).use {
            val client = RestTemplate()
            val result = client.getForEntity<String>("http://localhost:9000/")
            assertThat(result.body).isEqualTo("success")
        }
    }
}
