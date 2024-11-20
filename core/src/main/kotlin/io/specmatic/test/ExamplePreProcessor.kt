package io.specmatic.test

import io.specmatic.core.*
import io.specmatic.core.pattern.ContractException
import io.specmatic.core.pattern.Row
import io.specmatic.core.pattern.parsedValue
import io.specmatic.core.value.JSONArrayValue
import io.specmatic.core.value.JSONObjectValue
import io.specmatic.core.value.StringValue
import io.specmatic.core.value.Value

const val delayedRandomSubstitutionKey = "\$rand"
val SUBSTITUTE_PATTERN = Regex("^\\$(\\w+)?\\((.*)\\)$")

enum class SubstitutionType { SIMPLE, DELAYED_RANDOM }

object ExampleProcessor {
    private val factStore: MutableMap<String, Value> = mutableMapOf()
    private var runningEntity: Map<String, Value> = mapOf()

    init {
        val payloadConfig = SpecmaticConfig().parsedPayloadConfig?.toFactStore("CONFIG") ?: emptyMap()
        factStore.putAll(payloadConfig)
    }

    private fun defaultIfNotExits(lookupKey: String, type: SubstitutionType = SubstitutionType.SIMPLE): Value {
        throw ContractException("Could not resolve $lookupKey, key does not exist in fact store")
    }

    private fun ifNotExitsToLookupPattern(lookupKey: String, type: SubstitutionType = SubstitutionType.SIMPLE): Value {
        return when (type) {
            SubstitutionType.SIMPLE -> StringValue("$($lookupKey)")
            SubstitutionType.DELAYED_RANDOM -> StringValue("$delayedRandomSubstitutionKey($lookupKey)")
        }
    }

    fun getFactStore(): Map<String, Value> {
        return factStore + runningEntity
    }

    private fun getValue(key: String, type: SubstitutionType): Value? {
        val returnValue = factStore[key] ?: runningEntity[key]
        if (type != SubstitutionType.DELAYED_RANDOM) return returnValue

        val arrayValue = returnValue as? JSONArrayValue
            ?: throw ContractException("$key is not an array in fact store")

        val entityKey = "ENTITY.${key.substringAfterLast('.')}"
        val entityValue = factStore[entityKey] ?: runningEntity[entityKey]
            ?: throw ContractException("Could not resolve $entityKey in fact store")

        val filteredList = arrayValue.list.filterNot { it.toStringLiteral() == entityValue.toStringLiteral() }.ifEmpty {
            throw ContractException("Couldn't pick a random value from $key that was not equal to $entityValue")
        }

        return filteredList.random()
    }

    /* RESOLVER HELPERS */
    fun resolveLookupIfPresent(row: Row): Row {
        return row.copy(
            requestExample = row.requestExample?.let { resolve(it, ::ifNotExitsToLookupPattern) },
            responseExample = row.responseExample?.let { resolve(it, ::ifNotExitsToLookupPattern) },
            values = row.values.map { resolve(parsedValue(it), ::ifNotExitsToLookupPattern).toStringLiteral() }
        )
    }

    fun resolve(httpRequest: HttpRequest, ifNotExists: (lookupKey: String, type: SubstitutionType) -> Value = ::defaultIfNotExits): HttpRequest {
        return httpRequest.copy(
            method = httpRequest.method,
            path = httpRequest.parsePath().joinToString("/", prefix = "/") { resolve(it, ifNotExists) },
            headers = resolve(httpRequest.headers, ifNotExists),
            body = resolve(httpRequest.body, ifNotExists),
            queryParams = QueryParameters(resolve(httpRequest.queryParams.asMap(), ifNotExists))
        )
    }

    fun resolve(httpResponse: HttpResponse, ifNotExists: (lookupKey: String, type: SubstitutionType) -> Value = ::defaultIfNotExits): HttpResponse {
        return httpResponse.copy(
            status = httpResponse.status,
            headers = resolve(httpResponse.headers, ifNotExists),
            body = resolve(httpResponse.body, ifNotExists)
        )
    }

