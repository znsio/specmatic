package `in`.specmatic.conversions

import `in`.specmatic.GENERATION
import `in`.specmatic.core.*
import `in`.specmatic.core.pattern.ContractException
import `in`.specmatic.core.value.*
import `in`.specmatic.test.TestExecutor
import org.assertj.core.api.Assertions
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Tag
import org.junit.jupiter.api.Test

@Tag(GENERATION)
class GenerativeTests {
    @Test
    fun `generative tests for enums when an example is provided`() {
        val feature = OpenApiSpecification.fromYAML(
            """
            ---
            openapi: "3.0.1"
            info:
              title: "Person API"
              version: "1"
            paths:
              /person:
                post:
                  summary: Create person record
                  requestBody:
                    content:
                      application/json:
                        examples:
                          CREATE_PERSON:
                            value:
                              status: "active"
                        schema:
                          required:
                          - status
                          properties:
                            status:
                              type: string
                              enum:
                                - active
                                - inactive
                  responses:
                    200:
                      description: Person record created
                      content:
                        text/plain:
                          schema:
                            type: "string"
                          examples:
                            CREATE_PERSON:
                              value:
                                "Person record created"
            """.trimIndent(), ""
        ).toFeature()

        val statusesSeen = mutableSetOf<String>()

        feature.enableGenerativeTesting().executeTests(object : TestExecutor {
            override fun execute(request: HttpRequest): HttpResponse {
                val body = request.body as JSONObjectValue
                println(body.toStringLiteral())

                when (body.jsonObject["status"]!!) {
                    is StringValue -> statusesSeen.add(body.jsonObject["status"]!!.toStringLiteral())
                    else -> statusesSeen.add(body.jsonObject["status"]!!.displayableType())
                }

                return HttpResponse.OK
            }

            override fun setServerState(serverState: Map<String, Value>) {
            }
        })

        assertThat(statusesSeen).isEqualTo(setOf("active", "inactive", "null", "boolean", "number"))
    }

    @Test
    fun `generative tests for an optional key given a payload example without the optional key`() {
        val feature = OpenApiSpecification.fromYAML(
            """
            ---
            openapi: "3.0.1"
            info:
              title: "Person API"
              version: "1"
            paths:
              /person:
                post:
                  summary: Create person record
                  requestBody:
                    content:
                      application/json:
                        examples:
                          CREATE_PERSON:
                            value:
                              name: "John Doe"
                        schema:
                          required:
                          - name
                          properties:
                            name:
                              type: string
                            description:
                              type: string
                  responses:
                    200:
                      description: Person record created
                      content:
                        text/plain:
                          schema:
                            type: "string"
                          examples:
                            CREATE_PERSON:
                              value:
                                "Person record created"
            """.trimIndent(), ""
        ).toFeature()

        val fromExample = 1
        val positiveGenerated = 1
        val negativeGenerativeAll = 3 + 3
        val negativeGenerativeNothing = 3


        try {
            val results = runGenerativeTests(feature)

            val expectedCountOfTests =
                fromExample + positiveGenerated + negativeGenerativeAll + negativeGenerativeNothing

            assertThat(results.results).hasSize(expectedCountOfTests)
        } catch (e: ContractException) {
            println(e.report())

            throw e
        }
    }

    @Test
    fun `generative tests for headers`() {
        val feature = OpenApiSpecification.fromYAML(
            """
            ---
            openapi: "3.0.1"
            info:
              title: "Person API"
              version: "1"
            paths:
              /person:
                post:
                  summary: Create person record
                  parameters:
                    - name: X-Request-Id
                      in: header
                      required: true
                      schema:
                        type: integer
                      examples:
                        CREATE_PERSON:
                          value: 987
                  requestBody:
                    content:
                      application/json:
                        examples:
                          CREATE_PERSON:
                            value:
                              name: "John Doe"
                        schema:
                          required:
                          - name
                          properties:
                            name:
                              type: string
                  responses:
                    200:
                      description: Person record created
                      content:
                        text/plain:
                          schema:
                            type: object
                            required:
                              - id
                            properties:
                              id:
                                type: integer
                          examples:
                            CREATE_PERSON:
                              value:
                                id: 123
            """.trimIndent(), ""
        ).toFeature()

        try {
            val results = runGenerativeTests(feature)
            assertThat(results.results).hasSize(6)
        } catch (e: ContractException) {
            println(e.report())
            throw e
        }
    }

