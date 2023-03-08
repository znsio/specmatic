package `in`.specmatic.core

import `in`.specmatic.conversions.OpenApiSpecification
import `in`.specmatic.core.Result.Success
import `in`.specmatic.core.pattern.parsedJSON
import `in`.specmatic.core.value.Value
import `in`.specmatic.stub.HttpStub
import `in`.specmatic.test.TestExecutor
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.RepeatedTest
import org.junit.jupiter.api.Test

class CyclePrevention {
    @Test
    fun `one level test`() {
        val contract = OpenApiSpecification.fromYAML("""
            openapi: "3.0.0"
            info:
              version: 1.0.0
              title: Data API
            paths:
              /data:
                post:
                  description: Get data
                  requestBody:
                    content:
                      application/json:
                        schema:
                          ${'$'}ref: '#/components/schemas/TopLevel'
                  responses:
                    '200':
                      description: data
            components:
              schemas:
                TopLevel:
                  nullable: true
                  type: object
                  properties:
                    key:
                      ${'$'}ref: '#/components/schemas/TopLevel'
        """.trimIndent(), "").toFeature()

        var testCount = 0

        contract.executeTests(object: TestExecutor {
            override fun execute(request: HttpRequest): HttpResponse {
                testCount += 1
                println(request.toLogString())
                return HttpResponse.OK
            }

            override fun setServerState(serverState: Map<String, Value>) {
            }

        })

        assertThat(testCount).isEqualTo(4)
    }

    @Test
    fun `two level test`() {
        val contract = OpenApiSpecification.fromYAML("""
            openapi: "3.0.0"
            info:
              version: 1.0.0
              title: Data API
            paths:
              /data:
                post:
                  description: Get data
                  requestBody:
                    content:
                      application/json:
                        schema:
                          ${'$'}ref: '#/components/schemas/TopLevel'
                  responses:
                    '200':
                      description: data
            components:
              schemas:
                TopLevel:
                  nullable: true
                  type: object
                  properties:
                    key:
                      ${'$'}ref: '#/components/schemas/NextLevel'
                NextLevel:
                  nullable: true
                  type: object
                  properties:
                    subkey:
                      ${'$'}ref: '#/components/schemas/TopLevel'
        """.trimIndent(), "").toFeature()

        var testCount = 0

        contract.executeTests(object: TestExecutor {
            override fun execute(request: HttpRequest): HttpResponse {
                testCount += 1
                println(request.toLogString())
                return HttpResponse.OK
            }

            override fun setServerState(serverState: Map<String, Value>) {
            }

        })

        assertThat(testCount).isEqualTo(8)
    }

    @Test
    @RepeatedTest(5)
    fun `test cycle in optional key to circular ref`() {
//        key? -> circular-ref-value

        val stubContract = OpenApiSpecification.fromYAML("""
            openapi: "3.0.0"
            info:
              version: 1.0.0
              title: Data API
            paths:
              /data:
                get:
                  description: Get data
                  responses:
                    '200':
                      description: data
                      content:
                        application/json:
                          schema:
                            ${'$'}ref: '#/components/schemas/TopLevel'
            components:
              schemas:
                TopLevel:
                  type: object
                  properties:
                    key:
                      ${'$'}ref: '#/components/schemas/TopLevel'
        """.trimIndent(), "").toFeature()

        HttpStub(stubContract).use {
            val randomResponse = it.client.execute(HttpRequest("GET", "/data"))
            assertThat(stubContract.scenarios.first().let { it.httpResponsePattern.matches(randomResponse, it.resolver) }).isInstanceOf(
                Success::class.java)

            val rawJSON = """
                {
                    "http-request": {
                        "method": "GET",
                        "path": "/data"
                    },
                    "http-response": {
                        "status": 200,
                        "body": {
                            "key": {
                                "key": {
                                    "key": {}
                                }
                            }
                        }
                    }
                }
            """.trimIndent()

            val expectationSettingResponse = setExpectation(rawJSON, it)

            assertThat(expectationSettingResponse.status).isEqualTo(200)
        }

    }

    private fun setExpectation(rawJSON: String, it: HttpStub): HttpResponse {
        val expectation = parsedJSON(rawJSON)
        val expectationSettingResponse =
            it.client.execute(HttpRequest("POST", "/_specmatic/expectations", body = expectation))
        return expectationSettingResponse
    }

