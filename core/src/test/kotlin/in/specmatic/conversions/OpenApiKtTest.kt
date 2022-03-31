package `in`.specmatic.conversions

import `in`.specmatic.core.*
import `in`.specmatic.core.log.Verbose
import `in`.specmatic.core.log.logger
import `in`.specmatic.core.pattern.ContractException
import `in`.specmatic.core.value.Value
import `in`.specmatic.stub.HttpStub
import `in`.specmatic.stub.createStubFromContracts
import `in`.specmatic.test.TestExecutor
import com.fasterxml.jackson.annotation.JsonProperty
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.junit.Ignore
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import org.springframework.core.ParameterizedTypeReference
import org.springframework.http.HttpEntity
import org.springframework.http.HttpHeaders
import org.springframework.http.HttpMethod
import org.springframework.http.HttpStatus.BAD_REQUEST
import org.springframework.util.LinkedMultiValueMap
import org.springframework.util.MultiValueMap
import org.springframework.web.client.HttpClientErrorException
import org.springframework.web.client.RestTemplate
import org.testcontainers.shaded.com.fasterxml.jackson.databind.ObjectMapper
import java.io.File
import java.net.URI
import java.util.function.Consumer
import java.util.regex.Pattern


internal class OpenApiKtTest {
    companion object {
        val openAPISpec = """
Feature: Hello world

Background:
  Given openapi openapi/hello.yaml            

Scenario: zero should return not found
  When GET /hello/0
  Then status 404
        """.trimIndent()

        private val sourceSpecPath = File("src/test/resources/hello.spec").canonicalPath

        @BeforeAll
        @JvmStatic
        fun setup() {
            logger = Verbose()
        }
    }

    @Test
    fun `should create stub from gherkin that includes OpenAPI spec`() {
        val feature = parseGherkinStringToFeature(openAPISpec, sourceSpecPath)

        val response = HttpStub(feature).use {
            val restTemplate = RestTemplate()
            restTemplate.exchange(
                URI.create("http://localhost:9000/hello/1"), HttpMethod.GET,
                httpEntityWithBearerAuthHeader(), String::class.java
            )
        }

        assertThat(response.statusCodeValue).isEqualTo(200)

        HttpStub(feature).use {
            val restTemplate = RestTemplate()
            try {
                restTemplate.exchange(
                    URI.create("http://localhost:9000/hello/0"),
                    HttpMethod.GET,
                    httpEntityWithBearerAuthHeader(),
                    String::class.java
                )
            } catch (e: HttpClientErrorException) {
                assertThat(e.statusCode).isEqualTo(org.springframework.http.HttpStatus.NOT_FOUND)
            }
        }
    }

    @Test
    fun `should create test from gherkin that includes OpenAPI spec`() {
        val flags = mutableMapOf<String, Boolean>()

        val feature = parseGherkinStringToFeature(openAPISpec, sourceSpecPath)

        val results = feature.executeTests(
            object : TestExecutor {
                override fun execute(request: HttpRequest): HttpResponse {
                    flags["${request.path} executed"] = true
                    assertThat(request.path).matches("""\/hello\/[0-9]+""")
                    val headers: HashMap<String, String> = object : HashMap<String, String>() {
                        init {
                            put("Content-Type", "application/json")
                        }
                    }
                    val id = request.path!!.split('/')[2].toInt()
                    val status = when (id) {
                        0 -> 404
                        else -> 200
                    }
                    return HttpResponse(status, "", headers)
                }

                override fun setServerState(serverState: Map<String, Value>) {
                }
            }
        )

        assertThat(flags["/hello/0 executed"]).isTrue
        assertThat(flags.size).isEqualTo(3)
        assertThat(results.report()).isEqualTo("""Match not found""".trimIndent())
    }