    @Test
    fun `generative tests for headers that are optional`() {
        val feature = OpenApiSpecification.fromYAML(
            """
            ---
            openapi: "3.0.1"
            info:
              title: "Person API"
              version: "1"
            paths:
              /person:
                post:
                  summary: Create person record
                  parameters:
                    - name: X-Request-Id
                      in: header
                      schema:
                        type: integer
                      examples:
                        CREATE_PERSON:
                          value: 987
                  requestBody:
                    content:
                      application/json:
                        examples:
                          CREATE_PERSON:
                            value:
                              name: "John Doe"
                        schema:
                          required:
                          - name
                          properties:
                            name:
                              type: string
                  responses:
                    200:
                      description: Person record created
                      content:
                        text/plain:
                          schema:
                            type: object
                            required:
                              - id
                            properties:
                              id:
                                type: integer
                          examples:
                            CREATE_PERSON:
                              value:
                                id: 123
            """.trimIndent(), ""
        ).toFeature()

        try {
            val results = runGenerativeTests(feature)
            assertThat(results.results).hasSize(7)
        } catch (e: ContractException) {
            println(e.report())
            throw e
        }
    }

    @Test
    fun `handle request payloads with the same key in header and request payload with different types`() {
        val feature = OpenApiSpecification.fromYAML(
            """
            openapi: 3.0.0
            info:
              title: Result
              version: 1.0.0
            paths:
              /result:
                post:
                  parameters:
                    - name: status
                      in: header
                      schema:
                        type: boolean
                      examples:
                        SUCCESS:
                          value: true
                  requestBody:
                    content:
                      application/json:
                        examples:
                          SUCCESS:
                            value:
                              status: success
                        schema:
                          type: object
                          required:
                            - status
                          properties:
                            status:
                              type: string
                  responses:
                    '200':
                      description: OK
                      content:
                        text/plain:
                          schema:
                            type: string
                          examples:
                            SUCCESS:
                              value: OK
        """.trimIndent(), ""
        ).toFeature()

        val statusValuesSeen = mutableSetOf<String>()

        val results = feature.enableGenerativeTesting(onlyPositive = true).executeTests(object : TestExecutor {
            override fun execute(request: HttpRequest): HttpResponse {
                val body = request.body as JSONObjectValue
                println(body.toStringLiteral())
                statusValuesSeen.add(body.findFirstChildByPath("status")!!.toStringLiteral() + " in body")
                statusValuesSeen.add(request.headers["status"]?.let { "$it in headers" } ?: "status not in headers")

                return HttpResponse.ok("OK")
            }

            override fun setServerState(serverState: Map<String, Value>) {
            }
        })

        if (results.failureCount > 0)
            println(results.report())

        assertThat(results.failureCount).isEqualTo(0)
        assertThat(results.successCount).isGreaterThan(0)

        assertThat(statusValuesSeen).containsExactlyInAnyOrder(
            "true in headers",
            "status not in headers",
            "success in body"
        )
    }

