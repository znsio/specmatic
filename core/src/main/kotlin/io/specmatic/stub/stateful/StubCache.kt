package io.specmatic.stub.stateful

import io.specmatic.core.value.JSONArrayValue
import io.specmatic.core.value.JSONObjectValue
import java.util.concurrent.locks.ReentrantLock
import kotlin.concurrent.withLock

data class CachedResponse(
    val path: String,
    val responseBody: JSONObjectValue
)

class StubCache {
    private val cachedResponses = mutableListOf<CachedResponse>()
    private val lock = ReentrantLock()

    fun addResponse(path: String, responseBody: JSONObjectValue) = lock.withLock {
        cachedResponses.add(
            CachedResponse(path, responseBody)
        )
    }

    fun updateResponse(
        path: String,
        responseBody: JSONObjectValue,
        idKey: String,
        idValue: String
    ) = lock.withLock {
        deleteResponse(path, idKey, idValue)
        addResponse(path, responseBody)
    }

    fun findResponseFor(path: String, idKey: String, idValue: String): CachedResponse? = lock.withLock {
        return cachedResponses.filter {
            it.path == path
        }.firstOrNull {
            val body = it.responseBody
            body.findFirstChildByPath(idKey)?.toStringLiteral() == idValue
        }
    }

    fun findAllResponsesFor(path: String, attributeSelectionKeys: Set<String>): JSONArrayValue = lock.withLock {
        val responseBodies = cachedResponses.filter { it.path == path }.map {
            it.responseBody.removeKeysNotPresentIn(attributeSelectionKeys)
        }
        return JSONArrayValue(responseBodies)
    }

    fun deleteResponse(path: String, idKey: String, idValue: String) {
        val existingResponse = findResponseFor(path, idKey, idValue) ?: return
        lock.withLock {
            cachedResponses.remove(existingResponse)
        }
    }
}