    @Test
    fun `should create tests from OpenAPI examples`() {
        val flags = mutableMapOf<String, Boolean>()

        val feature = parseGherkinStringToFeature(
            """
Feature: Hello world

Background:
  Given openapi openapi/helloWithExamples.yaml
        """.trimIndent(), sourceSpecPath
        )

        val results = feature.executeTests(
            object : TestExecutor {
                override fun execute(request: HttpRequest): HttpResponse {
                    flags["${request.path} executed"] = true
                    assertThat(request.path).matches("""\/hello\/[0-9]+""")
                    val headers: HashMap<String, String> = object : HashMap<String, String>() {
                        init {
                            put("Content-Type", "application/json")
                        }
                    }
                    val id = request.path!!.split('/')[2].toInt()
                    val status = when (id) {
                        0 -> 404
                        else -> 200
                    }
                    return HttpResponse(status, "hello world", headers)
                }

                override fun setServerState(serverState: Map<String, Value>) {
                }
            }
        )

        assertThat(flags["/hello/0 executed"]).isTrue
        assertThat(flags["/hello/15 executed"]).isTrue
        assertThat(flags.size).isEqualTo(3)
        assertThat(results.report()).isEqualTo("""Match not found""".trimIndent())
    }

    @Test
    fun `should report errors in tests created from OpenAPI examples`() {
        val flags = mutableMapOf<String, Boolean>()

        val feature = parseGherkinStringToFeature(
            """
Feature: Hello world

Background:
  Given openapi openapi/helloWithExamples.yaml
        """.trimIndent(), sourceSpecPath
        )

        val results = feature.executeTests(
            object : TestExecutor {
                override fun execute(request: HttpRequest): HttpResponse {
                    flags["${request.path} executed"] = true
                    assertThat(request.path).matches("""\/hello\/[0-9]+""")
                    val headers: HashMap<String, String> = object : HashMap<String, String>() {
                        init {
                            put("Content-Type", "application/json")
                        }
                    }
                    val id = request.path!!.split('/')[2].toInt()
                    val status = when (id) {
                        0 -> 403
                        else -> 202
                    }
                    return HttpResponse(status, "hello world", headers)
                }

                override fun setServerState(serverState: Map<String, Value>) {
                }
            }
        )

        assertThat(flags["/hello/0 executed"]).isTrue
        assertThat(flags["/hello/15 executed"]).isTrue
        assertThat(flags.size).isEqualTo(3)
        assertThat(results.report()).isEqualTo("""Match not found""".trimIndent())
    }

    @Test
    fun `should report error in test with both OpenAPI and Gherkin scenario names`() {
        val flags = mutableMapOf<String, Boolean>()

        val feature = parseGherkinStringToFeature(openAPISpec, sourceSpecPath)

        val results = feature.executeTests(
            object : TestExecutor {
                override fun execute(request: HttpRequest): HttpResponse {
                    flags["executed"] = true
                    assertThat(request.path).matches("""\/hello\/[0-9]+""")
                    val headers: HashMap<String, String> = object : HashMap<String, String>() {
                        init {
                            put("Content-Type", "application/json")
                        }
                    }
                    val id = request.path!!.split('/')[2].toInt()
                    val status = when (id) {
                        0 -> 403
                        else -> 202
                    }
                    return HttpResponse(status, "", headers)
                }

                override fun setServerState(serverState: Map<String, Value>) {
                }
            }
        )

        assertThat(flags["executed"]).isTrue
        assertFalse(results.success())
        assertThat(results.report()).isEqualTo("""Match not found""".trimIndent())
    }

    @Test
    fun `should throw error when request in Gherkin scenario does not match included OpenAPI spec`() {
        assertThatThrownBy {
            parseGherkinStringToFeature(
                """
        Feature: Hello world
        
        Background:
          Given openapi openapi/hello.yaml            
        
        Scenario: sending string instead of number should return not found
          When GET /hello/test
          Then status 404
                """.trimIndent(), sourceSpecPath
            )
        }.satisfies(Consumer {
            assertThat(it.message).isEqualTo("""Scenario: "sending string instead of number should return not found" PATH: "/hello/test" is not as per included wsdl / OpenApi spec""")
        })
    }

    @Test
    fun `should throw error when response code in Gherkin scenario does not match included OpenAPI spec`() {
        assertThatThrownBy {
            parseGherkinStringToFeature(
                """
        Feature: Hello world
        
        Background:
          Given openapi openapi/hello.yaml            
        
        Scenario: zero should return forbidden
          When GET /hello/0
          Then status 403
                """.trimIndent(), sourceSpecPath
            )
        }.satisfies(Consumer {
            assertThat(it.message).isEqualTo("""Scenario: "zero should return forbidden" RESPONSE STATUS: "403" is not as per included wsdl / OpenApi spec""")
        })
    }

