package io.specmatic.core.discriminator

import io.specmatic.core.value.JSONObjectValue
import io.specmatic.core.value.StringValue
import io.specmatic.core.value.Value
import io.specmatic.mock.MOCK_HTTP_REQUEST
import io.specmatic.mock.MOCK_HTTP_RESPONSE

private const val DISCRIMINATOR_DESCRIPTION = "description"

class DiscriminatorExampleInjector(
    private val stubJSON: JSONObjectValue,
    private val requestDiscriminator: DiscriminatorMetadata,
    private val responseDiscriminator: DiscriminatorMetadata
) {
    fun getExampleWithDiscriminator(): JSONObjectValue {
        val existingExample = stubJSON.jsonObject
        val existingRequest = (existingExample[MOCK_HTTP_REQUEST] as JSONObjectValue)
        val existingResponse = (existingExample[MOCK_HTTP_RESPONSE] as JSONObjectValue)

        val requestDescription = mapOf(
            DISCRIMINATOR_DESCRIPTION to getRequestDescription(existingRequest.jsonObject).toStringValue()
        )
        val responseDescription = mapOf(
            DISCRIMINATOR_DESCRIPTION to getResponseDescription(existingRequest.jsonObject).toStringValue()
        )

        val updatedRequestResponseMap = mapOf(
            MOCK_HTTP_REQUEST to existingRequest.copy(
                jsonObject = existingRequest.jsonObject.plus(requestDescription)
            ),
            MOCK_HTTP_RESPONSE to existingResponse.copy(
                jsonObject = existingResponse.jsonObject.plus(responseDescription)
            )
        )
        val updatedExample = existingExample.filterKeys {
            it != MOCK_HTTP_REQUEST && it != MOCK_HTTP_RESPONSE
        }.plus(updatedRequestResponseMap)

        return stubJSON.copy(jsonObject = updatedExample)
    }

    private fun getRequestDescription(request: Map<String, Value>): String {
        val (discriminatorProperty, discriminatorValue) = getNonEmptyDiscriminator()

        val method = request["method"]?.toStringLiteral()
        val path = request["path"]?.toStringLiteral()

        val entityDescription = getEntityDescription(discriminatorProperty, discriminatorValue)

        return when (method) {
            "GET" -> {
                val pathSegments = path?.split("/")?.filter { it.isNotEmpty() } ?: emptyList()
                if (pathSegments.size == 1) {
                    "This is an example of a request to retrieve a list of entities at the $path endpoint"
                } else {
                    "This is an example of a request to retrieve a specific entity at the $path endpoint"
                }
            }
            "POST" -> "This is an example of a request to create a new entity at the $path endpoint $entityDescription"
            "PATCH" -> "This is an example of a request to partially update an existing entity at the $path endpoint $entityDescription"
            "PUT" -> "This is an example of a request to update an existing entity at the $path endpoint $entityDescription"
            "DELETE" -> "This is an example of a request to delete an entity at the $path endpoint"
            else -> "Unknown HTTP method: $method"
        }
    }

    private fun getResponseDescription(request: Map<String, Value>): String {
        val (discriminatorProperty, discriminatorValue) = getNonEmptyDiscriminator()

        val method = request["method"]?.toStringLiteral()
        val path = request["path"]?.toStringLiteral()
        val entityDescription = getEntityDescription(discriminatorProperty, discriminatorValue)

        return when (method) {
            "GET" -> "This is an example of a response $entityDescription"
            "PATCH" -> "This is an example of a response $entityDescription after it has been modified using PATCH"
            "POST" -> "This is an example of a response after creating a new entity at the $path endpoint $entityDescription"
            "PUT" -> "This is an example of a response after updating an existing entity at the $path endpoint $entityDescription"
            "DELETE" -> "This is an example of a response confirming deletion of an entity at the $path endpoint"
            else -> "Unknown HTTP method: $method"
        }
    }

    private fun getEntityDescription(discriminatorProperty: String, discriminatorValue: String): String {
        if(discriminatorProperty.isEmpty() || discriminatorValue.isEmpty()) return ""
        return "when the entity has $discriminatorProperty value as $discriminatorValue"
    }

    private fun getNonEmptyDiscriminator(): DiscriminatorMetadata {
        return when {
            requestDiscriminator.discriminatorValue.isNotEmpty() -> requestDiscriminator
            else -> responseDiscriminator
        }
    }

    private fun String.toStringValue(): StringValue {
        return StringValue(this)
    }
}