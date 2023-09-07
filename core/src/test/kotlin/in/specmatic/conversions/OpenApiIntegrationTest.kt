package `in`.specmatic.conversions

import com.google.common.net.HttpHeaders
import `in`.specmatic.core.*
import `in`.specmatic.core.value.Value
import `in`.specmatic.test.TestExecutor
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import java.io.File

class OpenApiIntegrationTest {
    private val sourceSpecPath = File("src/test/resources/hello.spec").canonicalPath

    @Test
    fun `should generate test with oauth2 security scheme with authorization header value from example`() {
        val contract: Feature = parseGherkinStringToFeature(
            """
Feature: Authenticated

  Background:
    Given openapi openapi/hello_with_oauth2.yaml
  
  Scenario: Query param auth test
    When GET /hello/(id:number)
    Then status 200
    
    Examples:
    | Authorization | id |
    | Bearer abc123 | 10 |
        """.trimIndent(), sourceSpecPath
        )

        val contractTests = contract.generateContractTestScenarios(emptyList())
        val result = executeTest(contractTests.single(), object : TestExecutor {
            override fun execute(request: HttpRequest): HttpResponse {
                assertThat(request.headers).containsEntry(HttpHeaders.AUTHORIZATION, "Bearer abc123")
                return HttpResponse.OK("success")
            }

            override fun setServerState(serverState: Map<String, Value>) {

            }

        })

        assertThat(result).isInstanceOf(Result.Success::class.java)
    }

    @Test
    fun `should generate test with oauth2 security scheme with random token in authorization header when no example exists`() {
        val contract: Feature = parseGherkinStringToFeature(
            """
Feature: Authenticated

  Background:
    Given openapi openapi/hello_with_oauth2.yaml
        """.trimIndent(), sourceSpecPath
        )

        val contractTests = contract.generateContractTestScenarios(emptyList())
        val result = executeTest(contractTests.single(), object : TestExecutor {
            override fun execute(request: HttpRequest): HttpResponse {
                assertThat(request.headers).containsKey(HttpHeaders.AUTHORIZATION)
                assertThat(request.headers[HttpHeaders.AUTHORIZATION]).matches("Bearer (\\S+)")
                return HttpResponse.OK("success")
            }

            override fun setServerState(serverState: Map<String, Value>) {

            }

        })

        assertThat(result).isInstanceOf(Result.Success::class.java)
    }

    @Test
    fun `should generate test with oauth2 security scheme with token in authorization header from security configuration`() {
        val feature = parseContractFileToFeature(
            "./src/test/resources/openapi/hello_with_oauth2.yaml", securityConfiguration = SecurityConfiguration(
                OpenAPI = OpenAPISecurityConfiguration(
                    securitySchemes = mapOf(
                        "oAuth2AuthCode" to OAuth2SecuritySchemeConfiguration(
                            "oauth2", "QWERTY1234"
                        )
                    )
                )
            )
        )
        val contractTests = feature.generateContractTestScenarios(emptyList())
        val result = executeTest(contractTests.single(), object : TestExecutor {
            override fun execute(request: HttpRequest): HttpResponse {
                assertThat(request.headers).containsKey(HttpHeaders.AUTHORIZATION)
                assertThat(request.headers[HttpHeaders.AUTHORIZATION]).matches("Bearer QWERTY1234")
                return HttpResponse.OK("success")
            }
            override fun setServerState(serverState: Map<String, Value>) {

            }
        })

        assertThat(result).isInstanceOf(Result.Success::class.java)
    }

