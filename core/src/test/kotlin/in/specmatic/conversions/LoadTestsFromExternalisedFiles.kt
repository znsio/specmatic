package `in`.specmatic.conversions

import `in`.specmatic.core.HttpRequest
import `in`.specmatic.core.HttpResponse
import `in`.specmatic.core.pattern.ContractException
import `in`.specmatic.core.pattern.parsedJSONArray
import `in`.specmatic.core.pattern.parsedJSONObject
import `in`.specmatic.core.value.Value
import `in`.specmatic.test.TestExecutor
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.junit.jupiter.api.Test
import java.util.function.Consumer

class LoadTestsFromExternalisedFiles {
    @Test
    fun `should load and execute externalized tests from _tests directory`() {
        val feature = OpenApiSpecification.fromFile("src/test/resources/openapi/has_externalized_test_and_no_example.yaml").toFeature()

        val results = feature.executeTests(object : TestExecutor {
            override fun execute(request: HttpRequest): HttpResponse {
                assertThat(request.path).isEqualTo("/order_action_figure")
                assertThat(request.method).isEqualTo("POST")
                assertThat(request.body).isEqualTo(parsedJSONObject("""{"name": "Master Yoda", "description": "Head of the Jedi Council"}"""))

                return HttpResponse.OK(parsedJSONObject("""{"id": 1}"""))
            }

            override fun setServerState(serverState: Map<String, Value>) {
            }
        })

        assertThat(results.successCount).isEqualTo(1)
        assertThat(results.failureCount).isEqualTo(0)
    }

    @Test
    fun `externalized tests should replace example tests`() {
        val feature = OpenApiSpecification.fromFile("src/test/resources/openapi/has_externalized_test_and_one_example.yaml").toFeature()

        val results = feature.executeTests(object : TestExecutor {
            override fun execute(request: HttpRequest): HttpResponse {
                assertThat(request.path).isEqualTo("/order_action_figure")
                assertThat(request.method).isEqualTo("POST")
                assertThat(request.body).isEqualTo(parsedJSONObject("""{"name": "Master Yoda", "description": "Head of the Jedi Council"}"""))

                return HttpResponse.OK(parsedJSONObject("""{"id": 1}"""))
            }

            override fun setServerState(serverState: Map<String, Value>) {
            }
        })

        assertThat(results.successCount).isEqualTo(1)
        assertThat(results.failureCount).isEqualTo(0)
    }

    @Test
    fun `externalized tests should be validated`() {
        val feature = OpenApiSpecification.fromFile("src/test/resources/openapi/has_invalid_externalized_test.yaml").toFeature()

        assertThatThrownBy {
            feature.executeTests(object : TestExecutor {
                override fun execute(request: HttpRequest): HttpResponse {
                    assertThat(request.path).isEqualTo("/order_action_figure")
                    assertThat(request.method).isEqualTo("POST")
                    assertThat(request.body).isEqualTo(parsedJSONObject("""{"name": "Master Yoda", "description": "Head of the Jedi Council"}"""))

                    return HttpResponse.OK(parsedJSONObject("""{"id": 1}"""))
                }

                override fun setServerState(serverState: Map<String, Value>) {
                }
            })
        }.satisfies(Consumer {
            assertThat(it).isInstanceOf(ContractException::class.java)
            it as ContractException

            assertThat(it.report())
                .contains(">> description")
                .contains("10")

            println(it.report())
        })

    }

    @Test
    fun `externalized tests be converted to rows`() {
        val feature = OpenApiSpecification.fromFile("src/test/resources/openapi/has_two_externalised_tests.yaml").toFeature()
        assertThat(feature.scenarios.first().examples.first().rows.size).isEqualTo(2)
    }

    @Test
    fun `externalized tests with query parameters`() {
        val feature = OpenApiSpecification.fromFile("src/test/resources/openapi/has_externalised_test_with_query_params.yaml").toFeature()

        val results = feature.executeTests(object : TestExecutor {
            override fun execute(request: HttpRequest): HttpResponse {
                assertThat(request.queryParams).containsEntry("description", "Jedi")

                return HttpResponse.OK(parsedJSONArray("""[{"name": "Master Yoda", "description": "Head of the Jedi Council"}]"""))
            }

            override fun setServerState(serverState: Map<String, Value>) {
            }
        })

        println(results.report())

        assertThat(results.successCount).isEqualTo(1)
        assertThat(results.failureCount).isEqualTo(0)
    }
}
