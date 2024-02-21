package `in`.specmatic.core

import `in`.specmatic.conversions.OpenApiSpecification
import org.assertj.core.api.Assertions
import org.junit.jupiter.api.Test

class TestCountTest {
    @Test
    fun `request body is string`() {
        val spec = """
            openapi: 3.0.0
            info:
              title: Sample API
              version: 1.0.0
            paths:
              /users:
                post:
                  requestBody:
                    content:
                      text/plain:
                        schema:
                          type: string
                  responses:
                    '200':
                      description: A complex object array response
                      content:
                        text/plain:
                          schema:
                            type: string
        """.trimIndent()

        Assertions.assertThat(spec.testCount()).isEqualTo(spec.contractTests().size)
    }

    @Test
    fun `json request with one mandatory key`() {
        val spec = """
            openapi: 3.0.0
            info:
              title: Sample API
              version: 1.0.0
            paths:
              /users:
                post:
                  requestBody:
                    content:
                      application/json:
                        schema:
                          type: object
                          required:
                          - name
                          properties:
                            name:
                              type: string
                  responses:
                    '200':
                      description: A complex object array response
                      content:
                        text/plain:
                          schema:
                            type: string
        """.trimIndent()

        Assertions.assertThat(spec.testCount()).isEqualTo(spec.contractTests().size)
    }

    @Test
    fun `json request with one optional key`() {
        val spec = """
            openapi: 3.0.0
            info:
              title: Sample API
              version: 1.0.0
            paths:
              /users:
                post:
                  requestBody:
                    content:
                      application/json:
                        schema:
                          type: object
                          properties:
                            name:
                              type: string
                  responses:
                    '200':
                      description: A complex object array response
                      content:
                        text/plain:
                          schema:
                            type: string
        """.trimIndent()

        Assertions.assertThat(spec.testCount()).isEqualTo(spec.contractTests().size)
    }

    @Test
    fun `json request with one mandatory key having an object with one mandatory key`() {
        val spec = """
            openapi: 3.0.0
            info:
              title: Sample API
              version: 1.0.0
            paths:
              /users:
                post:
                  requestBody:
                    content:
                      application/json:
                        schema:
                          type: object
                          required:
                            - name
                          properties:
                            name:
                              type: object
                              required:
                                - firstname
                              properties:
                                firstname:
                                  type: string
                  responses:
                    '200':
                      description: A complex object array response
                      content:
                        text/plain:
                          schema:
                            type: string
        """.trimIndent()

        Assertions.assertThat(spec.testCount()).isEqualTo(spec.contractTests().size)
    }

    @Test
    fun `json request with one mandatory key having an object with one optional key`() {
        val spec = """
            openapi: 3.0.0
            info:
              title: Sample API
              version: 1.0.0
            paths:
              /users:
                post:
                  requestBody:
                    content:
                      application/json:
                        schema:
                          type: object
                          required:
                            - name
                          properties:
                            name:
                              type: object
                              properties:
                                firstname:
                                  type: string
                  responses:
                    '200':
                      description: A complex object array response
                      content:
                        text/plain:
                          schema:
                            type: string
        """.trimIndent()

        Assertions.assertThat(spec.testCount()).isEqualTo(spec.contractTests().size)
    }

    @Test
    fun `json request with two mandatory keys`() {
        val spec = """
            openapi: 3.0.0
            info:
              title: Sample API
              version: 1.0.0
            paths:
              /users:
                post:
                  requestBody:
                    content:
                      application/json:
                        schema:
                          type: object
                          required:
                            - name
                            - address
                          properties:
                            name:
                              type: string
                            address:
                              type: string
                  responses:
                    '200':
                      description: A complex object array response
                      content:
                        text/plain:
                          schema:
                            type: string
        """.trimIndent()

        Assertions.assertThat(spec.testCount()).isEqualTo(spec.contractTests().size)
    }

    @Test
    fun `json request with one mandatory key and one optional key`() {
        val spec = """
            openapi: 3.0.0
            info:
              title: Sample API
              version: 1.0.0
            paths:
              /users:
                post:
                  requestBody:
                    content:
                      application/json:
                        schema:
                          type: object
                          required:
                            - name
                          properties:
                            name:
                              type: string
                            address:
                              type: string
                  responses:
                    '200':
                      description: A complex object array response
                      content:
                        text/plain:
                          schema:
                            type: string
        """.trimIndent()

        Assertions.assertThat(spec.testCount()).isEqualTo(spec.contractTests().size)
    }