    @Test
    fun `should generate stub with non primitive open api data types`() {
        val feature = parseGherkinStringToFeature(
            """
Feature: Hello world

Background:
  Given openapi openapi/petstore-expanded.yaml
        """.trimIndent(), sourceSpecPath
        )

        val response = HttpStub(feature).use {
            val restTemplate = RestTemplate()
            restTemplate.exchange(URI.create("http://localhost:9000/pets/1"), HttpMethod.GET, null, Pet::class.java)
        }

        assertThat(response.statusCodeValue).isEqualTo(200)
        assertThat(response.body).isInstanceOf(Pet::class.java)
    }

    @Test
    fun `should generate stub with non primitive array open api data types`() {
        val feature = parseGherkinStringToFeature(
            """
Feature: Hello world

Background:
  Given openapi openapi/petstore-expanded.yaml
        """.trimIndent(), sourceSpecPath
        )
        val headers = HttpHeaders()
        headers.set("X-Request-ID", "717e5682-c214-11eb-8529-0242ac130003")
        val requestEntity: HttpEntity<String> = HttpEntity("", headers)
        listOf("http://localhost:9000/pets", "http://localhost:9000/pets?tag=test&limit=3").forEach { urlString ->
            val response = HttpStub(feature).use {
                val restTemplate = RestTemplate()
                restTemplate.exchange(
                    URI.create(urlString),
                    HttpMethod.GET,
                    requestEntity,
                    object : ParameterizedTypeReference<List<Pet>>() {}
                )
            }

            assertThat(response.statusCodeValue).isEqualTo(200)
            assertThat(response.body).isInstanceOf(List::class.java)
            assertThat(response.body[0]).isInstanceOf(Pet::class.java)
            assertThat(response.headers.keys).containsAll(
                listOf(
                    "Content-Type",
                    "X-RateLimit-Limit",
                    "X-RateLimit-Remaining",
                    "X-RateLimit-Reset"
                )
            )
        }

        HttpStub(feature).use {
            val restTemplate = RestTemplate()
            try {
                restTemplate.exchange(
                    URI.create("http://localhost:9000/pets?tag=test&limit=three"),
                    HttpMethod.GET,
                    requestEntity,
                    object : ParameterizedTypeReference<List<Pet>>() {}
                )
            } catch (e: HttpClientErrorException) {
                assertThat(e.statusCode).isEqualTo(BAD_REQUEST)
            }
        }
    }

    @Test
    fun `should not throw errors while parsing file with no paths`() {
        OpenApiSpecification.fromFile("openapi/common.yaml").toFeature()
    }

    @Test
    fun `should run zero tests when the file has no paths`() {
        val feature = OpenApiSpecification.fromFile("openapi/common.yaml").toFeature()
        val results = feature.executeTests(
            object : TestExecutor {
                override fun execute(request: HttpRequest): HttpResponse = HttpResponse(200)
                override fun setServerState(serverState: Map<String, Value>) {}
            },
            scenarioNames = emptyList()
        )
        assertThat(results.hasResults()).isFalse
    }

    @Test
    fun `should generate stub with primitive array open api data types`() {
        val feature = parseGherkinStringToFeature(
            """
Feature: Hello world

Background:
  Given openapi openapi/petstore-expanded.yaml
        """.trimIndent(), sourceSpecPath
        )

        val response = HttpStub(feature).use {
            val restTemplate = RestTemplate()
            restTemplate.exchange(
                URI.create("http://localhost:9000/petIds"),
                HttpMethod.GET,
                httpEntityWithBearerAuthHeader(),
                object : ParameterizedTypeReference<List<Integer>>() {}
            )
        }

        assertThat(response.statusCodeValue).isEqualTo(200)
        assertThat(response.body).isInstanceOf(List::class.java)
        assertThat(response.body[0]).isInstanceOf(Integer::class.java)
    }

    private fun httpEntityWithBearerAuthHeader() = HttpEntity(null, HttpHeaders().also { it.setBearerAuth("test") })