    @Test
    fun `generative tests should correctly generate values of the right type where the same key appears with different types in the request payload`() {
        val feature = OpenApiSpecification.fromYAML(
            """
            openapi: 3.0.0
            info:
              title: Addresses
              version: 1.0.0
            paths:
              /addresses:
                post:
                  requestBody:
                    content:
                      application/json:
                        examples:
                          SUCCESS:
                            value:
                              person:
                                address:
                                  building: 1
                              company:
                                address:
                                  building: "Bldg no 1"
                        schema:
                          type: object
                          required:
                            - person
                            - company
                          properties:
                            person:
                              type: object
                              required:
                                - address
                              properties:
                                address:
                                  type: object
                                  required:
                                    - building
                                  properties:
                                    building:
                                      type: integer
                            company:
                              type: object
                              required:
                                - address
                              properties:
                                address:
                                  type: object
                                  required:
                                    - building
                                  properties:
                                    building:
                                      type: string
                  responses:
                    '200':
                      description: OK
                      content:
                        text/plain:
                          schema:
                            type: string
                          examples:
                            SUCCESS:
                              value: OK
                    '400':
                      description: Bad Request
                      content:
                        text/plain:
                          schema:
                            type: string
        """.trimIndent(), ""
        ).toFeature()

        val buildingValuesSeen = mutableSetOf<String>()

        try {
            feature.enableGenerativeTesting().executeTests(object : TestExecutor {
                override fun execute(request: HttpRequest): HttpResponse {
                    val body = request.body as JSONObjectValue
                    when (val personBuilding = body.findFirstChildByPath("person.address.building")!!) {
                        is NullValue -> buildingValuesSeen.add("null in person.address.building")
                        is NumberValue -> buildingValuesSeen.add("Value ${personBuilding.toStringLiteral()} in person.address.building")
                        else -> buildingValuesSeen.add("Value type ${personBuilding.displayableType()} in person.address.building")
                    }

                    when (val companyBuilding = body.findFirstChildByPath("company.address.building")!!) {
                        is NullValue -> buildingValuesSeen.add("null in company.address.building")
                        is StringValue -> buildingValuesSeen.add("Value ${companyBuilding.toStringLiteral()} in company.address.building")
                        else -> buildingValuesSeen.add("Value type ${companyBuilding.displayableType()} in company.address.building")
                    }

                    if (body.findFirstChildByPath("person.address.building")!! !is NumberValue ||
                        body.findFirstChildByPath("company.address.building")!! !is StringValue
                    )
                        return HttpResponse.ERROR_400

                    return HttpResponse.ok("OK")
                }

                override fun setServerState(serverState: Map<String, Value>) {
                }
            })

            assertThat(buildingValuesSeen).containsExactlyInAnyOrder(
                "Value 1 in person.address.building",
                "null in person.address.building",
                "Value type boolean in person.address.building",
                "Value type string in person.address.building",
                "Value Bldg no 1 in company.address.building",
                "null in company.address.building",
                "Value type number in company.address.building",
                "Value type boolean in company.address.building"
            )
        } catch (e: ContractException) {
            Assertions.fail("Should not have got this error:\n${e.report()}")
        }
    }


    @Test
    fun `generative positive-only tests with REQUEST-BODY example`() {
        val specification = OpenApiSpecification.fromYAML(
            """
            openapi: "3.0.1"
            info:
              title: "Person API"
              version: "1"
            paths:
              /person:
                post:
                  summary: "Get person by id"
                  parameters: []
                  requestBody:
                    content:
                      application/json:
                        examples:
                          SUCCESS:
                            value:
                              address:
                                - street: "1"

                        schema:
                          required:
                          - "address"
                          properties:
                            address:
                              type: "array"
                              items:
                                ${'$'}ref: "#/components/schemas/Address"
                  responses:
                    200:
                      description: "Get person by id"
                      content:
                        text/plain:
                          schema:
                            type: "string"
                          examples:
                            SUCCESS:
                              value: success
            components:
              schemas:
                Address:
                  properties:
                    street:
                      type: "string"
        """, ""
        ).toFeature()

        val requestBodiesSeen = mutableListOf<Value>()

        val results = specification.enableGenerativeTesting().executeTests(object : TestExecutor {
            override fun execute(request: HttpRequest): HttpResponse {
                println(request.body)
                requestBodiesSeen.add(request.body)
                return HttpResponse.ok("success")
            }

            override fun setServerState(serverState: Map<String, Value>) {
            }
        })
        println(results.report())

        assertThat(requestBodiesSeen).hasSize(3)
    }

