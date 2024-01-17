package reports

import `in`.specmatic.reports.CentralContractRepoReport
import `in`.specmatic.reports.CentralContractRepoReportJson
import `in`.specmatic.reports.SpecificationOperation
import `in`.specmatic.reports.SpecificationRow
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.AfterAll
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.Test
import java.io.File

class CentralContractRepoReportTest {

    @Test
    fun `test generates report based on all the open api specifications present in the specified dir`() {
        val report = CentralContractRepoReport().generate("./specifications")
        assertThat(report).isEqualTo(
            CentralContractRepoReportJson(
                listOf(
                    SpecificationRow(
                        "service1/service1.yaml",
                        "HTTP",
                        listOf(
                            SpecificationOperation(
                                "/hello/{id}",
                                "GET",
                                200
                            ),
                            SpecificationOperation(
                                "/hello/{id}",
                                "GET",
                                404
                            ),
                            SpecificationOperation(
                                "/hello/{id}",
                                "GET",
                                400
                            )
                        )
                    ),
                    SpecificationRow(
                        "service2/service2.yaml",
                        "HTTP",
                        listOf(
                            SpecificationOperation(
                                "/products/{id}",
                                "GET",
                                200
                            )
                        )
                    ),
                )
            )
        )
    }

    companion object {

        @JvmStatic
        @BeforeAll
        fun setupBeforeAll() {
            createSpecFiles()
        }

        @JvmStatic
        @AfterAll
        fun tearDownAfterAll() {
            File("./specifications").deleteRecursively()
        }

        private fun createSpecFiles() {
            val service1spec = """
openapi: 3.0.0
info:
  title: Sample API
  description: Optional multiline or single-line description in [CommonMark](http://commonmark.org/help/) or HTML.
  version: 0.1.9
servers:
  - url: http://api.example.com/v1
    description: Optional server description, e.g. Main (production) server
  - url: http://staging-api.example.com
    description: Optional server description, e.g. Internal staging server for testing
paths:
  /hello/{id}:
    get:
      summary: hello world
      description: Optional extended description in CommonMark or HTML.
      parameters:
        - in: path
          name: id
          schema:
            type: integer
          required: true
          description: Numeric ID
      responses:
        '200':
          description: Says hello
          content:
            application/json:
              schema:
                type: string
        '404':
          description: Not Found
          content:
            application/json:
              schema:
                type: string
        '400':
          description: Bad Request
          content:
            application/json:
              schema:
                type: string
        """
            val service1File = File("./specifications/service1/service1.yaml")
            service1File.parentFile.mkdirs()
            service1File.writeText(service1spec)


            val service2spec= """
openapi: 3.0.0
info:
  title: Order API
  version: '1.0'
servers:
  - url: 'http://localhost:3000'
paths:
  '/products/{id}':
    parameters:
      - schema:
          type: number
        name: id
        in: path
        required: true
        examples:
          GET_DETAILS_10:
            value: 10
          GET_DETAILS_20:
            value: 20
    get:
      summary: Fetch product details
      tags: []
      responses:
        '200':
          description: OK
          content:
            application/json:
              schema:
                ${'$'}ref: './common.yaml#/components/schemas/Product'
              examples:
                GET_DETAILS_10:
                  value:
                    name: 'XYZ Phone'
                    type: 'gadget'
                    inventory: 10
                    id: 10
                GET_DETAILS_20:
                  value:
                     name: 'Macbook'
                     type: 'gadget'
                     inventory: 10
                     id: 20
            """.trimIndent()

            val service2File = File("./specifications/service2/service2.yaml")
            service2File.parentFile.mkdirs()
            service2File.writeText(service2spec)

            val commonSpec = """
            openapi: 3.0.0
            info:
              title: Common schema
              version: '1.0'
            paths: {}
            components:
              schemas:
                ProductDetails:
                  title: Product Details
                  type: object
                  properties:
                    name:
                      type: string
                    type:
                      ${'$'}ref: '#/components/schemas/ProductType'
                    inventory:
                      type: integer
                  required:
                    - name
                    - type
                    - inventory
                ProductType:
                  type: string
                  title: Product Type
                  enum:
                    - book
                    - food
                    - gadget
                    - other
                ProductId:
                  title: Product Id
                  type: object
                  properties:
                    id:
                      type: integer
                  required:
                    - id
                Product:
                  title: Product
                  allOf:
                    - ${'$'}ref: '#/components/schemas/ProductId'
                    - ${'$'}ref: '#/components/schemas/ProductDetails'
        """.trimIndent()

            val commonFile = File("./specifications/service2/common.yaml")
            commonFile.writeText(commonSpec)

        }
    }
}