    @Test
    fun `should generate stub that returns error when bearer auth security defined at operation level is not satisfied`() {
        val feature = parseGherkinStringToFeature(
            """
Feature: Hello world

Background:
  Given openapi openapi/petstore-expanded.yaml
        """.trimIndent(), sourceSpecPath
        )

        HttpStub(feature).use {
            val restTemplate = RestTemplate()
            val httpClientErrorException = assertThrows<HttpClientErrorException> {
                restTemplate.exchange(
                    URI.create("http://localhost:9000/petIds"),
                    HttpMethod.GET,
                    null,
                    object : ParameterizedTypeReference<List<Integer>>() {}
                )
            }
            assertThat(httpClientErrorException.statusCode).isEqualTo(BAD_REQUEST)
        }
    }

    @Test
    fun `should generate stub that returns error when bearer auth security defined at document level is not satisfied`() {
        val feature = parseGherkinStringToFeature(
            """
Feature: Hello world

Background:
  Given openapi openapi/hello.yaml
        """.trimIndent(), sourceSpecPath
        )

        HttpStub(feature).use {
            val restTemplate = RestTemplate()
            val httpClientErrorException = assertThrows<HttpClientErrorException> {
                restTemplate.exchange(
                    URI.create("http://localhost:9000/hello/0"),
                    HttpMethod.GET,
                    null,
                    object : ParameterizedTypeReference<List<Integer>>() {}
                )
            }
            assertThat(httpClientErrorException.statusCode).isEqualTo(BAD_REQUEST)
        }
    }

    @Test
    fun `should generate stub that returns authenticates with api key in header and query`() {
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

    @Test
    fun `should throw not supported error when a security scheme other than bearer auth is defined`() {
        assertThrows<ContractException> {
            parseGherkinStringToFeature(
                """
Feature: Hello world

Background:
  Given openapi openapi/unsupported-authentication.yaml
        """.trimIndent(), sourceSpecPath
            )
        }.also { assertThat(it.message).isEqualTo("Specmatic only supports bearer and api key authentication (header, query) scheme at the moment") }
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
        }.also { assertThat(it.message).isEqualTo("Specmatic only supports bearer and api key authentication (header, query) scheme at the moment") }
    }

    @Test
    fun `should generate stub with http post and non primitive request and response data types`() {
        val feature = parseGherkinStringToFeature(
            """
Feature: Hello world

Background:
  Given openapi openapi/petstore-expanded.yaml
        """.trimIndent(), sourceSpecPath
        )

        val petResponse = HttpStub(feature).use {
            val restTemplate = RestTemplate()
            restTemplate.postForObject(
                URI.create("http://localhost:9000/pets"),
                NewPet("scooby", "golden"),
                Pet::class.java
            )
        }

        assertThat(petResponse).isInstanceOf(Pet::class.java)
        assertThat(petResponse).isNotNull
    }

    @Test
    fun `should validate with cyclic reference in open api`() {
        val feature = parseGherkinStringToFeature(
            """
Feature: Hello world

Background:
  Given openapi openapi/petstore-post.yaml
        """.trimIndent(), sourceSpecPath
        )

        val result = testBackwardCompatibility(feature, feature)
        assertThat(result.success()).isTrue()
    }

    //TODO:
    @Ignore
    fun `should generate stub with cyclic reference in open api`() {
        val feature = parseGherkinStringToFeature(
            """
Feature: Hello world

Background:
  Given openapi openapi/petstore-post.yaml
        """.trimIndent(), sourceSpecPath
        )

        val petResponse = HttpStub(feature).use {
            val restTemplate = RestTemplate()
            restTemplate.postForObject(
                URI.create("http://localhost:9000/pets"),
                NewPet("scooby", "golden"),
                Pet::class.java
            )
        }

        assertThat(petResponse).isInstanceOf(CyclicPet::class.java)
        assertThat(petResponse).isNotNull
    }

    @Test
    fun `should parse nullable fields`() {
        val feature = parseGherkinStringToFeature(
            """
Feature: Hello world

Background:
  Given openapi openapi/petstore-expanded.yaml
        """.trimIndent(), sourceSpecPath
        )

        val petResponse = HttpStub(feature).use {
            val restTemplate = RestTemplate()
            restTemplate.postForObject(
                URI.create("http://localhost:9000/pets"),
                NewPet("scooby", null),
                Pet::class.java
            )
        }

        assertThat(petResponse).isInstanceOf(Pet::class.java)
        assertThat(petResponse).isNotNull
    }

