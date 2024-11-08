package integration_tests

import io.specmatic.conversions.OpenApiSpecification
import io.specmatic.core.HttpRequest
import io.specmatic.core.HttpResponse
import io.specmatic.core.Result
import io.specmatic.core.SPECMATIC_TYPE_HEADER
import io.specmatic.core.pattern.parsedJSONObject
import io.specmatic.core.value.JSONObjectValue
import io.specmatic.mock.ScenarioStub
import io.specmatic.stub.HttpStub
import io.specmatic.test.TestExecutor
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.fail
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.CsvSource

class OneOfDiscriminatorTest {
    @Nested
    inner class ChildrenDoNotHaveDiscriminator {
        val getAPI = OpenApiSpecification.fromYAML(
"""
openapi: 3.0.3
info:
  title: Product API
  version: 1.0.0
paths:
  /product/{id}:
    get:
      summary: Get product details by ID
      parameters:
        - name: id
          in: path
          required: true
          schema:
            type: string
      responses:
        '200':
          description: Successful response with product details
          content:
            application/json:
              schema:
                ${"$"}ref: '#/components/schemas/Product'
components:
  schemas:
    Product:
      type: object
      oneOf:
        - ${"$"}ref: '#/components/schemas/Food'
        - ${"$"}ref: '#/components/schemas/Gadget'
      discriminator:
        propertyName: type
        mapping:
          food: '#/components/schemas/Food'
          gadget: '#/components/schemas/Gadget'

    ProductType:
      type: object
      required:
        - type
      properties:
        type:
          type: string

    Food:
      allOf:
        - ${"$"}ref: '#/components/schemas/ProductType'
        - type: object
          properties:
            id:
              type: string
            name:
              type: string
            expirationDate:
              type: string
              format: date

    Gadget:
      allOf:
        - ${"$"}ref: '#/components/schemas/ProductType'
        - type: object
          properties:
            id:
              type: string
            name:
              type: string
            warrantyPeriod:
              type: string
              format: date
""".trimIndent(), ""
        ).toFeature()

        val patchAPI = OpenApiSpecification.fromYAML(
"""
openapi: 3.0.3
info:
  title: Product API
  version: 1.0.0
paths:
  /product/{id}:
    patch:
      summary: Update product details by ID
      parameters:
        - name: id
          in: path
          required: true
          schema:
            type: string
      requestBody:
        required: true
        content:
          application/json:
            schema:
              ${"$"}ref: '#/components/schemas/Product'
      responses:
        200:
          description: 'Successful response with product details'
          content:
            application/json:
              schema:
                ${"$"}ref: '#/components/schemas/Product'
components:
  schemas:
    Product:
      type: object
      oneOf:
        - ${"$"}ref: '#/components/schemas/Food'
        - ${"$"}ref: '#/components/schemas/Gadget'
      discriminator:
        propertyName: type
        mapping:
          food: '#/components/schemas/Food'
          gadget: '#/components/schemas/Gadget'

    ProductType:
      type: object
      required:
        - type
      properties:
        type:
          type: string

    Food:
      allOf:
        - ${"$"}ref: '#/components/schemas/ProductType'
        - type: object
          properties:
            id:
              type: string
            name:
              type: string
            expirationDate:
              type: string
              format: date

    Gadget:
      allOf:
        - ${"$"}ref: '#/components/schemas/ProductType'
        - type: object
          properties:
            id:
              type: string
            name:
              type: string
            warrantyPeriod:
              type: string
              format: date
""".trimIndent(), ""
        ).toFeature()

        @Nested
        inner class Stub {
            var stub = HttpStub(getAPI)

            @AfterEach
            fun teardown() {
                stub.close()
            }

            @Test
            fun `should be able to stub out all discriminated schemas`() {
                val request = HttpRequest("GET", "/product/123")

                fun check(example: ScenarioStub, key: String) {
                    stub.setExpectation(example)

                    stub.client.execute(example.request).let { response ->
                        assertThat(response.status).isEqualTo(200)
                        assertThat(response.headers).doesNotContainKey("X-Specmatic-Random")
                        assertThat((response.body as JSONObjectValue).jsonObject).containsKey(key)
                    }
                }

                val example1 = ScenarioStub(
                    request = request,
                    response = HttpResponse.ok(parsedJSONObject("""{"id": "10", "name": "rice", "type": "food", "expirationDate": "2022-12-31"}"""))
                )

                val example2 = ScenarioStub(
                    request = request,
                    response = HttpResponse.ok(parsedJSONObject("""{"id": "10", "name": "phone", "type": "gadget", "warrantyPeriod": "2025-01-01"}"""))
                )

                check(example1, "expirationDate")
                check(example2, "warrantyPeriod")
            }

            @Test
            fun `should return a randomized response for discriminatd schemas`() {
                stub.client.execute(HttpRequest("GET", "/product/345")).let { response ->
                    assertThat(response.status).isEqualTo(200)
                    assertThat(response.headers).containsKey(SPECMATIC_TYPE_HEADER)

                    val jsonResponse = response.body as? JSONObjectValue ?: fail("Expected response to be a json object")

                    if(jsonResponse.jsonObject["type"]?.toStringLiteral() == "food") {
                        assertThat(jsonResponse.jsonObject).containsKey("expirationDate")
                    } else if (jsonResponse.jsonObject["type"]?.toStringLiteral() == "gadget") {
                        assertThat(jsonResponse.jsonObject).containsKey("warrantyPeriod")
                    } else {
                        fail("Expected response to have a type of either food or gadget")
                    }
                }
            }
        }

        @Test
        fun `should generate a value with the correct discriminated structure`() {
            val scenario = getAPI.scenarios.first()

            val jsonResponse = scenario.httpResponsePattern.body.generate(scenario.resolver) as? JSONObjectValue ?: fail("Expected response to be a json object")

            if(jsonResponse.jsonObject["type"]?.toStringLiteral() == "food") {
                assertThat(jsonResponse.jsonObject).containsKey("expirationDate")
            } else if (jsonResponse.jsonObject["type"]?.toStringLiteral() == "gadget") {
                assertThat(jsonResponse.jsonObject).containsKey("warrantyPeriod")
            } else {
                fail("Expected response to have a type of either food or gadget")
            }
        }

        @ParameterizedTest
        @CsvSource(
            value = [
                """{"id": "10", "name": "rice", "type": "food", "expirationDate": "2022-12-31"}    | success""",
                """{"id": "10", "name": "phone", "type": "gadget", "warrantyPeriod": "2022-12-31"} | success""",
                """{"id": "10", "name": "phone", "type": "gadget", "expirationDate": "2022-12-31"} | failure""",
                """{"id": "10", "name": "phone", "type": "food", "warrantyPeriod": "2022-12-31"}   | failure""",
                """{"id": "10", "name": "phone", "type": "car", "warrantyPeriod": "2022-12-31"}    | failure"""
            ],
            delimiterString = "|"
        )
        fun `object match should be discriminator-based`(responsePayload: String, matchResult: String) {
            val request = HttpRequest("GET", "/product/123")

            getAPI.matchResult(
                request,
                HttpResponse.ok(parsedJSONObject(responsePayload))
            ).let { result ->
                when(matchResult.trim()) {
                    "success" -> assertThat(result).withFailMessage(result.reportString()).isInstanceOf(Result.Success::class.java)
                    "failure" -> assertThat(result).withFailMessage(result.reportString()).isInstanceOf(Result.Failure::class.java)
                    else -> fail("Invalid match result")
                }
            }
        }

        @ParameterizedTest
        @CsvSource(
            value = [
                """{"id": "10", "name": "rice", "type": "food", "expirationDate": "2022-12-31"}    | success""",
                """{"id": "10", "name": "phone", "type": "gadget", "warrantyPeriod": "2022-12-31"} | success""",
                """{"id": "10", "name": "phone", "type": "car", "warrantyPeriod": "2022-12-31"}    | failure"""
            ]
        )
        fun `test responses should validate discriminated values correctly`(responsePayload: String, testResult: String) {
            val results = getAPI.executeTests(object : TestExecutor {
                override fun execute(request: HttpRequest): HttpResponse {
                    return HttpResponse.ok(parsedJSONObject(responsePayload))
                }
            })

            when(testResult) {
                "success" -> assertThat(results.success()).withFailMessage(results.report()).isTrue()
                "failure" -> assertThat(results.success()).withFailMessage(results.report()).isFalse()
            }
        }

        @Test
        fun `tests from discriminator object without examples should generate the right objects`() {
            val results = patchAPI.executeTests(object : TestExecutor {
                override fun execute(request: HttpRequest): HttpResponse {
                    val jsonRequest = request.body as? JSONObjectValue ?: fail("Expected request to be a json object")

                    when(jsonRequest.findFirstChildByPath("type")?.toStringLiteral()) {
                        "food" -> assertThat(jsonRequest.jsonObject).containsKey("expirationDate")
                        "gadget" -> assertThat(jsonRequest.jsonObject).containsKey("warrantyPeriod")
                        else -> fail("Expected request to have a type of either food or gadget")
                    }

                    return HttpResponse.ok(parsedJSONObject("""{"id": "10", "name": "phone", "type": "gadget", "warrantyPeriod": "2022-12-31"}"""))
                }
            })
        }
    }