    @Test
    fun `specification with 2 levels of depth having inline examples and only one of the keys optional`() {
        val feature = OpenApiSpecification.fromYAML(
            """
        openapi: 3.0.0
        info:
          title: Product API
          version: 1.0.0
        paths:
          /products:
            post:
              summary: Create a new product
              requestBody:
                content:
                  application/json:
                    schema:
                      type: object
                      required:
                        - productDetails
                      properties:
                        productDetails:
                          type: object
                          required:
                            - productId
                          properties:
                            productId:
                              type: string
                              example: 'product123'
                        discountCoupons:
                          type: array
                          items:
                            type: string
                            example: 'coupon890'
              responses:
                '200':
                  description: Product created successfully
                  content:
                    text/plain:
                      schema:
                        type: string
                '400':
                  description: Invalid request payload
                '500':
                  description: Internal server error
    """.trimIndent(), ""
        ).toFeature()

        val seenRequestBodies = mutableListOf<Value>()

        val updatedFeature = feature.enableGenerativeTesting().enableSchemaExampleDefault()

        val results = updatedFeature.executeTests(object : TestExecutor {
            override fun execute(request: HttpRequest): HttpResponse {
                println(request.body)
                seenRequestBodies.add(request.body)

                return HttpResponse.ok("success")
            }

            override fun setServerState(serverState: Map<String, Value>) {
            }
        })

        assertThat(results.results).hasSize(9)
    }

    private fun runGenerativeTests(
        feature: Feature
    ): Results {
        try {
            return feature.enableGenerativeTesting().executeTests(object : TestExecutor {
                override fun execute(request: HttpRequest): HttpResponse {
                    println(request.toLogString())
                    return HttpResponse.OK
                }

                override fun setServerState(serverState: Map<String, Value>) {
                }
            })
        } finally {
            System.clearProperty(Flags.ONLY_POSITIVE)
        }
    }

    @Test
    fun `the flag SPECMATIC_GENERATIVE_TESTS should be used`() {
        try {
            System.setProperty(Flags.SPECMATIC_GENERATIVE_TESTS, "true")

            val feature = OpenApiSpecification.fromYAML(
                """
                openapi: 3.0.0
                info:
                  version: 1.0.0
                  title: Product API
                  description: API for creating a product
                paths:
                  /products:
                    post:
                      summary: Create a product
                      requestBody:
                        required: true
                        content:
                          application/json:
                            schema:
                              ${"$"}ref: '#/components/schemas/Product'
                      responses:
                        '200':
                          description: Product created successfully
                          content:
                            text/plain:
                              schema:
                                type: string
                        '400':
                          description: Bad request
                          content:
                            text/plain:
                              schema:
                                type: string
                components:
                  schemas:
                    Product:
                      type: object
                      required:
                        - name
                      properties:
                        name:
                          type: string
                          description: The name of the product
                          example: 'Soap'
                    """, ""
            ).toFeature()

            val testType = mutableListOf<String>()

            val results = feature.executeTests(object : TestExecutor {
                override fun execute(request: HttpRequest): HttpResponse {
                    val body = request.body as JSONObjectValue

                    if (body.jsonObject["name"] !is StringValue) {
                        testType.add("name mutated to " + body.jsonObject["name"]!!.displayableType())
                        return HttpResponse.ERROR_400
                    }

                    testType.add("name not mutated")

                    return HttpResponse.OK
                }

                override fun setServerState(serverState: Map<String, Value>) {

                }
            })

            assertThat(testType).containsExactlyInAnyOrder(
                "name not mutated",
                "name mutated to null",
                "name mutated to boolean",
                "name mutated to number"
            )

            assertThat(results.failureCount).isEqualTo(0)

            assertThat(results.results).hasSize(testType.size)
        } finally {
            System.clearProperty(Flags.SPECMATIC_GENERATIVE_TESTS)
        }
    }