    @Test
    @RepeatedTest(5)
    fun `test cycle in required key to nullable ref`() {
//        key -> circular-ref-value?

        val feature = OpenApiSpecification.fromYAML("""
            openapi: "3.0.0"
            info:
              version: 1.0.0
              title: Data API
            paths:
              /data:
                get:
                  description: Get data
                  responses:
                    '200':
                      description: data
                      content:
                        application/json:
                          schema:
                            ${'$'}ref: '#/components/schemas/TopLevel'
            components:
              schemas:
                TopLevel:
                  type: object
                  required:
                    - key
                  properties:
                    key:
                      oneOf:
                        - type: object
                          properties: {}
                          nullable: true
                        - ${'$'}ref: '#/components/schemas/TopLevel'
        """.trimIndent(), "").toFeature()

        HttpStub(feature).use {
            val response = it.client.execute(HttpRequest("GET", "/data"))

            assertThat(feature.scenarios.first().let { it.httpResponsePattern.matches(response, it.resolver) }).isInstanceOf(
                Success::class.java)

            assertThat(
                setExpectation(
                    """
                        {
                            "http-request": {
                                "method": "GET",
                                "path": "/data"
                            },
                            "http-response": {
                                "status": 200,
                                "body": {
                                    "key": {
                                        "key": {
                                            "key": null
                                        }
                                    }
                                }
                            }
                        }
                    """.trimIndent(), it
                ).status).isEqualTo(200)
        }
    }

    @Test
    @RepeatedTest(5)
    fun `test cycle in optional key to nullable ref`() {
//        key? -> circular-ref-value?

        val feature = OpenApiSpecification.fromYAML("""
            openapi: "3.0.0"
            info:
              version: 1.0.0
              title: Data API
            paths:
              /data:
                get:
                  description: Get data
                  responses:
                    '200':
                      description: data
                      content:
                        application/json:
                          schema:
                            ${'$'}ref: '#/components/schemas/TopLevel'
            components:
              schemas:
                TopLevel:
                  type: object
                  nullable: true
                  properties:
                    key:
                      ${'$'}ref: '#/components/schemas/TopLevel'
        """.trimIndent(), "").toFeature()

        HttpStub(feature).use {
            val response = it.client.execute(HttpRequest("GET", "/data"))

            assertThat(feature.scenarios.first().let { it.httpResponsePattern.matches(response, it.resolver) }).isInstanceOf(
                Success::class.java)

            assertThat(
                setExpectation(
                    """
                        {
                            "http-request": {
                                "method": "GET",
                                "path": "/data"
                            },
                            "http-response": {
                                "status": 200,
                                "body": {
                                    "key": {
                                        "key": {
                                            "key": null
                                        }
                                    }
                                }
                            }
                        }
                    """.trimIndent(), it
                ).status).isEqualTo(200)

            assertThat(
                setExpectation(
                    """
                        {
                            "http-request": {
                                "method": "GET",
                                "path": "/data"
                            },
                            "http-response": {
                                "status": 200,
                                "body": {
                                    "key": {
                                        "key": {
                                            "key": {}
                                        }
                                    }
                                }
                            }
                        }
                    """.trimIndent(), it
                ).status).isEqualTo(200)
        }
    }

    @Test
    fun `test cycle in key to circular ref`() {
//        key -> circular-ref-value

        val feature = OpenApiSpecification.fromYAML(
            """
            openapi: "3.0.0"
            info:
              version: 1.0.0
              title: Data API
            paths:
              /data:
                get:
                  description: Get data
                  responses:
                    '200':
                      description: data
                      content:
                        application/json:
                          schema:
                            ${'$'}ref: '#/components/schemas/TopLevel'
            components:
              schemas:
                TopLevel:
                  type: object
                  required:
                  - key
                  properties:
                    key:
                      ${'$'}ref: '#/components/schemas/TopLevel'
        """.trimIndent(), ""
        ).toFeature()

        HttpStub(feature).use {
            val response = it.client.execute(HttpRequest("GET", "/data"))
            assertThat(response.status).isEqualTo(400)

            assertThat(
                setExpectation(
                    """
                        {
                            "http-request": {
                                "method": "GET",
                                "path": "/data"
                            },
                            "http-response": {
                                "status": 200,
                                "body": {
                                    "key": {
                                        "key": {
                                            "key": {}
                                        }
                                    }
                                }
                            }
                        }
                    """.trimIndent(), it
                ).status).isEqualTo(400)
        }
    }