    @Test
    fun `discriminator in oneOf should be honored when children declare their own discriminator`() {
        val feature = OpenApiSpecification.fromYAML(
            """
openapi: 3.0.3
info:
  title: Product API
  version: 1.0.0
paths:
  /product/{id}:
    get:
      summary: Get product details by ID
      parameters:
        - name: id
          in: path
          required: true
          schema:
            type: string
      responses:
        '200':
          description: Successful response with product details
          content:
            application/json:
              schema:
                ${"$"}ref: '#/components/schemas/Product'

components:
  schemas:
    Product:
      type: object
      oneOf:
        - ${"$"}ref: '#/components/schemas/Food'
        - ${"$"}ref: '#/components/schemas/Gadget'
      discriminator:
        propertyName: type
        mapping:
          food: '#/components/schemas/Food'
          gadget: '#/components/schemas/Gadget'

    ProductType:
      type: object
      required:
        - type
      properties:
        type:
          type: string

    Food:
      allOf:
        - ${"$"}ref: '#/components/schemas/ProductType'
        - type: object
          properties:
            id:
              type: string
            name:
              type: string
            expirationDate:
              type: string
              format: date
      discriminator:
        propertyName: type
        mapping:
          food: '#/components/schemas/Food'

    Gadget:
      allOf:
        - ${"$"}ref: '#/components/schemas/ProductType'
        - type: object
          properties:
            id:
              type: string
            name:
              type: string
            warrantyPeriod:
              type: string
              format: date
      discriminator:
        propertyName: type
        mapping:
          gadget: '#/components/schemas/Gadget'
""".trimIndent(), ""
        ).toFeature()

        val request = HttpRequest("GET", "/product/123")
        val response =
            HttpResponse.ok(parsedJSONObject("""{"id": "10", "name": "phone", "type": "food", "expirationDate": "2022-12-31"}"""))

        val scenario = feature.scenarios.first()

        val responseBody = scenario.httpResponsePattern.body.generate(scenario.resolver) as JSONObjectValue

        assertThat(responseBody.findFirstChildByPath("type")?.toStringLiteral()).isIn("gadget", "food")

        println(responseBody.toStringLiteral())

        val result = feature.matchResult(request, response)

        assertThat(result).withFailMessage(result.reportString()).isInstanceOf(Result.Success::class.java)

    }
}