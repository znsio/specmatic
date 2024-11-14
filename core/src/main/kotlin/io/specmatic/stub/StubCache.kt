package io.specmatic.stub

import io.specmatic.core.value.JSONArrayValue
import io.specmatic.core.value.JSONObjectValue

data class CachedResponse(
    val path: String,
    val responseBody: JSONObjectValue
)

class StubCache {
    private val cachedResponses = mutableListOf<CachedResponse>()

    fun addResponse(path: String, responseBody: JSONObjectValue) {
        cachedResponses.add(
            CachedResponse(path, responseBody)
        )
    }

    fun updateResponse(
        path: String,
        responseBody: JSONObjectValue,
        idKey: String,
        idValue: String
    ) {
        deleteResponse(path, idKey, idValue)
        addResponse(path, responseBody)
    }

    fun findResponseFor(path: String, idKey: String, idValue: String): CachedResponse? {
        return cachedResponses.filter {
            it.path == path
        }.firstOrNull {
            val body = it.responseBody
            body.findFirstChildByPath(idKey)?.toStringLiteral() == idValue
        }
    }

    fun findAllResponsesFor(path: String, attributeSelectionKeys: Set<String>): JSONArrayValue {
        val responseBodies = cachedResponses.filter { it.path == path }.map {
            it.responseBody.removeKeysNotPresentIn(attributeSelectionKeys)
        }
        return JSONArrayValue(responseBodies)
    }

    fun deleteResponse(path: String, idKey: String, idValue: String) {
        val existingResponse = findResponseFor(path, idKey, idValue) ?: return
        cachedResponses.remove(existingResponse)
    }
}