    @Test
    fun `should generate stub with non primitive request which throws error on unexpected fields`() {
        val feature = parseGherkinStringToFeature(
            """
Feature: Hello world

Background:
  Given openapi openapi/petstore-expanded.yaml
        """.trimIndent(), sourceSpecPath
        )

        HttpStub(feature).use {
            val restTemplate = RestTemplate()
            try {
                restTemplate.postForObject(
                    URI.create("http://localhost:9000/pets"),
                    NewPetWithUnexpectedFields("scooby", "golden", Integer(4)),
                    Pet::class.java
                )
                throw AssertionError("Should not allow unexpected fields")
            } catch (e: HttpClientErrorException) {
                assertThat(e.statusCode).isEqualTo(BAD_REQUEST)
            }
        }
    }

    @Test
    fun `should generate stub with non primitive request which allows optional fields`() {
        val feature = parseGherkinStringToFeature(
            """
Feature: Hello world

Background:
  Given openapi openapi/petstore-expanded.yaml
        """.trimIndent(), sourceSpecPath
        )

        val petResponse = HttpStub(feature).use {
            val restTemplate = RestTemplate()
            restTemplate.postForObject(
                URI.create("http://localhost:9000/pets"),
                NewPetWithMissingTag("scooby"),
                Pet::class.java
            )
        }

        assertThat(petResponse).isInstanceOf(Pet::class.java)
        assertThat(petResponse).isNotNull
    }

    @Test
    fun `should generate stub with non primitive request which throws error on missing required fields`() {
        val feature = parseGherkinStringToFeature(
            """
Feature: Hello world

Background:
  Given openapi openapi/petstore-expanded.yaml
        """.trimIndent(), sourceSpecPath
        )

        HttpStub(feature).use {
            val restTemplate = RestTemplate()
            try {
                restTemplate.postForObject(
                    URI.create("http://localhost:9000/pets"),
                    NewPetWithMissingName("golden"),
                    Pet::class.java
                )
                throw AssertionError("Should not allow empty value on the name field which is required")
            } catch (e: HttpClientErrorException) {
                assertThat(e.statusCode).isEqualTo(BAD_REQUEST)
            }
        }
    }