    @Test
    fun `the flag ONLY_POSITIVE should be used`() {
        try {
            System.setProperty(Flags.SPECMATIC_GENERATIVE_TESTS, "true")
            System.setProperty(Flags.ONLY_POSITIVE, "true")

            val feature = OpenApiSpecification.fromYAML(
                """
                openapi: 3.0.0
                info:
                  version: 1.0.0
                  title: Product API
                  description: API for creating a product
                paths:
                  /products:
                    post:
                      summary: Create a product
                      requestBody:
                        required: true
                        content:
                          application/json:
                            schema:
                              ${"$"}ref: '#/components/schemas/Product'
                            examples:
                              SUCCESS:
                                value:
                                  name: 'Soap'
                      responses:
                        '200':
                          description: Product created successfully
                          content:
                            text/plain:
                              schema:
                                type: string
                              examples:
                                SUCCESS:
                                  value: 'Product created successfully'
                components:
                  schemas:
                    Product:
                      type: object
                      required:
                        - name
                      properties:
                        name:
                          type: string
                          description: The name of the product
                          example: 'Soap'
                        price:
                          type: number
                          description: The price of the product
                    """, ""
            ).toFeature()

            val testType = mutableListOf<String>()

            val results = feature.executeTests(object : TestExecutor {
                override fun execute(request: HttpRequest): HttpResponse {
                    val body = request.body as JSONObjectValue

                    if (body.jsonObject["name"] !is StringValue) {
                        testType.add("name mutated to " + body.jsonObject["name"]!!.displayableType())
                        return HttpResponse.ERROR_400
                    }

                    if ("price" in body.jsonObject && body.jsonObject["price"] !is NumberValue) {
                        testType.add("price mutated to " + body.jsonObject["price"]!!.displayableType())
                        return HttpResponse.ERROR_400
                    }

                    if ("price" in body.jsonObject)
                        testType.add("price is present")
                    else
                        testType.add("price is absent")

                    return HttpResponse.OK
                }

                override fun setServerState(serverState: Map<String, Value>) {

                }
            })

            assertThat(testType).containsExactlyInAnyOrder(
                "price is present", "price is absent"
            )

            assertThat(results.results).hasSize(testType.size)

            assertThat(results.failureCount).isEqualTo(0)

        } finally {
            System.clearProperty(Flags.SPECMATIC_GENERATIVE_TESTS)
            System.clearProperty(Flags.ONLY_POSITIVE)
        }
    }