    @Test
    @RepeatedTest(5)
    fun `test cycle in required key to optional key to circular ref`() {
//        key1 -> { key2? -> circular-ref-value }

        val feature = OpenApiSpecification.fromYAML("""
            openapi: "3.0.0"
            info:
              version: 1.0.0
              title: Data API
            paths:
              /data:
                get:
                  description: Get data
                  responses:
                    '200':
                      description: data
                      content:
                        application/json:
                          schema:
                            ${'$'}ref: '#/components/schemas/TopLevel'
            components:
              schemas:
                TopLevel:
                  type: object
                  required:
                    - key1
                  properties:
                    key1:
                      ${'$'}ref: '#/components/schemas/NextLevel'
                NextLevel:
                  type: object
                  properties:
                    key2:
                      type:
                        ${'$'}ref: '#/components/schemas/TopLevel'
        """.trimIndent(), "").toFeature()

        val response = HttpStub(feature).use {
            it.client.execute(HttpRequest("GET", "/data"))
        }

        assertThat(feature.scenarios.first().let { it.httpResponsePattern.matches(response, it.resolver) }).isInstanceOf(
            Success::class.java)
    }

    @Test
    @RepeatedTest(5)
    fun `test cycle in required key to required key to nullable circular ref`() {
//        key1 -> { key2 -> circular-ref-value? }

        val feature = OpenApiSpecification.fromYAML("""
            openapi: "3.0.0"
            info:
              version: 1.0.0
              title: Data API
            paths:
              /data:
                get:
                  description: Get data
                  responses:
                    '200':
                      description: data
                      content:
                        application/json:
                          schema:
                            ${'$'}ref: '#/components/schemas/TopLevel'
            components:
              schemas:
                TopLevel:
                  type: object
                  required:
                    - key1
                  properties:
                    key1:
                      ${'$'}ref: '#/components/schemas/NextLevel'
                NextLevel:
                  type: object
                  required:
                    - key2
                  properties:
                    key2:
                      oneOf:
                        - type: object
                          properties: {}
                          nullable: true
                        - ${'$'}ref: '#/components/schemas/TopLevel'
        """.trimIndent(), "").toFeature()

        val response = HttpStub(feature).use {
            it.client.execute(HttpRequest("GET", "/data"))
        }

        assertThat(feature.scenarios.first().let { it.httpResponsePattern.matches(response, it.resolver) }).isInstanceOf(
            Success::class.java)
    }

    @Test
    @RepeatedTest(5)
    fun `test cycle in required key to optional key to nullable circular ref`() {
//        key1 -> { key2? -> circular-ref-value? }

        val feature = OpenApiSpecification.fromYAML("""
            openapi: "3.0.0"
            info:
              version: 1.0.0
              title: Data API
            paths:
              /data:
                get:
                  description: Get data
                  responses:
                    '200':
                      description: data
                      content:
                        application/json:
                          schema:
                            ${'$'}ref: '#/components/schemas/TopLevel'
            components:
              schemas:
                TopLevel:
                  type: object
                  required:
                    - key1
                  properties:
                    key1:
                      ${'$'}ref: '#/components/schemas/NextLevel'
                NextLevel:
                  type: object
                  properties:
                    key2:
                      type:
                        oneOf:
                          - type: object
                            properties: {}
                            nullable: true
                          - ${'$'}ref: '#/components/schemas/TopLevel'
        """.trimIndent(), "").toFeature()

        val response = HttpStub(feature).use {
            it.client.execute(HttpRequest("GET", "/data"))
        }

        assertThat(feature.scenarios.first().let { it.httpResponsePattern.matches(response, it.resolver) }).isInstanceOf(
            Success::class.java)
    }

    @Test
    fun `test cycle in required key to required key to circular ref`() {
//        key1 -> { key2 -> circular-ref-value }

        val feature = OpenApiSpecification.fromYAML("""
            openapi: "3.0.0"
            info:
              version: 1.0.0
              title: Data API
            paths:
              /data:
                get:
                  description: Get data
                  responses:
                    '200':
                      description: data
                      content:
                        application/json:
                          schema:
                            ${'$'}ref: '#/components/schemas/TopLevel'
            components:
              schemas:
                TopLevel:
                  type: object
                  required:
                  - key1
                  properties:
                    key1:
                      ${'$'}ref: '#/components/schemas/NextLevel'
                NextLevel:
                  type: object
                  required:
                  - key2
                  properties:
                    key2:
                      ${'$'}ref: '#/components/schemas/TopLevel'
        """.trimIndent(), "").toFeature()

        val response = HttpStub(feature).use {
            it.client.execute(HttpRequest("GET", "/data"))
        }

        assertThat(response.status).isEqualTo(400)
    }
}