    @Test
    fun `should generate tests with appropriate credentials for supported security schemes based on security configuration`() {
        val feature = parseContractFileToFeature(
            "./src/test/resources/openapi/hello_with_all_supported_security_schemes.yaml", securityConfiguration = SecurityConfiguration(
                OpenAPI = OpenAPISecurityConfiguration(
                    securitySchemes = mapOf(
                        "oAuth2AuthCode" to OAuth2SecuritySchemeConfiguration("oauth2","OAUTH1234"),
                        "BearerAuth" to BearerSecuritySchemeConfiguration("bearer", "BEARER1234"),
                        "ApiKeyAuthHeader" to APIKeySecuritySchemeConfiguration("apiKey", "API-HEADER-USER"),
                        "ApiKeyAuthQuery" to APIKeySecuritySchemeConfiguration("apiKey", "API-QUERY-PARAM-USER")
                    )
                )
            )
        )
        val contractTests = feature.generateContractTestScenarios(emptyList())
        val requestLogs = mutableListOf<String>()
        contractTests.forEach {
            val result = executeTest(it, object : TestExecutor {
                override fun execute(request: HttpRequest): HttpResponse {
                    request.headers[HttpHeaders.AUTHORIZATION]?.let { header ->
                        if (header == "Bearer OAUTH1234") {
                            requestLogs.add("Path ${request.path} invoked with ${HttpHeaders.AUTHORIZATION} header set with oAuth2 token: OAUTH1234")
                        }
                    }
                    request.headers[HttpHeaders.AUTHORIZATION]?.let { header ->
                        if (header == "Bearer BEARER1234") {
                            requestLogs.add("Path ${request.path} invoked with ${HttpHeaders.AUTHORIZATION} header set with bearer token: BEARER1234")
                        }
                    }
                    request.headers["X-API-KEY"]?.let { header ->
                        if (header == "API-HEADER-USER") {
                            requestLogs.add("Path ${request.path} invoked with X-API-KEY header set as API-HEADER-USER")
                        }
                    }
                    request.queryParams["apiKey"]?.let { queryParam ->
                        if (queryParam == "API-QUERY-PARAM-USER") {
                            requestLogs.add("Path ${request.path} invoked with 'apiKey' query parameter set as API-QUERY-PARAM-USER")
                        }
                    }
                    return HttpResponse.OK("success")
                }
                override fun setServerState(serverState: Map<String, Value>) {

                }
            })
            assertThat(result).isInstanceOf(Result.Success::class.java)
        }

        assertThat(
            requestLogs
        ).isEqualTo(listOf(
            "Path /hello-with-oauth2 invoked with Authorization header set with oAuth2 token: OAUTH1234",
            "Path /hello-with-bearer invoked with Authorization header set with bearer token: BEARER1234",
            "Path /hello-with-apikey-header invoked with X-API-KEY header set as API-HEADER-USER",
            "Path /hello-with-apikey-query-param invoked with 'apiKey' query parameter set as API-QUERY-PARAM-USER"
        ))
    }

    @Test
    fun `should match http request with authorization header for spec with oauth2 security scheme`() {
        val feature = parseGherkinStringToFeature(
            """
Feature: Hello world

Background:
  Given openapi openapi/hello_with_oauth2.yaml
        """.trimIndent(), sourceSpecPath
        )
        val httpRequest = HttpRequest(
            "GET",
            "/hello/1",
            mapOf(
                HttpHeaders.AUTHORIZATION to "Bearer foo"
            )
        )
        val result = feature.scenarios.first().httpRequestPattern.matches(httpRequest, Resolver())
        assertThat(result).isInstanceOf(Result.Success::class.java)
    }

    @Test
    fun `should not match http request with authorization header missing for spec with oauth2 security scheme`() {
        val feature = parseGherkinStringToFeature(
            """
Feature: Hello world

Background:
  Given openapi openapi/hello_with_oauth2.yaml
        """.trimIndent(), sourceSpecPath
        )
        val httpRequest = HttpRequest(
            "GET",
            "/hello/1"
        )
        val result = feature.scenarios.first().httpRequestPattern.matches(httpRequest, Resolver())
        assertThat(result).isInstanceOf(Result.Failure::class.java)
        assertThat(result.reportString()).contains("Authorization header is missing in request")
    }

    @Test
    fun `should not match http request with authorization header without bearer prefix for spec with oauth2 security scheme`() {
        val feature = parseGherkinStringToFeature(
            """
Feature: Hello world

Background:
  Given openapi openapi/hello_with_oauth2.yaml
        """.trimIndent(), sourceSpecPath
        )
        val httpRequest = HttpRequest(
            "GET",
            "/hello/1",
            mapOf(
                HttpHeaders.AUTHORIZATION to "foo"
            )
        )
        val result = feature.scenarios.first().httpRequestPattern.matches(httpRequest, Resolver())
        assertThat(result).isInstanceOf(Result.Failure::class.java)
        assertThat(result.reportString()).contains("Authorization header must be prefixed with \"Bearer\"")
    }
}