package application

import `in`.specmatic.conversions.OpenApiSpecification
import `in`.specmatic.core.git.GitCommand
import io.mockk.every
import io.mockk.mockk
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import java.io.File

internal class ReDeclaredAPICommandTest {
    val oldContractYaml = """
            openapi: "3.0.0"
            info:
              version: 1.0.0
              title: Swagger Petstore
              description: A sample API that uses a petstore as an example to demonstrate features in the OpenAPI 3.0 specification
              termsOfService: http://swagger.io/terms/
              contact:
                name: Swagger API Team
                email: apiteam@swagger.io
                url: http://swagger.io
              license:
                name: Apache 2.0
                url: https://www.apache.org/licenses/LICENSE-2.0.html
            servers:
              - url: http://petstore.swagger.io/api
            paths:
              /pets:
                post:
                  description: Creates a new pet in the store. Duplicates are allowed
                  operationId: addPet
                  requestBody:
                    description: Pet to add to the store
                    required: true
                    content:
                      application/json:
                        schema:
                          ${'$'}ref: '#/components/schemas/NewPet'
                  responses:
                    '201':
                      description: pet response
                      content:
                        text/plain:
                          schema:
                            type: string
            components:
              schemas:
                NewPet:
                  type: object
                  required:
                    - name
                    - tag
                  properties:
                    name:
                      type: string
                      nullable: false
                    tag:
                      type: string
                      nullable: false
                    optional:
                      type: string
                      nullable: true
        """.trimIndent()

    val newContractYaml = """
            openapi: "3.0.0"
            info:
              version: 1.0.0
              title: Swagger Petstore
              description: A sample API that uses a petstore as an example to demonstrate features in the OpenAPI 3.0 specification
              termsOfService: http://swagger.io/terms/
              contact:
                name: Swagger API Team
                email: apiteam@swagger.io
                url: http://swagger.io
              license:
                name: Apache 2.0
                url: https://www.apache.org/licenses/LICENSE-2.0.html
            servers:
              - url: http://petstore.swagger.io/api
            paths:
              /pet/{id}:
                get:
                  description: Get pet details
                  operationId: getPetDetails
                  parameters:
                    - in: path
                      name: id
                      schema:
                        type: integer
                      required: true
                      description: Numeric ID
                  responses:
                    '201':
                      description: pet response
                      content:
                        application/json:
                          schema:
                            ${'$'}ref: '#/components/schemas/NewPet'
              /pets:
                post:
                  description: Creates a new pet in the store. Duplicates are allowed
                  operationId: addPet
                  requestBody:
                    description: Pet to add to the store
                    required: true
                    content:
                      application/json:
                        schema:
                          ${'$'}ref: '#/components/schemas/NewPet'
                  responses:
                    '201':
                      description: pet response
                      content:
                        text/plain:
                          schema:
                            type: string
            components:
              schemas:
                NewPet:
                  type: object
                  required:
                    - name
                    - tag
                  properties:
                    name:
                      type: string
                      nullable: false
                    tag:
                      type: string
                      nullable: false
                    optional:
                      type: string
                      nullable: true
        """.trimIndent()

    @Nested
    inner class GetNewPaths {
        @Test
        fun `from uncommitted file`() {
            val contractFile = mockk<CanonicalFile>()
            every { contractFile.readText() } returns newContractYaml
            every { contractFile.relativeTo(any()) } returns File(".")

            val git = mockk<GitCommand>()
            every { git.gitRoot() } returns "/git/root"
            every { git.exists("HEAD", any()) } returns true
            every { git.show("HEAD", any()) } returns oldContractYaml

            val contractToCheck = ContractToCheck(contractFile, git)

            val newPaths = contractToCheck.getNewPathsInContract("HEAD", "")
            assertThat(newPaths).hasSize(1)
            assertThat(newPaths).contains("/pet/(id:number)")
        }
    }

    @Test
    fun `should identify redeclared contracts`() {
        val contractToCheck = mockk<ContractToCheck>()
        every { contractToCheck.getNewPathsInContract("v1", "v2") } returns listOf("/pets")
        every { contractToCheck.fetchAllOtherContracts() } returns listOf(Pair(OpenApiSpecification.fromYAML(oldContractYaml, "/contract.yaml").toFeature(), "/contract.yaml"))

        val redeclaredContracts = findRedeclaredContracts(contractToCheck, "v1", "v2")

        assertThat(redeclaredContracts).hasSize(1)
        assertThat(redeclaredContracts.single().apiURLPath).isEqualTo("/pets")

        assertThat(redeclaredContracts.single().contractsContainingAPI).hasSize(1)
        assertThat(redeclaredContracts.single().contractsContainingAPI.single()).isEqualTo("/contract.yaml")
    }
}