    @Test
    fun `json request with two optional keys`() {
        val spec = """
            openapi: 3.0.0
            info:
              title: Sample API
              version: 1.0.0
            paths:
              /users:
                post:
                  requestBody:
                    content:
                      application/json:
                        schema:
                          type: object
                          properties:
                            name:
                              type: string
                            address:
                              type: string
                  responses:
                    '200':
                      description: A complex object array response
                      content:
                        text/plain:
                          schema:
                            type: string
        """.trimIndent()

        Assertions.assertThat(spec.testCount()).isEqualTo(spec.contractTests().size)
    }

    @Test
    fun `json request with two json keys having two mandatory keys`() {
        val spec = """
            openapi: 3.0.0
            info:
              title: Sample API
              version: 1.0.0
            paths:
              /users:
                post:
                  requestBody:
                    content:
                      application/json:
                        schema:
                          type: object
                          required:
                            - name
                            - address
                          properties:
                            name:
                              type: object
                              required:
                                - firstname
                                - lastname
                              properties:
                                firstname:
                                  type: string
                                lastname:
                                  type: string
                            address:
                              type: object
                              required:
                                - city
                                - state
                              properties:
                                city:
                                  type: string
                                state:
                                  type: string
                  responses:
                    '200':
                      description: A complex object array response
                      content:
                        text/plain:
                          schema:
                            type: string
        """.trimIndent()

        Assertions.assertThat(spec.testCount()).isEqualTo(spec.contractTests().size)
    }

    @Test
    fun `json request with two mandatory json keys having one mandatory key and one optional key each`() {
        val spec = """
            openapi: 3.0.0
            info:
              title: Sample API
              version: 1.0.0
            paths:
              /users:
                post:
                  requestBody:
                    content:
                      application/json:
                        schema:
                          type: object
                          required:
                            - name
                            - address
                          properties:
                            name:
                              type: object
                              required:
                                - firstname
                              properties:
                                firstname:
                                  type: string
                                lastname:
                                  type: string
                            address:
                              type: object
                              required:
                                - city
                              properties:
                                city:
                                  type: string
                                state:
                                  type: string
                  responses:
                    '200':
                      description: A complex object array response
                      content:
                        text/plain:
                          schema:
                            type: string
        """.trimIndent()

        Assertions.assertThat(spec.testCount()).isEqualTo(spec.contractTests().size)
    }

    @Test
    fun `json request with two mandatory json keys having two optional keys`() {
        val spec = """
            openapi: 3.0.0
            info:
              title: Sample API
              version: 1.0.0
            paths:
              /users:
                post:
                  requestBody:
                    content:
                      application/json:
                        schema:
                          type: object
                          required:
                            - name
                            - address
                          properties:
                            name:
                              type: object
                              properties:
                                firstname:
                                  type: string
                                lastname:
                                  type: string
                            address:
                              type: object
                              properties:
                                city:
                                  type: string
                                state:
                                  type: string
                  responses:
                    '200':
                      description: A complex object array response
                      content:
                        text/plain:
                          schema:
                            type: string
        """.trimIndent()
    }
    @Test
    fun `json request with one optional key and one mandatory json keys having two mandatory keys each`() {
        val spec = """
        openapi: 3.0.0
        info:
          title: Sample API
          version: 1.0.0
        paths:
          /users:
            post:
              requestBody:
                content:
                  application/json:
                    schema:
                      type: object
                      required:
                        - name
                      properties:
                        name:
                          type: object
                          required:
                            - firstname
                            - lastname
                          properties:
                            firstname:
                              type: string
                            lastname:
                              type: string
                        address:
                          type: object
                          required:
                            - city
                            - state
                          properties:
                            city:
                              type: string
                            state:
                              type: string
              responses:
                '200':
                  description: A complex object array response
                  content:
                    text/plain:
                      schema:
                        type: string
    """.trimIndent()

        Assertions.assertThat(spec.testCount()).isEqualTo(spec.contractTests().size)
    }