    @Test
    fun `generative tests when the example goes 2 levels deep`() {
        val feature = OpenApiSpecification.fromYAML(
            """
            ---
            openapi: "3.0.1"
            info:
              title: "Person API"
              version: "1"
            paths:
              /person:
                post:
                  summary: Create person record
                  requestBody:
                    content:
                      application/json:
                        examples:
                          CREATE_PERSON:
                            value:
                              name: "John Doe"
                              address:
                                building:
                                  flat: 10
                                  name: "Mason Apartments"
                                street: "1st Street"
                        schema:
                          required:
                          - name
                          - address
                          properties:
                            name:
                              type: string
                            address:
                              type: object
                              properties:
                                building:
                                  type: object
                                  properties:
                                    flat:
                                      type: integer
                                    name:
                                      type: string
                                street:
                                  type: string
                                propertyType:
                                  type: string
                                  enum:
                                    - residential
                                    - commercial
                  responses:
                    200:
                      description: Person record created
                      content:
                        text/plain:
                          schema:
                            type: "string"
                          examples:
                            CREATE_PERSON:
                              value:
                                "Person record created"
                    400:
                      description: Bad Request
                      content:
                        text/plain:
                          schema:
                            type: "string"
            """.trimIndent(), ""
        ).toFeature()

        val notes = mutableListOf<String>()

        val results = feature.enableGenerativeTesting().executeTests(object : TestExecutor {
            override fun execute(request: HttpRequest): HttpResponse {
                val body = request.body as JSONObjectValue
                println(body.toStringLiteral())

                body.findFirstChildByPath("name")?.let {
                    if (it !is StringValue) {
                        notes.add("name mutated to ${it.displayableType()}")
                        return HttpResponse.ERROR_400
                    }

                    assertThat(it).isEqualTo(StringValue("John Doe"))
                }

                body.findFirstChildByPath("address.building.name")?.let {
                    if (it !is StringValue) {
                        notes.add("address.building.name mutated to ${it.displayableType()}")
                        return HttpResponse.ERROR_400
                    }

                    assertThat(it).isEqualTo(StringValue("Mason Apartments"))
                }

                body.findFirstChildByPath("address.building.flat")?.let {
                    if (it !is NumberValue) {
                        notes.add("address.building.flat mutated to ${it.displayableType()}")
                        return HttpResponse.ERROR_400
                    }

                    assertThat(it).isEqualTo(NumberValue(10))
                }

                body.findFirstChildByPath("address.street")?.let {
                    if (it !is StringValue) {
                        notes.add("address.street mutated to ${it.displayableType()}")
                        return HttpResponse.ERROR_400
                    }

                    assertThat(it).isEqualTo(StringValue("1st Street"))
                }

                body.findFirstChildByPath("address.propertyType")?.let {
                    if (it == StringValue("residential") || it == StringValue("commercial")) {
                        notes.add("address.propertyType is ${it.toStringLiteral()}")
                    } else {
                        notes.add("address.propertyType mutated to ${it.displayableType()}")
                        return HttpResponse.ERROR_400
                    }
                }

                body.findFirstChildByPath("address")?.let {
                    it as JSONObjectValue

                    if (it.jsonObject.isEmpty()) {
                        notes.add("address object is empty")
                    }
                }

                body.findFirstChildByPath("building")?.let {
                    it as JSONObjectValue

                    if (it.jsonObject.isEmpty()) {
                        notes.add("building object is empty")
                    }
                }

                notes.add("request matches the specification")

                return HttpResponse.OK
            }

            override fun setServerState(serverState: Map<String, Value>) {
            }
        })

        if (results.failureCount > 0)
            println(results.report())

        assertThat(notes.sorted()).isEqualTo(
            listOf(
                "address object is empty",
                "address.building.flat mutated to boolean",
                "address.building.flat mutated to boolean",
                "address.building.flat mutated to null",
                "address.building.flat mutated to null",
                "address.building.flat mutated to string",
                "address.building.flat mutated to string",
                "address.building.name mutated to boolean",
                "address.building.name mutated to boolean",
                "address.building.name mutated to null",
                "address.building.name mutated to null",
                "address.building.name mutated to number",
                "address.building.name mutated to number",
                "address.propertyType is commercial",
                "address.propertyType is commercial",
                "address.propertyType is residential",
                "address.propertyType is residential",
                "address.propertyType mutated to boolean",
                "address.propertyType mutated to boolean",
                "address.propertyType mutated to null",
                "address.propertyType mutated to null",
                "address.propertyType mutated to number",
                "address.propertyType mutated to number",
                "address.street mutated to boolean",
                "address.street mutated to boolean",
                "address.street mutated to boolean",
                "address.street mutated to boolean",
                "address.street mutated to null",
                "address.street mutated to null",
                "address.street mutated to null",
                "address.street mutated to null",
                "address.street mutated to number",
                "address.street mutated to number",
                "address.street mutated to number",
                "address.street mutated to number",
                "name mutated to boolean",
                "name mutated to boolean",
                "name mutated to boolean",
                "name mutated to boolean",
                "name mutated to boolean",
                "name mutated to null",
                "name mutated to null",
                "name mutated to null",
                "name mutated to null",
                "name mutated to null",
                "name mutated to number",
                "name mutated to number",
                "name mutated to number",
                "name mutated to number",
                "name mutated to number",
                "request matches the specification",
                "request matches the specification",
                "request matches the specification",
                "request matches the specification",
                "request matches the specification",
                "request matches the specification"
            ).sorted()
        )
    }

    @Test
    fun `generative tests for path params`() {
        val feature = OpenApiSpecification.fromYAML(
            """
            ---
            openapi: "3.0.1"
            info:
              title: "Person API"
              version: "1"
            paths:
              /person/{id}:
                parameters:
                  - name: id
                    in: path
                    schema:
                      type: integer
                    examples:
                      CREATE_PERSON:
                        value: 100
                post:
                  summary: Create person record
                  responses:
                    200:
                      description: Person record created
                      content:
                        text/plain:
                          schema:
                            type: "string"
                          examples:
                            CREATE_PERSON:
                              value: "Person record created"
            """.trimIndent(), ""
        ).toFeature()

        val pathsSeen = mutableListOf<String>()

        val results = feature.enableGenerativeTesting().executeTests(object : TestExecutor {
            override fun execute(request: HttpRequest): HttpResponse {
                pathsSeen.add(request.path!!)

                return HttpResponse.OK
            }

            override fun setServerState(serverState: Map<String, Value>) {
            }
        })

        assertThat(pathsSeen).withFailMessage(results.report()).satisfiesExactlyInAnyOrder(
            { assertThat(it).isEqualTo("/person/100") },
            { assertThat(it).matches("^/person/(false|true)") },
            { assertThat(it).matches("/person/[A-Z]+$") }
        )
    }
}
