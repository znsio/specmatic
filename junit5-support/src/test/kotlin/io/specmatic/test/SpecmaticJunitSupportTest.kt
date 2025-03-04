package io.specmatic.test

import io.specmatic.core.HttpRequest
import io.specmatic.core.HttpResponse
import io.specmatic.core.TestConfig
import io.specmatic.test.SpecmaticJUnitSupport.Companion.BASE_URL
import io.specmatic.test.listeners.ContractExecutionListener
import io.specmatic.test.reports.coverage.Endpoint
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatCode
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.ValueSource
import org.junit.platform.launcher.TestExecutionListener
import org.opentest4j.TestAbortedException
import java.util.*

class SpecmaticJunitSupportTest {
    companion object {
        val initialPropertyKeys = System.getProperties().mapKeys { it.key.toString() }.keys
    }

    @Test
    fun `should retain open api path parameter convention for parameterized endpoints`() {
        val result: Pair<Sequence<ContractTest>, List<Endpoint>> = SpecmaticJUnitSupport().loadTestScenarios(
            "./src/test/resources/spec_with_parameterized_paths.yaml",
            "",
            "",
            TestConfig(emptyMap(), emptyMap()),
            filterName = null,
            filterNotName = null
        )
        val specEndpoints = result.second
        assertThat(specEndpoints.count()).isEqualTo(2)
        assertThat(specEndpoints.all { it.path == "/sayHello/{name}" })
    }

    @ParameterizedTest
    @ValueSource(strings = ["http://test.com", "https://my-json-server.typicode.com/znsio/specmatic-documentation"])
    fun `should pick up and use valid baseURL system property when set`(validURL: String) {
        System.setProperty(BASE_URL, validURL)
        lateinit var url: String
        assertThatCode {
            url = SpecmaticJUnitSupport().getTestBaseURL()
        }.doesNotThrowAnyException()
        assertThat(url).isEqualTo(validURL)
    }

    @Test
    fun `should take the domain from the host system property when it is an URI`() {
        val baseURL = "http://test.com:8080/v1"
        System.setProperty(BASE_URL, baseURL)
        lateinit var url: String
        assertThatCode {
            url = SpecmaticJUnitSupport().getTestBaseURL()
        }.doesNotThrowAnyException()
        assertThat(url).isEqualTo(baseURL)
    }

    @ParameterizedTest
    @ValueSource(strings = ["http://invalid url.com", "http://localhost:abcd/", "http://localhost:80 80/", "http://localhost:a123/test"])
    fun `testBaseURL system property should be valid URI`(invalidURL: String) {
        System.setProperty(BASE_URL, invalidURL)
        val ex = assertThrows<TestAbortedException> {
            SpecmaticJUnitSupport().getTestBaseURL()
        }
        assertThat(ex.message).isEqualTo("Please specify a valid URL in $BASE_URL environment variable")
    }

    @Test
    fun `host system property should be valid`() {
        System.setProperty(BASE_URL, "https://invalid domain:8080/v1")
        val ex = assertThrows<TestAbortedException> {
            SpecmaticJUnitSupport().getTestBaseURL()
        }
        assertThat(ex.message).isEqualTo("Please specify a valid URL in $BASE_URL environment variable")
    }

    @Test
    fun `protocol system property should be valid`() {
        System.setProperty(BASE_URL, "ftp://example.com/v1")
        val ex = assertThrows<TestAbortedException> {
            SpecmaticJUnitSupport().getTestBaseURL()
        }
        assertThat(ex.message).isEqualTo(
            "Please specify a valid scheme / protocol (http or https) in $BASE_URL environment variable"
        )
    }

    @Test
    fun `port system property should be valid`() {
        System.setProperty(BASE_URL, "http://example.com:0/v1")
        val ex = assertThrows<TestAbortedException> {
            SpecmaticJUnitSupport().getTestBaseURL()
        }
        assertThat(ex.message).isEqualTo("Please specify a valid port number in $BASE_URL environment variable")
    }

    @Test
    fun `ContractExecutionListener should be registered`() {
        val registeredListeners = ServiceLoader.load(TestExecutionListener::class.java)
            .map { it.javaClass.name }
            .toMutableList()

        assertThat(registeredListeners).contains(ContractExecutionListener::class.java.name)
    }

    @Test
    fun `should be able to get actuator endpoints from swaggerUI`() {
        SpecmaticJUnitSupport.actuatorFromSwagger("", object: TestExecutor {
            override fun execute(request: HttpRequest): HttpResponse {
                return HttpResponse(
                    200,
                    body = """
                    openapi: 3.0.1
                    info:
                      title: Order BFF
                      version: '1.0'
                    paths:
                      /orders:
                        post:
                          responses:
                            '200':
                              description: OK
                      /products:
                        post:
                          responses:
                            '200':
                              description: OK
                      /findAvailableProducts/{date_time}:
                        get:
                          parameters:
                            - ${"$"}ref: '#/components/parameters/DateTimeParameter'
                          responses:
                            '200':
                              description: OK
                    components:
                        schemas:
                            DateTime:
                                type: string
                                format: date-time
                        parameters:
                            DateTimeParameter:
                                name: date_time
                                in: path
                                required: true
                                schema:
                                    ${"$"}ref: '#/components/schemas/DateTime'
                    """.trimIndent()
                )
            }
        })

        assertThat(SpecmaticJUnitSupport.openApiCoverageReportInput.endpointsAPISet).isTrue()
        assertThat(SpecmaticJUnitSupport.openApiCoverageReportInput.getApplicationAPIs()).isEqualTo(listOf(
            API("POST", "/orders"),
            API("POST", "/products"),
            API("GET", "/findAvailableProducts/{date_time}")
        ))
    }

    @AfterEach
    fun tearDown() {
        System.getProperties().keys.minus(initialPropertyKeys).forEach { println("Clearing $it"); System.clearProperty(it.toString()) }
    }
}