package io.specmatic.core

import io.specmatic.core.pattern.ContractException
import io.specmatic.core.value.JSONObjectValue
import io.specmatic.core.value.Value

class Workflow(
    val workflow: WorkflowConfiguration = WorkflowConfiguration(),
) {
    var id: Value? = null

    fun extractDataFrom(request: HttpRequest, response: HttpResponse, originalScenario: Scenario) {
        val operation = workflow.ids.get(originalScenario.apiDescription)

        if(operation == null)
            return

        val extractLocation = operation.extract

        if(extractLocation != null) {
            val locationPath = extractLocation.split(".")
            if(locationPath.size == 0)
                return

            val area = locationPath[0]

            val path = locationPath.drop(1)

            when(area.uppercase()) {
                "BODY" -> {
                    val responseBody = response.body

                    if(path.size == 0) {
                        id = responseBody
                    } else if(responseBody is JSONObjectValue) {
                        val data = responseBody.findFirstChildByPath(path.joinToString("."))
                        if(data != null)
                            id = data
                    }
                }
                else -> {
                    throw ContractException("Cannot extract data from $area yet")
                }
            }

        }
    }

    fun updateRequest(request: HttpRequest, originalScenario: Scenario): HttpRequest {
        val operation = workflow.ids.get(originalScenario.apiDescription) ?: workflow.ids.get("*")

        if(operation == null)
            return request

        val useLocation = operation.use

        if(useLocation == null)
            return request

        val locationPath = useLocation.split(".")
        if(locationPath.size == 0)
            return request

        val area = locationPath[0]

        val path = locationPath.drop(1)

        return when(area.uppercase()) {
            "PATH" -> {
                if(path.size == 0)
                    throw ContractException("Cannot use id $useLocation")

                if(path.size > 1)
                    throw ContractException("PATH.<name> must refer to the name of a path parameter")

                val pathParamName = path.get(0)

                val pathParamIndex = originalScenario.httpRequestPattern.httpPathPattern?.pathSegmentPatterns?.indexOfFirst { it.key == pathParamName } ?: -1

                if(pathParamIndex < 0) {
                    request
                }
                else {
                    val updatedPath = request.path!!.split("/").toMutableList()

                    val indexToUpdate = if(updatedPath.getOrNull(0) == "") pathParamIndex + 1 else pathParamIndex

                    id?.let {
                        updatedPath.set(indexToUpdate, it.toStringLiteral())
                    }

                    request.copy(path = updatedPath.joinToString("/"))
                }
            }
            else -> {
                throw ContractException("Cannot extract data from $area yet")
            }
        }
    }
}