    @Test
    fun `should create petstore tests`() {
        val flags = mutableMapOf<String, Int>().withDefault { 0 }

        val feature = parseGherkinStringToFeature(
            """
Feature: Hello world

Background:
  Given openapi openapi/petstore-expanded.yaml
  
  Scenario: get by tag
    When POST /pets
    Then status 201
    Examples:
      | tag     | name |
      | testing | test |
      
  Scenario: zero return bad request
    When GET /pets/0
    Then status 400
        """.trimIndent(), sourceSpecPath
        )

        val results = feature.executeTests(
            object : TestExecutor {
                override fun execute(request: HttpRequest): HttpResponse {
                    val flagKey = "${request.path} ${request.method} executed"
                    flags[flagKey] = flags.getValue(flagKey) + 1
                    val headers: HashMap<String, String> = object : HashMap<String, String>() {
                        init {
                            put("Content-Type", "application/json")
                        }
                    }
                    val pet = Pet("scooby", "golden", 1, "retriever", 2)
                    return when {
                        request.path!!.matches(Regex("""\/pets\/[0-9]+""")) -> when (request.method) {
                            "GET" -> {
                                when (request.path) {
                                    "/pets/0" -> HttpResponse(
                                        400,
                                        ObjectMapper().writeValueAsString(Error(1, "zero is not allowed")),
                                        headers
                                    )
                                    else -> HttpResponse(
                                        200,
                                        ObjectMapper().writeValueAsString(pet),
                                        headers
                                    )
                                }
                            }
                            "DELETE" -> HttpResponse(
                                204,
                                headers
                            )
                            else -> HttpResponse(400, "", headers)
                        }
                        request.path == "/pets" -> {
                            when (request.method) {
                                "GET" -> {
                                    HttpResponse(
                                        200,
                                        ObjectMapper().writeValueAsString(listOf(pet)),
                                        object : HashMap<String, String>() {
                                            init {
                                                put("Content-Type", "application/json")
                                                put("X-RateLimit-Reset", "2021-05-31T17:32:28Z")
                                                put("X-Date-DataType", "2021-05-31")
                                                put("X-Boolean-DataType", "true")
                                                put("X-Number-DataType", "123123.123123")
                                            }
                                        }
                                    )
                                }
                                "POST" -> {
                                    assertThat(request.bodyString).isEqualTo(
                                        """
                                        {
                                            "tag": "testing",
                                            "name": "test"
                                        }
                                    """.trimIndent()
                                    )
                                    HttpResponse(
                                        201,
                                        ObjectMapper().writeValueAsString(pet),
                                        headers
                                    )
                                }
                                else -> HttpResponse(400, "", headers)
                            }
                        }
                        request.path == "/petIds" -> {
                            when (request.method) {
                                "GET" -> {
                                    if (request.headers.containsKey("Authorization")) {
                                        HttpResponse(
                                            200,
                                            ObjectMapper().writeValueAsString(listOf(1)),
                                            headers
                                        )
                                    } else {
                                        HttpResponse(403, "UnAuthorized", headers)
                                    }
                                }
                                else -> HttpResponse(400, "", headers)
                            }
                        }
                        else -> HttpResponse(400, "", headers)
                    }
                }

                override fun setServerState(serverState: Map<String, Value>) {
                }
            }
        )

        assertThat(flags["/pets POST executed"]).isEqualTo(1)
        assertThat(flags["/pets GET executed"]).isEqualTo(12)
        assertThat(flags["/petIds GET executed"]).isEqualTo(4)
        assertThat(flags["/pets/0 GET executed"]).isEqualTo(1)
        assertThat(flags.keys.filter { it.matches(Regex("""\/pets\/[0-9]+ GET executed""")) }.size).isEqualTo(2)
        assertThat(flags.keys.any { it.matches(Regex("""\/pets\/[0-9]+ DELETE executed""")) }).isNotNull
        assertThat(flags.size).isEqualTo(6)
        assertTrue(results.success(), results.report())
    }

    @Test
    fun `should report errors when a value other than string enum is returned`() {
        val flags = mutableMapOf<String, Int>().withDefault { 0 }

        val feature = parseGherkinStringToFeature(
            """
Feature: Hello world

Background:
  Given openapi openapi/petstore-expanded.yaml
        """.trimIndent(), sourceSpecPath
        )

        val results = feature.executeTests(
            object : TestExecutor {
                override fun execute(request: HttpRequest): HttpResponse {
                    val flagKey = "${request.path} ${request.method} executed"
                    flags[flagKey] = flags.getValue(flagKey) + 1
                    val headers: HashMap<String, String> = object : HashMap<String, String>() {
                        init {
                            put("Content-Type", "application/json")
                        }
                    }
                    val pet = Pet("scooby", "golden", 1, "malinois", 2)
                    return when {
                        request.path == "/pets" -> {
                            when (request.method) {
                                "POST" -> {
                                    HttpResponse(
                                        201,
                                        ObjectMapper().writeValueAsString(pet),
                                        headers
                                    )
                                }
                                else -> HttpResponse(400, "", headers)
                            }
                        }
                        else -> HttpResponse(400, "", headers)
                    }
                }

                override fun setServerState(serverState: Map<String, Value>) {
                }
            },
            scenarioNames = listOf("create a pet. Response: pet response")
        )

        assertFalse(results.success())
        val reportText = """
            In scenario "create a pet. Response: pet response"
            API: POST /pets -> 201

              >> RESPONSE.BODY.breed
              
                 ${
            ContractAndResponseMismatch.mismatchMessage(
                """("labrador" or "retriever" or "null")""",
                "string: \"malinois\""
            )
        }
            """.trimIndent()
        assertThat(results.report()).contains(
            reportText
        )

        assertThat(countMatches(results.report(), reportText)).isEqualTo(3)
    }

