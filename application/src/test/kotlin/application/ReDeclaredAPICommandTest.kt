package application

import io.specmatic.conversions.OpenApiSpecification
import io.mockk.every
import io.mockk.mockk
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test

internal class ReDeclaredAPICommandTest {
    private val oldContractYaml = """
            openapi: "3.0.0"
            info:
              version: 1.0.0
              title: Swagger Pet Store
              description: A sample API that uses a Pet Store as an example to demonstrate features in the OpenAPI 3.0 specification
              termsOfService: http://swagger.io/terms/
              contact:
                name: Swagger API Team
                email: apiteam@swagger.io
                url: http://swagger.io
              license:
                name: Apache 2.0
                url: https://www.apache.org/licenses/LICENSE-2.0.html
            servers:
              - url: http://Pet Store.swagger.io/api
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

    private val newContractYaml = """
            openapi: "3.0.0"
            info:
              version: 1.0.0
              title: Swagger Pet Store
              description: A sample API that uses a Pet Store as an example to demonstrate features in the OpenAPI 3.0 specification
              termsOfService: http://swagger.io/terms/
              contact:
                name: Swagger API Team
                email: apiteam@swagger.io
                url: http://swagger.io
              license:
                name: Apache 2.0
                url: https://www.apache.org/licenses/LICENSE-2.0.html
            servers:
              - url: http://Pet Store.swagger.io/api
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

    @Test
    fun `should identify APIs in one contract that have been re-declared in others`() {
        val contractToCheck = mockk<ContractToCheck>()
        every { contractToCheck.getPathsInContract() } returns listOf("/pets")
        every { contractToCheck.fetchAllOtherContracts() } returns listOf(Pair(OpenApiSpecification.fromYAML(oldContractYaml, "/contract.yaml").toFeature(), "/contract.yaml"))

        val reDeclaredContracts = findReDeclaredContracts(contractToCheck)

        assertThat(reDeclaredContracts).hasSize(1)
        assertThat(reDeclaredContracts.single().apiURLPath).isEqualTo("/pets")

        assertThat(reDeclaredContracts.single().contractsContainingAPI).hasSize(1)
        assertThat(reDeclaredContracts.single().contractsContainingAPI.single()).isEqualTo("/contract.yaml")
    }

    @Test
    fun `should identify re-declared APIs across a list of contracts`() {
        val reDeclarations: Map<String, List<String>> = findReDeclarationsAmongstContracts(
            listOf(
                Pair(OpenApiSpecification.fromYAML(oldContractYaml, "/old.yaml").toFeature(), "/old.yaml"),
                Pair(OpenApiSpecification.fromYAML(newContractYaml, "/new.yaml").toFeature(), "/new.yaml"),
            )
        )

        assertThat(reDeclarations).hasSize(1)

        assertThat(reDeclarations).containsKey("/pets")

        val contractsDeclaringPets = reDeclarations.getValue("/pets")

        assertThat(contractsDeclaringPets).hasSize(2)
        assertThat(contractsDeclaringPets).contains("/old.yaml")
        assertThat(contractsDeclaringPets).contains("/new.yaml")
    }
}
