package `in`.specmatic.conversions

import io.swagger.v3.oas.models.OpenAPI

class OpenApiSpecificationInfo(private val openApiFilePath: String, private val parsedOpenApi: OpenAPI) {
    override fun toString(): String {
        val info = StringBuilder()

        info.append("API Specification Summary: $openApiFilePath\n")
        info.append("  OpenAPI Version: ${parsedOpenApi.openapi}\n")

        parsedOpenApi.paths?.let {
            val operationsCount = it.map { (_, pathItem) ->
                pathItem.readOperationsMap().map { (_, operation) -> operation.operationId }
            }.flatten().toList().size
            info.append("  API Paths: ${it.size}, API Operations: $operationsCount\n")
        }

        parsedOpenApi.components?.let {
            info.append("  Schema components: ${it.schemas?.size}, Security Schemes: ${it.securitySchemes?.keys}\n")
        }

        return info.toString()
    }
}