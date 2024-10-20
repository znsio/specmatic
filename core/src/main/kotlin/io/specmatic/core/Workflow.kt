package io.specmatic.core

import io.specmatic.core.pattern.ContractException
import io.specmatic.core.pattern.key
import io.specmatic.core.value.JSONArrayValue
import io.specmatic.core.value.JSONObjectValue
import io.specmatic.core.value.ScalarValue
import io.specmatic.core.value.Value

data class IDDetails(val propertyName: String, val propertyValue: Value)

class Workflow(
    val workflow: WorkflowConfiguration = WorkflowConfiguration()
) {
    var idDetails: IDDetails? = null

    var responseBody: Value? = null
    var scalarRequestValues: Map<String, ScalarValue> = emptyMap()

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

                    this.responseBody = responseBody

                    if(path.isEmpty()) {
                        idDetails = IDDetails("", responseBody)
                    } else if(responseBody is JSONObjectValue) {
                        val idPath = path.joinToString(".")
                        val data = responseBody.findFirstChildByPath(idPath)
                        if(data != null) {
                            idDetails = IDDetails(idPath, data)
                        }
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

                    idDetails?.let { (_, value) ->
                        updatedPath.set(indexToUpdate, value.toStringLiteral())
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

    fun validateEntityResponse(requestMethod: String?, response: HttpResponse): Result {
        if(requestMethod != "GET")
            return Result.Success()

        val (idPropertyName, idPropertyValue) = idDetails ?: return Result.Success()

        val storedResponse = responseBody as? JSONObjectValue ?: return Result.Success()

        return when(response.body) {
            is JSONObjectValue -> {
                compare(storedResponse, response.body, null) ?: Result.Success()
            }

            is JSONArrayValue -> {
                val results = response.body.list.mapIndexed { index, responseBodyListItem ->
                    val result = if(responseBodyListItem is JSONObjectValue)
                        compare(storedResponse, responseBodyListItem, idDetails)
                    else
                        Result.Success()

                    result?.breadCrumb("[$index]")
                }.filterNotNull()

                if(results.isEmpty())
                    return Result.Failure("""None of the objects returned had a property "$idPropertyName" with the value ${idPropertyValue.displayableValue()}""")

                Result.fromResults(results)
            }

            else -> return Result.Failure("Expected JSON object or array in the response but got ${response.body.displayableType()}")
        }
    }

    val entityMismatchMessages = object : MismatchMessages {
        override fun mismatchMessage(expected: String, actual: String): String {
            return "Expected $expected as per the entity created but was $actual"
        }

        override fun unexpectedKey(keyLabel: String, keyName: String): String {
            return "$keyLabel $keyName in response entity was unexpected"
        }

        override fun expectedKeyWasMissing(keyLabel: String, keyName: String): String {
            return "$keyLabel $keyName in response entity was unexpected"
        }

    }

    private fun compare(
        storedResponse: JSONObjectValue,
        responseBody: JSONObjectValue,
        idDetails: IDDetails?
    ): Result? {
        if (idDetails != null) {
            val (propertyName, idValue) = idDetails
            val idInResponse = responseBody.findFirstChildByPath(propertyName)

            if (idInResponse == null)
                return Result.Failure("""ID property "$propertyName" not found""")

            if (idInResponse != idValue)
                return null
        }

        val storedResponsePattern = storedResponse.exactMatchElseType()
        return storedResponsePattern.matches(responseBody, Resolver(mismatchMessages = entityMismatchMessages))
    }
}