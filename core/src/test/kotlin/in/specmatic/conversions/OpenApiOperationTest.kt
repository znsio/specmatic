package `in`.specmatic.conversions

import io.swagger.v3.oas.models.Operation
import io.swagger.v3.oas.models.media.StringSchema
import io.swagger.v3.oas.models.parameters.Parameter
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Test
import `in`.specmatic.core.pattern.ContractException
import io.swagger.v3.oas.models.media.ArraySchema
import org.junit.jupiter.api.assertDoesNotThrow

class OpenApiOperationTest {
    @Test
    fun `validates a valid parameter without throwing an exception`() {
        val operation = Operation()
        operation.parameters = listOf(
            Parameter().apply {
                name = "data"
                schema = StringSchema()
            }
        )
        val openApiOperation = OpenApiOperation(operation)
        assertDoesNotThrow { openApiOperation.validateParameters() }
    }

    @Test
    fun `validates a valid array parameter without throwing an exception`() {
        val operation = Operation()
        operation.parameters = listOf(
            Parameter().apply {
                name = "data"
                schema = ArraySchema().apply {
                    this.items = StringSchema()
                }
            }
        )

        val openApiOperation = OpenApiOperation(operation)
        assertDoesNotThrow { openApiOperation.validateParameters() }
    }

    @Test
    fun `throws exception when parameter name is missing`() {
        val operation = Operation()
        operation.parameters = listOf(
            Parameter().apply {
                schema = StringSchema()
            }
        )
        val openApiOperation = OpenApiOperation(operation)
        assertThrows(ContractException::class.java) { openApiOperation.validateParameters() }
    }

    @Test
    fun `throws exception when parameter schema is missing`() {
        val operation = Operation()
        operation.parameters = listOf(
            Parameter().apply {
                name = "name"
            }
        )
        val openApiOperation = OpenApiOperation(operation)
        assertThrows(ContractException::class.java) { openApiOperation.validateParameters() }
    }

    @Test
    fun `throws exception when parameter type is array but items are not defined`() {
        val operation = Operation()
        operation.parameters = listOf(
            Parameter().apply {
                name = "name"
                schema = StringSchema().apply {
                    type = "array"
                }
            }
        )
        val openApiOperation = OpenApiOperation(operation)
        assertThrows(ContractException::class.java) { openApiOperation.validateParameters() }
    }
}