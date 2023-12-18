package `in`.specmatic.conversions

import io.swagger.v3.oas.models.OpenAPI

class OpenApiSpecificationInfo(private val openApiFilePath: String, private val parsedOpenApi: OpenAPI) {
    override fun toString(): String {
        val info = StringBuilder()

        info.append("========================================\n")
        info.append("Loaded OpenAPI File: $openApiFilePath\n")
        info.append("OpenAPI Version: ${parsedOpenApi.openapi}\n")

        parsedOpenApi.info?.let {
            info.append("API Info:\n")
            info.append("  Title: ${it.title}\n")
            info.append("  Version: ${it.version}\n")
            info.append("  Description: ${it.description}\n")
        }

        parsedOpenApi.paths?.let {
            info.append("No of API Paths: ${it.size}\n")
            it.map { (_, pathItem) ->
                pathItem.readOperationsMap().map { (_, operation) ->
                    operation.operationId
                }
            }.flatten().toList().let { info.append("No of API Operations: ${it.size}\n") }
        }

        parsedOpenApi.components?.let {
            info.append("API Components:\n")
            info.append("  Schemas: ${it.schemas?.keys}\n")
            info.append("  Responses: ${it.responses?.keys}\n")
            info.append("  Parameters: ${it.parameters?.keys}\n")
            info.append("  Examples: ${it.examples?.keys}\n")
            info.append("  Request Bodies: ${it.requestBodies?.keys}\n")
            info.append("  Headers: ${it.headers?.keys}\n")
            info.append("  Security Schemes: ${it.securitySchemes?.keys}\n")
            info.append("  Links: ${it.links?.keys}\n")
            info.append("  Callbacks: ${it.callbacks?.keys}\n")
        }

        info.append("========================================\n")

        return info.toString()
    }
}