    @Test
    fun `json request with one optional key and one mandatory json keys having one optional key and one mandatory key each`() {
        val spec = """
        openapi: 3.0.0
        info:
          title: Sample API
          version: 1.0.0
        paths:
          /users:
            post:
              requestBody:
                content:
                  application/json:
                    schema:
                      type: object
                      required:
                        - name
                      properties:
                        name:
                          type: object
                          required:
                            - firstname
                          properties:
                            firstname:
                              type: string
                            lastname:
                              type: string
                        address:
                          type: object
                          required:
                            - city
                          properties:
                            city:
                              type: string
                            state:
                              type: string
              responses:
                '200':
                  description: A complex object array response
                  content:
                    text/plain:
                      schema:
                        type: string
    """.trimIndent()

        Assertions.assertThat(spec.testCount()).isEqualTo(spec.contractTests().size)
    }

    @Test
    fun `json request with two optional json keys having one optional key and one mandatory key each`() {
        val spec = """
        openapi: 3.0.0
        info:
          title: Sample API
          version: 1.0.0
        paths:
          /users:
            post:
              requestBody:
                content:
                  application/json:
                    schema:
                      type: object
                      properties:
                        name:
                          type: object
                          required:
                            - firstname
                          properties:
                            firstname:
                              type: string
                            lastname:
                              type: string
                        address:
                          type: object
                          required:
                            - city
                          properties:
                            city:
                              type: string
                            state:
                              type: string
              responses:
                '200':
                  description: A complex object array response
                  content:
                    text/plain:
                      schema:
                        type: string
    """.trimIndent()

        Assertions.assertThat(spec.testCount()).isEqualTo(spec.contractTests().size)
    }

    @Test
    fun `there are three optional headers`() {
        val spec = """
            openapi: 3.0.0
            info:
              title: Sample API
              version: 1.0.0
            paths:
              /users:
                get:
                  parameters:
                    - name: trace-id
                      in: header
                      schema:
                        type: string
                    - name: auth
                      in: header
                      schema:
                        type: integer
                    - name: user-agent
                      in: header
                      schema:
                          type: string
                  responses:
                    '200':
                      description: A complex object array response
                      content:
                        text/plain:
                          schema:
                            type: string
        """.trimIndent()

        Assertions.assertThat(spec.testCount()).isEqualTo(spec.contractTests().size)
    }

    @Test
    fun `there are three optional query parameters`() {
        val spec = """
            openapi: 3.0.0
            info:
              title: Sample API
              version: 1.0.0
            paths:
              /users:
                get:
                  parameters:
                    - name: trace-id
                      in: query
                      schema:
                        type: string
                    - name: auth
                      in: query
                      schema:
                        type: integer
                    - name: user-agent
                      in: query
                      schema:
                          type: string
                  responses:
                    '200':
                      description: A complex object array response
                      content:
                        text/plain:
                          schema:
                            type: string
        """.trimIndent()

        Assertions.assertThat(spec.testCount()).isEqualTo(spec.contractTests().size)
    }

    @Test
    fun `there are three optional headers and a request body with 3 optional JSON keys`() {
        val spec = """
            openapi: 3.0.0
            info:
              title: Sample API
              version: 1.0.0
            paths:
              /users:
                post:
                  parameters:
                    - name: trace-id
                      in: query
                      schema:
                        type: string
                    - name: auth
                      in: query
                      schema:
                        type: integer
                    - name: user-agent
                      in: query
                      schema:
                          type: string
                  requestBody:
                    content:
                      application/json:
                        schema:
                          type: object
                          properties:
                            name:
                              type: string
                            address:
                              type: string
                  responses:
                    '200':
                      description: A complex object array response
                      content:
                        text/plain:
                          schema:
                            type: string
        """.trimIndent()

        Assertions.assertThat(spec.testCount()).isEqualTo(spec.contractTests().size)
    }

    private fun String.contractTests() = OpenApiSpecification.fromYAML(this, "").toFeature().generateContractTestScenarios(emptyList())
    private fun String.testCount() = OpenApiSpecification.fromYAML(this, "").toFeature().testCount().toInt()

}