    @Test
    fun `should report errors when a value other than numeric enum is returned`() {
        val flags = mutableMapOf<String, Int>().withDefault { 0 }

        val feature = parseGherkinStringToFeature(
            """
Feature: Hello world

Background:
  Given openapi openapi/petstore-expanded.yaml
        """.trimIndent(), sourceSpecPath
        )

        val results = feature.executeTests(
            object : TestExecutor {
                override fun execute(request: HttpRequest): HttpResponse {
                    val flagKey = "${request.path} ${request.method} executed"
                    flags[flagKey] = flags.getValue(flagKey) + 1
                    val headers: HashMap<String, String> = object : HashMap<String, String>() {
                        init {
                            put("Content-Type", "application/json")
                        }
                    }
                    val pet = Pet("scooby", "golden", 1, "retriever", 3)
                    return when {
                        request.path == "/pets" -> {
                            when (request.method) {
                                "POST" -> {
                                    HttpResponse(
                                        201,
                                        ObjectMapper().writeValueAsString(pet),
                                        headers
                                    )
                                }
                                else -> HttpResponse(400, "", headers)
                            }
                        }
                        else -> HttpResponse(400, "", headers)
                    }
                }

                override fun setServerState(serverState: Map<String, Value>) {
                }
            },
            scenarioNames = listOf("create a pet. Response: pet response")
        )

        assertFalse(results.success())
        val reportText = """
            In scenario "create a pet. Response: pet response"
            API: POST /pets -> 201
            
              >> RESPONSE.BODY.rating
              
                 ${ContractAndResponseMismatch.mismatchMessage("(1 or 2)", "number: 3")}
            """.trimIndent()
        assertThat(results.report()).contains(
            reportText
        )

        assertThat(countMatches(results.report(), reportText)).isEqualTo(3)
    }

    @Test
    fun `should report errors when a string is not as per restrictions`() {
        val flags = mutableMapOf<String, Int>().withDefault { 0 }

        val feature = parseGherkinStringToFeature(
            """
Feature: Hello world

Background:
  Given openapi openapi/petstore-expanded.yaml
        """.trimIndent(), sourceSpecPath
        )

        val results = feature.executeTests(
            object : TestExecutor {
                override fun execute(request: HttpRequest): HttpResponse {
                    val flagKey = "${request.path} ${request.method} executed"
                    flags[flagKey] = flags.getValue(flagKey) + 1
                    val headers: HashMap<String, String> = object : HashMap<String, String>() {
                        init {
                            put("Content-Type", "application/json")
                        }
                    }
                    val pet = Pet("small", "golden", 1, "retriever", 2)
                    return when {
                        request.path == "/pets" -> {
                            when (request.method) {
                                "POST" -> {
                                    HttpResponse(
                                        201,
                                        ObjectMapper().writeValueAsString(pet),
                                        headers
                                    )
                                }
                                else -> HttpResponse(400, "", headers)
                            }
                        }
                        else -> HttpResponse(400, "", headers)
                    }
                }

                override fun setServerState(serverState: Map<String, Value>) {
                }
            },
            scenarioNames = listOf("create a pet. Response: pet response")
        )

        assertFalse(results.success())
        val expectedReport = """
            In scenario "create a pet. Response: pet response"
            API: POST /pets -> 201

              >> RESPONSE.BODY.name
              
                 ${ContractAndResponseMismatch.mismatchMessage("string with minLength 6", "string: \"small\"")}
            """.trimIndent()
        assertThat(results.report()).contains(
            expectedReport
        )

        assertThat(countMatches(results.report(), expectedReport)).isEqualTo(3)
    }

    fun countMatches(string: String, pattern: String): Int {
        var index = 0
        var count = 0

        while (true)
        {
            index = string.indexOf(pattern, index)
            index += if (index != -1)
            {
                count++
                pattern.length
            }
            else {
                return count
            }
        }
    }

    @Test
    fun `should generate stub with json in form data`() {
        val feature = parseGherkinStringToFeature(
            """
Feature: Hello world

Background:
  Given openapi openapi/json-in-form-data.yaml
        """.trimIndent(), sourceSpecPath
        )

        val headers = HttpHeaders()
        headers.set("Content-Type", "application/x-www-form-urlencoded")

        val map: MultiValueMap<String, String> = LinkedMultiValueMap()
        map.add("payload", """{"text":"json inside form data"}""")
        map.add("nonJsonPayload", "test")

        val request: HttpEntity<MultiValueMap<String, String>> = HttpEntity<MultiValueMap<String, String>>(map, headers)

        val response = HttpStub(feature).use {
            val restTemplate = RestTemplate()
            restTemplate.postForEntity(
                URI.create("http://localhost:9000/services/jsonAndNonJsonPayload"),
                request,
                String::class.java
            )
        }

        assertThat(response).isNotNull
        assertThat(response.statusCodeValue).isEqualTo(200)
    }

