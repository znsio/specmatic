package `in`.specmatic.core

import `in`.specmatic.conversions.OpenApiSpecification
import `in`.specmatic.core.Result.Success
import `in`.specmatic.stub.HttpStub
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.RepeatedTest
import org.junit.jupiter.api.Test

class CyclePrevention {
    @Test
    @RepeatedTest(5)
    fun `test cycle in optional key to circular ref`() {
//        key? -> circular-ref-value

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
                  properties:
                    key:
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

        val response = HttpStub(feature).use {
            it.client.execute(HttpRequest("GET", "/data"))
        }

        assertThat(feature.scenarios.first().let { it.httpResponsePattern.matches(response, it.resolver) }).isInstanceOf(
            Success::class.java)
    }

    @Test
//    @RepeatedTest(5)
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
                  properties:
                    key:
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

        val response = HttpStub(feature).use {
            it.client.execute(HttpRequest("GET", "/data"))
        }

        assertThat(response.status).isEqualTo(400)
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