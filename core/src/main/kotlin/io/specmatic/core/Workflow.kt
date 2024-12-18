package io.specmatic.core

import io.specmatic.core.pattern.ContractException
import io.specmatic.core.value.JSONObjectValue
import io.specmatic.core.value.Value

class Workflow(
    val workflow: WorkflowConfiguration = WorkflowConfiguration(),
) {
    companion object {
        fun transformLinksToWorkflow(scenarios: List<Scenario>): Map<String, WorkflowIDOperation> {

            val workflow = LinkedHashMap<String, WorkflowIDOperation>()

            for (scenario in scenarios) {
                if (scenario.links.isNotEmpty()) {
                    processForExtractWorkflow(scenario, workflow, scenarios)
                }
            }

            return workflow.toMap()
        }

        private fun processForExtractWorkflow(
            scenario: Scenario,
            workflow: LinkedHashMap<String, WorkflowIDOperation>,
            scenarios: List<Scenario>
        ) {
            for (link in scenario.links) {
                var workflowIdOperation = workflow[scenario.apiDescription] ?: WorkflowIDOperation()
                workflowIdOperation = link.value.parameters.let {
                    workflowIdOperation.copy(
                        extract = transformLinkRefToExtract(
                            (link.value.parameters as MutableMap).iterator().next().value
                        )
                    )
                }
                workflow[scenario.apiDescription] = workflowIdOperation

                val linkedScenario = scenarios.find { it.operationId == link.value.operationId }
                if (linkedScenario != null) {
                    processForUseWorkflow(
                        scenarios,
                        linkedScenario,
                        workflow,
                        (link.value.parameters as MutableMap).iterator().next().key
                    )
                }
            }
        }

        private fun processForUseWorkflow(scenarios: List<Scenario>, scenario: Scenario, workflow: LinkedHashMap<String, WorkflowIDOperation>, useKey: String) {
            val workflowIdOperation = workflow[scenario.apiDescription] ?: WorkflowIDOperation()

            workflow[scenario.apiDescription] = workflowIdOperation.copy(
                use = useKey
            )

            if (scenario.links.isNotEmpty()) {
                processForExtractWorkflow(scenario, workflow, scenarios)
            }
        }

        private fun transformLinkRefToExtract(input: String): String {
            val parts = input.removePrefix("\$response.").split("#/")
            return parts.joinToString(".")
        }
    }
    var id: Value? = null

    fun extractDataFrom(response: HttpResponse, originalScenario: Scenario) {
        val operation = workflow.ids[originalScenario.apiDescription] ?: return

        val extractLocation = operation.extract

        if(extractLocation != null) {
            val locationPath = extractLocation.split(".")
            if(locationPath.isEmpty())
                return

            val area = locationPath[0]

            val path = locationPath.drop(1)

            when(area.uppercase()) {
                "BODY" -> {
                    val responseBody = response.body

                    if(path.isEmpty()) {
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
        if(originalScenario.isNegative)
            return request

        val operation = workflow.ids[originalScenario.apiDescription] ?: workflow.ids["*"]

        if(operation == null)
            return request

        val useLocation = operation.use ?: return request

        val locationPath = useLocation.split(".")
        if(locationPath.isEmpty())
            return request

        val area = locationPath[0]

        val path = locationPath.drop(1)

        return when(area.uppercase()) {
            "PATH" -> {
                if(path.isEmpty())
                    throw ContractException("Cannot use id $useLocation")

                if(path.size > 1)
                    throw ContractException("PATH.<name> must refer to the name of a path parameter")

                val pathParamName = path[0]

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

                    val result = originalScenario.httpRequestPattern.httpPathPattern?.matches(updatedPath.joinToString("/"), originalScenario.resolver)

                    result?.throwOnFailure()

                    request.copy(path = updatedPath.joinToString("/"))
                }
            }
            else -> {
                throw ContractException("Cannot extract data from $area yet")
            }
        }
    }
}