package `in`.specmatic.conversions

import io.swagger.v3.oas.models.OpenAPI

class OpenApiSpecificationInfo(private val openApiFilePath: String, private val parsedOpenApi: OpenAPI) {
    override fun toString(): String {
        val info = StringBuilder()

        info.append("========================================\n")
        info.append("Loaded OpenAPI File: $openApiFilePath\n")
        info.append("OpenAPI Version: ${parsedOpenApi.openapi}\n")

        val apiInfo = parsedOpenApi.info
        info.append("API Info:\n")
        info.append("  Title: ${apiInfo.title}\n")
        info.append("  Version: ${apiInfo.version}\n")
        info.append("  Description: ${apiInfo.description}\n")

        val paths = parsedOpenApi.paths
        info.append("No of API Paths: ${paths.size}\n")
        paths.map { (_, pathItem) ->
            pathItem.readOperationsMap().map { (_, operation) ->
                operation.operationId
            }
        }.flatten().toList().let { info.append("No of API Operations: ${it.size}\n") }

        val components = parsedOpenApi.components
        info.append("API Components:\n")
        info.append("  Schemas: ${components.schemas?.keys}\n")
        info.append("  Responses: ${components.responses?.keys}\n")
        info.append("  Parameters: ${components.parameters?.keys}\n")
        info.append("  Examples: ${components.examples?.keys}\n")
        info.append("  Request Bodies: ${components.requestBodies?.keys}\n")
        info.append("  Headers: ${components.headers?.keys}\n")
        info.append("  Security Schemes: ${components.securitySchemes?.keys}\n")
        info.append("  Links: ${components.links?.keys}\n")
        info.append("  Callbacks: ${components.callbacks?.keys}\n")
        info.append("========================================\n")

        return info.toString()
    }
}