    fun resolve(value: Map<String, String>, ifNotExists: (lookupKey: String, type: SubstitutionType) -> Value): Map<String, String> {
        return value.mapValues { (_, value) -> resolve(value, ifNotExists) }
    }

    private fun resolve(value: String, ifNotExists: (lookupKey: String, type: SubstitutionType) -> Value): String {
        return value.ifSubstitutionToken { token, type ->
            if (type == SubstitutionType.DELAYED_RANDOM && ifNotExists == ::ifNotExitsToLookupPattern ) {
                return@ifSubstitutionToken ifNotExitsToLookupPattern(token, type).toStringLiteral()
            } else getValue(token, type)?.toStringLiteral() ?: ifNotExists(token, type).toStringLiteral()
        } ?: value
    }

    private fun resolve(value: Value, ifNotExists: (lookupKey: String, type: SubstitutionType) -> Value): Value {
        return when (value) {
            is StringValue -> resolve(value, ifNotExists)
            is JSONObjectValue -> resolve(value, ifNotExists)
            is JSONArrayValue -> resolve(value, ifNotExists)
            else -> value
        }
    }

    private fun resolve(value: StringValue, ifNotExists: (lookupKey: String, type: SubstitutionType) -> Value): Value {
        return value.ifSubstitutionToken { token, type ->
            if (type == SubstitutionType.DELAYED_RANDOM && ifNotExists == ::ifNotExitsToLookupPattern) {
                return@ifSubstitutionToken ifNotExitsToLookupPattern(token, type)
            } else getValue(token, type) ?: ifNotExists(token, type)
        } ?: value
    }

    private fun resolve(value: JSONObjectValue, ifNotExists: (lookupKey: String, type: SubstitutionType) -> Value): JSONObjectValue {
        return JSONObjectValue(value.jsonObject.mapValues { (_, value) -> resolve(value, ifNotExists) })
    }

    private fun resolve(value: JSONArrayValue, ifNotExists: (lookupKey: String, type: SubstitutionType) -> Value): JSONArrayValue {
        return JSONArrayValue(value.list.map { resolve(it, ifNotExists) })
    }

    /* STORE HELPERS */
    fun store(exampleRow: Row, httpResponse: HttpResponse) {
        val bodyToCheck = exampleRow.responseExample?.body ?: exampleRow.responseExampleForValidation?.responseExample?.body

        bodyToCheck?.ifContainsStoreToken { _ ->
            runningEntity = httpResponse.body.toFactStore(prefix = "ENTITY")
        }
    }

    private fun Value.ifContainsStoreToken(block: (entityKey: String) -> Unit) {
        if (this !is JSONObjectValue) return

        val entityId = this.jsonObject.entries.firstOrNull { it.value.toStringLiteral().matches("\\(ENTITY\\)".toRegex()) }
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

    private fun <T> T.ifSubstitutionToken(block: (lookupKey: String, type: SubstitutionType) -> T): T? {
        return if (isSubstitutionToken(this)) {
            val type = if (this.toString().contains(delayedRandomSubstitutionKey)) {
                SubstitutionType.DELAYED_RANDOM
            } else SubstitutionType.SIMPLE

            block(withoutSubstituteDelimiters(this), type)
        } else null
    }

    fun <T> isSubstitutionToken(token: T): Boolean {
        return when (token) {
            is String -> SUBSTITUTE_PATTERN.matchEntire(token) != null
            is StringValue -> SUBSTITUTE_PATTERN.matchEntire(token.string) != null
            else -> false
        }
    }

    private fun <T> withoutSubstituteDelimiters(token: T): String {
        return when (token) {
            is String -> SUBSTITUTE_PATTERN.find(token)?.groups?.get(2)?.value ?: ""
            is StringValue -> SUBSTITUTE_PATTERN.find(token.string)?.groups?.get(2)?.value ?: ""
            else -> ""
        }
    }
}