    @Test
    fun `should generate tests with json in form data`() {
        val flags = mutableMapOf<String, Int>().withDefault { 0 }

        val feature = parseGherkinStringToFeature(
            """
Feature: Hello world

Background:
  Given openapi openapi/json-in-form-data.yaml
        """.trimIndent(), sourceSpecPath
        )

        val results = feature.executeTests(
            object : TestExecutor {
                override fun execute(request: HttpRequest): HttpResponse {
                    val flagKey = "${request.path} ${request.method} executed"
                    flags[flagKey] = flags.getValue(flagKey) + 1
                    val headers: HashMap<String, String> = object : HashMap<String, String>() {
                        init {
                            put("Content-Type", "application/json")
                        }
                    }
                    return when (request.path) {
                        "/services/jsonAndNonJsonPayload" -> {
                            if (request.method == "POST" &&
                                request.headers["Content-Type"] == "application/x-www-form-urlencoded" &&
                                readFormField(request, "payload")["text"] != null
                            ) HttpResponse(
                                200,
                                "",
                                headers
                            ) else return HttpResponse(400, "", headers)

                        }
                        "/services/nonJsonPayloadOnly" -> {
                            if (request.method == "POST" &&
                                request.headers["Content-Type"] == "application/x-www-form-urlencoded" &&
                                request.formFields["nonJsonPayload"] != null
                            ) HttpResponse(
                                200,
                                "",
                                headers
                            ) else return HttpResponse(400, "", headers)

                        }
                        else -> return HttpResponse(400, "", headers)
                    }
                }

                private fun readFormField(request: HttpRequest, fieldName: String) =
                    ObjectMapper().readValue(request.formFields[fieldName], Map::class.java)

                override fun setServerState(serverState: Map<String, Value>) {
                }
            }
        )

        assertThat(flags["/services/jsonAndNonJsonPayload POST executed"]).isEqualTo(1)
        assertThat(flags["/services/nonJsonPayloadOnly POST executed"]).isEqualTo(1)
        assertThat(results.success()).isTrue
    }

    @Test
    fun `should not drop the query params declared in yaml when loading a test scenario from a wrapper spec`() {
        val openAPISpec = """
Feature: Hello world

Background:
  Given openapi openapi/helloWithQueryParams.yaml            

Scenario: zero should return not found
  When GET /hello
  Then status 200
        """.trimIndent()

        val feature = parseGherkinStringToFeature(openAPISpec, sourceSpecPath)

        var executed = false

        val result = executeTest(feature.scenarios.first(), object : TestExecutor {
            override fun execute(request: HttpRequest): HttpResponse {
                executed = true
                assertThat(request.queryParams).containsKey("id")
                return HttpResponse.OK
            }

            override fun setServerState(serverState: Map<String, Value>) {

            }
        })

        assertThat(result).isInstanceOf(Result.Success::class.java)
        assertThat(executed).isTrue
    }
}

data class Pet(
    @JsonProperty("name") val name: String,
    @JsonProperty("tag") val tag: String,
    @JsonProperty("id") val id: Int,
    @JsonProperty("breed") val breed: String?,
    @JsonProperty("rating") val rating: Int
)

data class CyclicPet(
    @JsonProperty("name") val name: String,
    @JsonProperty("tag") val tag: String,
    @JsonProperty("id") val id: Int,
    @JsonProperty("parent") val parent: CyclicPet
)

data class NewPet(
    @JsonProperty("name") val name: String,
    @JsonProperty("tag") val tag: String?,
)

data class NewPetWithUnexpectedFields(
    @JsonProperty("name") val name: String,
    @JsonProperty("tag") val tag: String,
    @JsonProperty("age") val age: Integer,
)

data class NewPetWithMissingTag(
    @JsonProperty("name") val name: String,
)

data class NewPetWithMissingName(
    @JsonProperty("tag") val tag: String,
)

data class Error(
    @JsonProperty("code") val code: Int,
    @JsonProperty("message") val message: String
)