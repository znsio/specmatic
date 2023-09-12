package `in`.specmatic.conversions

import com.google.common.net.HttpHeaders
import `in`.specmatic.core.*
import `in`.specmatic.core.pattern.ContractException
import `in`.specmatic.core.value.Value
import `in`.specmatic.stub.createStubFromContracts
import `in`.specmatic.test.TestExecutor
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import java.io.File

class OpenApiIntegrationTest {
    private val sourceSpecPath = File("src/test/resources/hello.spec").canonicalPath

    @Nested
    inner class Oauth2SecuritySchemeTest {

        @Nested
        inner class AuthorizationCodeFlowTest {

            @Test
            fun `should generate test with oauth2 authorization code security scheme with authorization header value from example`() {
                val contract: Feature = parseGherkinStringToFeature(
                    """
Feature: OAuth2

Background:
Given openapi openapi/hello_with_oauth2_authorization_code_flow.yaml

Scenario: Authorization Code Flow Test
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
            fun `should generate test with oauth2 authorization code security scheme with random token in authorization header when no example exists`() {
                val feature = parseContractFileToFeature("./src/test/resources/openapi/hello_with_oauth2_authorization_code_flow.yaml")
                val contractTests = feature.generateContractTestScenarios(emptyList())
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
            fun `should generate test with oauth2 authorization code security scheme with token in authorization header from security configuration`() {
                val feature = parseContractFileToFeature(
                    "./src/test/resources/openapi/hello_with_oauth2_authorization_code_flow.yaml",
                    securityConfiguration = SecurityConfiguration(
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
            fun `should match http request with authorization header for spec with oauth2 security scheme with authorization code`() {
                val feature = parseGherkinStringToFeature(
                    """
Feature: Hello world

Background:
Given openapi openapi/hello_with_oauth2_authorization_code_flow.yaml
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
            fun `should not match http request with authorization header missing for spec with oauth2 authorization code security scheme with authorization code`() {
                val feature = parseGherkinStringToFeature(
                    """
Feature: Hello world

Background:
Given openapi openapi/hello_with_oauth2_authorization_code_flow.yaml
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
            fun `should not match http request with authorization header without bearer prefix for spec with oauth2 authorization code security scheme with authorization code`() {
                val feature = parseGherkinStringToFeature(
                    """
Feature: Hello world

Background:
Given openapi openapi/hello_with_oauth2_authorization_code_flow.yaml
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

        @Nested
        inner class ClientCredentialsFlowTest {

            @Test
            fun `should generate test with oauth2 client credentials security scheme with authorization header value from example`() {
                val contract: Feature = parseGherkinStringToFeature(
                    """
Feature: Authenticated

  Background:
    Given openapi openapi/hello_with_oauth2_client_credentials_flow.yaml
  
  Scenario: OAuth2 Client Credentials Flow Test
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
            fun `should match http request with authorization header for spec with oauth2 security scheme with client credentials flow`() {
                val feature = parseGherkinStringToFeature(
                    """
Feature: Hello world

Background:
Given openapi openapi/hello_with_oauth2_client_credentials_flow.yaml
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

        }

        @Nested
        inner class ImplicitFlowTest {

            @Test
            fun `should generate test with oauth2 implicit security scheme with authorization header value from example`() {
                val contract: Feature = parseGherkinStringToFeature(
                    """
Feature: Authenticated

  Background:
    Given openapi openapi/hello_with_oauth2_implicit_flow.yaml
  
  Scenario: OAuth2 Client Credentials Flow Test
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
            fun `should match http request with authorization header for spec with oauth2 security scheme with implicit flow`() {
                val feature = parseGherkinStringToFeature(
                    """
Feature: Hello world

Background:
Given openapi openapi/hello_with_oauth2_implicit_flow.yaml
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

        }

        @Nested
        inner class ResourceOwnerPasswordCredentialsFlowTest {

            @Test
            fun `should generate test with oauth2 resource owner password credentials security scheme with authorization header value from example`() {
                val contract: Feature = parseGherkinStringToFeature(
                    """
Feature: Authenticated

  Background:
    Given openapi openapi/hello_with_oauth2_resource_owner_password_credentials_flow.yaml
  
  Scenario: OAuth2 Client Credentials Flow Test
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
            fun `should match http request with authorization header for spec with oauth2 security scheme with resource owner password credentials flow`() {
                val feature = parseGherkinStringToFeature(
                    """
Feature: Hello world

Background:
Given openapi openapi/hello_with_oauth2_resource_owner_password_credentials_flow.yaml
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

        }
    }

    @Nested
    inner class BearerSecuritySchemeTest {
        @Test
        fun `should generate test with bearer security scheme with authorization header value from example`() {
            val contract: Feature = parseGherkinStringToFeature(
                """
Feature: Authenticated

  Background:
    Given openapi openapi/authenticated.yaml
  
  Scenario: Bearer auth test
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
                    assertThat(request.headers).containsEntry("Authorization", "Bearer abc123")
                    return HttpResponse.OK("success")
                }

                override fun setServerState(serverState: Map<String, Value>) {

                }

            })

            assertThat(result).isInstanceOf(Result.Success::class.java)
        }

        @Test
        fun `should generate test with bearer security scheme with random token in authorization header when no example exists`() {
            val feature = parseContractFileToFeature("./src/test/resources/openapi/authenticated.yaml")
            val contractTests = feature.generateContractTestScenarios(emptyList())
            var requestMadeWithRandomlyGeneratedBearerToken = false
            contractTests.forEach { scenario ->
                val result = executeTest(scenario, object : TestExecutor {
                    override fun execute(request: HttpRequest): HttpResponse {
                        request.headers[HttpHeaders.AUTHORIZATION]?.takeIf {
                            it.matches(Regex("Bearer (\\S+)"))
                        }?.let {
                            requestMadeWithRandomlyGeneratedBearerToken = true
                        }
                        return HttpResponse.OK("success")
                    }

                    override fun setServerState(serverState: Map<String, Value>) {

                    }
                })
                assertThat(result).isInstanceOf(Result.Success::class.java)
            }
            assertThat(requestMadeWithRandomlyGeneratedBearerToken).isTrue
        }

        @Test
        fun `should match http request with authorization header for spec with bearer security scheme`() {
            val feature = parseGherkinStringToFeature(
                """
Feature: Hello world

Background:
  Given openapi openapi/hello.yaml
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
        fun `should not match http request without authorization header for spec with bearer security scheme`() {
            val feature = parseGherkinStringToFeature(
                """
Feature: Hello world

Background:
  Given openapi openapi/hello.yaml
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
        fun `should not match http request with authorization header but without the bearer prefix for spec with bearer security scheme`() {
            val feature = parseGherkinStringToFeature(
                """
Feature: Hello world

Background:
  Given openapi openapi/hello.yaml
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

    @Nested
    inner class ApiKeySecuritySchemeTest {
        @Test
        fun `should generate test with query param api key auth security scheme value from row`() {
            val contract: Feature = parseGherkinStringToFeature(
                """
Feature: Authenticated

  Background:
    Given openapi openapi/authenticated.yaml
  
  Scenario: Query param auth test
    When GET /hello/(id:number)
    Then status 200
    
    Examples:
    | apiKey | id |
    | abc123 | 10 |
        """.trimIndent(), sourceSpecPath
            )

            val contractTests = contract.generateContractTestScenarios(emptyList())
            val result = executeTest(contractTests.single(), object : TestExecutor {
                override fun execute(request: HttpRequest): HttpResponse {
                    assertThat(request.queryParams).containsEntry("apiKey", "abc123")
                    return HttpResponse.OK("success")
                }

                override fun setServerState(serverState: Map<String, Value>) {

                }

            })

            assertThat(result).isInstanceOf(Result.Success::class.java)
        }

        @Test
        fun `should throw not supported error when a security scheme other than oauth2 bearer or api key is defined`() {
            assertThrows<ContractException> {
                parseGherkinStringToFeature(
                    """
Feature: Hello world

Background:
  Given openapi openapi/unsupported-authentication.yaml
        """.trimIndent(), sourceSpecPath
                )
            }.also { assertThat(it.message).isEqualTo("Specmatic only supports oauth2, bearer, and api key authentication (header, query) security schemes at the moment") }
        }

        @Test
        fun `should throw not supported error when a non-query-or-header API security scheme is defined`() {
            assertThrows<ContractException> {
                parseGherkinStringToFeature(
                    """
Feature: Hello world

Background:
  Given openapi openapi/apiKeyAuthCookie.yaml
        """.trimIndent(), sourceSpecPath
                )
            }.also { assertThat(it.message).isEqualTo("Specmatic only supports oauth2, bearer, and api key authentication (header, query) security schemes at the moment") }
        }

        @Test
        fun `should generate test with api key security scheme value from row`() {
            val contract: Feature = parseGherkinStringToFeature(
                """
Feature: Authenticated

  Background:
    Given openapi openapi/authenticated.yaml
  
  Scenario: Header auth test
    When GET /hello/(id:number)
    Then status 200
    
    Examples:
    | X-API-KEY | id |
    | abc123    | 10 |
        """.trimIndent(), sourceSpecPath
            )

            val contractTests = contract.generateContractTestScenarios(emptyList())
            val result = executeTest(contractTests.single(), object : TestExecutor {
                override fun execute(request: HttpRequest): HttpResponse {
                    assertThat(request.headers).containsEntry("X-API-KEY", "abc123")
                    return HttpResponse.OK("success")
                }

                override fun setServerState(serverState: Map<String, Value>) {

                }

            })

            assertThat(result).isInstanceOf(Result.Success::class.java)
        }

        @Test
        fun `should generate stub that authenticates with api key in header and query`() {
            createStubFromContracts(listOf("src/test/resources/openapi/apiKeyAuth.yaml")).use {
                val requestWithHeader = HttpRequest(
                    method = "GET",
                    path = "/hello/10",
                    headers = mapOf(
                        "X-API-KEY" to "test"
                    )
                )

                val responseFromHeader = it.client.execute(requestWithHeader)
                assertThat(responseFromHeader.status).isEqualTo(200)

                val requestWithQuery = HttpRequest(
                    method = "GET",
                    path = "/hello/10",
                    queryParams = mapOf(
                        "apiKey" to "test"
                    )
                )

                val responseFromQuery = it.client.execute(requestWithQuery)
                assertThat(responseFromQuery.status).isEqualTo(200)
            }
        }
    }

    @Test
    fun `should generate tests with appropriate credentials for supported security schemes based on security configuration`() {
        val feature = parseContractFileToFeature(
            "./src/test/resources/openapi/hello_with_all_supported_security_schemes.yaml",
            securityConfiguration = SecurityConfiguration(
                OpenAPI = OpenAPISecurityConfiguration(
                    securitySchemes = mapOf(
                        "oAuth2AuthCode" to OAuth2SecuritySchemeConfiguration("oauth2", "OAUTH1234"),
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
        ).isEqualTo(
            listOf(
                "Path /hello-with-oauth2 invoked with Authorization header set with oAuth2 token: OAUTH1234",
                "Path /hello-with-bearer invoked with Authorization header set with bearer token: BEARER1234",
                "Path /hello-with-apikey-header invoked with X-API-KEY header set as API-HEADER-USER",
                "Path /hello-with-apikey-query-param invoked with 'apiKey' query parameter set as API-QUERY-PARAM-USER"
            )
        )
    }
}