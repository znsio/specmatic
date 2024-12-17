package integration_tests

import io.specmatic.conversions.OpenApiSpecification
import io.specmatic.core.HttpRequest
import io.specmatic.core.HttpResponse
import io.specmatic.core.pattern.parsedJSONObject
import io.specmatic.test.TestExecutor
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test

class LinksTest {
    @Test
    fun `a spec with links should be transformed to internal workflows`() {
        val feature = OpenApiSpecification
            .fromFile("src/test/resources/openapi/spec_for_links_crud_test.yaml")
            .toFeature()

        val testExecutor = TestExecutorWithWorkflowValidation()

        feature.executeTests(testExecutor)

        testExecutor.verifyWorkflow()
    }

    class TestExecutorWithWorkflowValidation : TestExecutor {
        private val receivedIds = mutableListOf<String>()
        private val validResponse = """{id: "new-order-id", name: "some-product", price: 123}"""

        override fun execute(request: HttpRequest): HttpResponse {
            return when (request.method) {
                "POST" -> {
                    receivedIds.add("new-order-id")
                    HttpResponse(201, parsedJSONObject("""{id: "new-order-id"}"""))
                }

                "GET", "PUT", "DELETE" -> {
                    val id = request.path?.split("/")?.last()
                    receivedIds.add(id!!)
                    HttpResponse(if (request.method == "DELETE") 204 else 200, parsedJSONObject(validResponse))
                }

                else -> throw IllegalArgumentException("Unsupported method ${request.method}")
            }
        }

        fun verifyWorkflow() {
            assertThat(receivedIds).containsExactly(
                "new-order-id", // POST creates the ID
                "new-order-id", // GET verifies it
                "new-order-id", // PUT modifies it
                "new-order-id"  // DELETE removes it
            )
        }
    }


}