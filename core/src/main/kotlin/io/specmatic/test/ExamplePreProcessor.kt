package io.specmatic.test

import io.specmatic.core.*
import io.specmatic.core.pattern.ContractException
import io.specmatic.core.pattern.Row
import io.specmatic.core.pattern.parsedValue
import io.specmatic.core.value.JSONArrayValue
import io.specmatic.core.value.JSONObjectValue
import io.specmatic.core.value.StringValue
import io.specmatic.core.value.Value

object ExampleProcessor {
    private val factStore: MutableMap<String, Value> = mutableMapOf()
    private var runningEntity: Map<String, Value> = mapOf()

    init {
        val payloadConfig = SpecmaticConfig().parsedPayloadConfig?.toFactStore("CONFIG") ?: emptyMap()
        factStore.putAll(payloadConfig)
    }

    private fun defaultIfNotExits(lookupKey: String): Value {
        throw ContractException("Could not resolve $lookupKey, key does not exist in fact store")
    }

    private fun ifNotExitsToLookupPattern(lookupKey: String): Value {
        return StringValue("$($lookupKey)")
    }

    fun getFactStore(): Map<String, Value> {
        return factStore + runningEntity
    }

    private fun getValue(key: String): Value? {
        return factStore[key] ?: runningEntity[key]
    }

    /* RESOLVER HELPERS */
    fun resolveLookupIfPresent(row: Row): Row {
        return row.copy(
            requestExample = row.requestExample?.let { resolve(it, ::ifNotExitsToLookupPattern) },
            responseExample = row.responseExample?.let { resolve(it, ::ifNotExitsToLookupPattern) },
            values = row.values.map { resolve(parsedValue(it), ::ifNotExitsToLookupPattern).toStringLiteral() }
        )
    }

    fun resolve(httpRequest: HttpRequest, ifNotExists: (lookupKey: String) -> Value = ::defaultIfNotExits): HttpRequest {
        return httpRequest.copy(
            method = httpRequest.method,
            path = httpRequest.parsePath().joinToString("/") { resolve(it, ifNotExists) },
            headers = resolve(httpRequest.headers, ifNotExists),
            body = resolve(httpRequest.body, ifNotExists),
            queryParams = QueryParameters(resolve(httpRequest.queryParams.asMap(), ifNotExists))
        )
    }

    fun resolve(httpResponse: HttpResponse, ifNotExists: (lookupKey: String) -> Value = ::defaultIfNotExits): HttpResponse {
        return httpResponse.copy(
            status = httpResponse.status,
            headers = resolve(httpResponse.headers, ifNotExists),
            body = resolve(httpResponse.body, ifNotExists)
        )
    }

    fun resolve(value: Map<String, String>, ifNotExists: (lookupKey: String) -> Value): Map<String, String> {
        return value.mapValues { (_, value) -> resolve(value, ifNotExists) }
    }

    private fun resolve(value: String, ifNotExists: (lookupKey: String) -> Value): String {
        return value.ifSubstitutionToken {
            getValue(it)?.toStringLiteral() ?: ifNotExists(it).toStringLiteral()
        } ?: value
    }

    private fun resolve(value: Value, ifNotExists: (lookupKey: String) -> Value): Value {
        return when (value) {
            is StringValue -> resolve(value, ifNotExists)
            is JSONObjectValue -> resolve(value, ifNotExists)
            is JSONArrayValue -> resolve(value, ifNotExists)
            else -> value
        }
    }

    private fun resolve(value: StringValue, ifNotExists: (lookupKey: String) -> Value): Value {
        return value.ifSubstitutionToken {
            getValue(it) ?: ifNotExists(it)
        } ?: value
    }

    private fun resolve(value: JSONObjectValue, ifNotExists: (lookupKey: String) -> Value): JSONObjectValue {
        return JSONObjectValue(value.jsonObject.mapValues { (_, value) -> resolve(value, ifNotExists) })
    }

    private fun resolve(value: JSONArrayValue, ifNotExists: (lookupKey: String) -> Value): JSONArrayValue {
        return JSONArrayValue(value.list.map { resolve(it, ifNotExists) })
    }

    /* STORE HELPERS */
    fun store(exampleRow: Row, httpRequest: HttpRequest): HttpRequest {
        TODO("Not yet implemented")
    }

    fun store(exampleRow: Row, httpResponse: HttpResponse) {
        val bodyToCheck = exampleRow.responseExample?.body ?: exampleRow.responseExampleForValidation?.responseExample?.body

        bodyToCheck?.ifContainsStoreToken { entityKey ->
            runningEntity = httpResponse.body.toFactStore(prefix = "ENTITY").let {
                val entityKeyValue = it["ENTITY.$entityKey"] ?: throw ContractException("Could not resolve $entityKey, key does not exist in response body")
                it + mapOf("ENTITY_ID" to entityKeyValue)
            }
        }
    }

    private fun Value.ifContainsStoreToken(block: (entityKey: String) -> Unit) {
        if (this !is JSONObjectValue) return

        val entityId = this.jsonObject.entries.firstOrNull { it.value.toStringLiteral().matches("\\(ENTITY_ID:.*\\)".toRegex()) }
        entityId?.let { block(it.key) }
    }

    /* PARSER HELPERS */
    private fun Value.toFactStore(prefix: String = ""): Map<String, Value> {
        return this.traverse(
            prefix = prefix,
            onScalar = { scalar, key -> mapOf(key to scalar) },
            onComposite = { composite, key -> mapOf(key to composite) }
        )
    }

    private fun HttpRequest.parsePath(): List<String> {
        return path?.trim('/')?.split("/") ?: emptyList()
    }

    private fun <T> T.ifSubstitutionToken(block: (lookupKey: String) -> T): T? {
        return if (isSubstitutionToken(this)) {
            block(withoutSubstituteDelimiters(this))
        } else null
    }

    fun <T> isSubstitutionToken(token: T): Boolean {
        return when (token) {
            is String -> token.startsWith("${'$'}(") && token.endsWith(")")
            is StringValue -> token.string.startsWith("${'$'}(") && token.string.endsWith(")")
            else -> false
        }
    }

    private fun <T> withoutSubstituteDelimiters(token: T): String {
        return when (token) {
            is String -> token.substring(2, token.length - 1)
            is StringValue -> token.string.substring(2, token.string.length - 1)
            else -> ""
        }
    }
}

