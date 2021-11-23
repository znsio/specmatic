package `in`.specmatic.core.pattern

import `in`.specmatic.core.*
import `in`.specmatic.core.utilities.mapZip
import `in`.specmatic.core.utilities.stringToPatternMap
import `in`.specmatic.core.utilities.withNullPattern
import `in`.specmatic.core.value.JSONArrayValue
import `in`.specmatic.core.value.JSONObjectValue
import `in`.specmatic.core.value.Value

fun toJSONObjectPattern(jsonContent: String, typeAlias: String?): JSONObjectPattern = toJSONObjectPattern(stringToPatternMap(jsonContent)).copy(typeAlias = typeAlias)

fun toJSONObjectPattern(map: Map<String, Pattern>): JSONObjectPattern {
    val missingKeyStrategy = when ("...") {
        in map -> IgnoreUnexpectedKeys
        else -> ValidateUnexpectedKeys
    }

    return JSONObjectPattern(map.minus("..."), missingKeyStrategy)
}

fun toJSONObjectPattern(map: Map<String, Pattern>, typeAlias: String?): JSONObjectPattern {
    val missingKeyStrategy = when ("...") {
        in map -> IgnoreUnexpectedKeys
        else -> ValidateUnexpectedKeys
    }

    return JSONObjectPattern(map.minus("..."), missingKeyStrategy, typeAlias)
}

data class JSONObjectPattern(override val pattern: Map<String, Pattern> = emptyMap(), private val unexpectedKeyCheck: UnexpectedKeyCheck = ValidateUnexpectedKeys, override val typeAlias: String? = null) : Pattern {
    override fun equals(other: Any?): Boolean = when (other) {
        is JSONObjectPattern -> this.pattern == other.pattern
        else -> false
    }

    override fun encompasses(otherPattern: Pattern, thisResolver: Resolver, otherResolver: Resolver, typeStack: TypeStack): Result {
        val thisResolverWithNullType = withNullPattern(thisResolver)
        val otherResolverWithNullType = withNullPattern(otherResolver)

        return when (otherPattern) {
            is ExactValuePattern -> otherPattern.fitsWithin(listOf(this), otherResolverWithNullType, thisResolverWithNullType, typeStack)
            is TabularPattern -> mapEncompassesMap(pattern, otherPattern.pattern, thisResolverWithNullType, otherResolverWithNullType, typeStack)
            is JSONObjectPattern -> mapEncompassesMap(pattern, otherPattern.pattern, thisResolverWithNullType, otherResolverWithNullType, typeStack)
            else -> Result.Failure("Expected json type, got ${otherPattern.typeName}")
        }
    }

    override fun listOf(valueList: List<Value>, resolver: Resolver): Value {
        return JSONArrayValue(valueList)
    }

    override fun matches(sampleData: Value?, resolver: Resolver): Result {
        val resolverWithNullType = withNullPattern(resolver)
        if (sampleData !is JSONObjectValue)
            return mismatchResult("JSON object", sampleData)

        val missingKey = resolverWithNullType.findKeyError(pattern, sampleData.jsonObject, unexpectedKeyCheck)
        if (missingKey != null)
            return missingKey.missingKeyToResult("key")

        mapZip(pattern, sampleData.jsonObject).forEach { (key, patternValue, sampleValue) ->
            when (val result = resolverWithNullType.matchesPattern(key, patternValue, sampleValue)) {
                is Result.Failure -> return result.breadCrumb(key)
            }
        }

        return Result.Success()
    }

    override fun generate(resolver: Resolver): JSONObjectValue {
        val resolverWithNullType = withNullPattern(resolver)
        return JSONObjectValue(generate(pattern, resolverWithNullType))
    }

    override fun newBasedOn(row: Row, resolver: Resolver): List<JSONObjectPattern> {
        val resolverWithNullType = withNullPattern(resolver)
        return forEachKeyCombinationIn(pattern.minus("..."), row) { pattern ->
            newBasedOn(pattern, row, resolverWithNullType)
        }.map { toJSONObjectPattern(it) }
    }

    override fun newBasedOn(resolver: Resolver): List<JSONObjectPattern> {
        val resolverWithNullType = withNullPattern(resolver)
        return allOrNothingCombinationIn(pattern.minus("...")) { pattern ->
            newBasedOn(pattern, resolverWithNullType)
        }.map { toJSONObjectPattern(it) }
    }

    override fun parse(value: String, resolver: Resolver): Value = parsedJSON(value)
    override fun hashCode(): Int = pattern.hashCode()

    override val typeName: String = "json object"
}

fun generate(jsonPattern: Map<String, Pattern>, resolver: Resolver): Map<String, Value> {
    val resolverWithNullType = withNullPattern(resolver)
    return jsonPattern.mapKeys { entry -> withoutOptionality(entry.key) }.mapValues { (key, pattern) ->
        attempt(breadCrumb = key) { resolverWithNullType.generate(key, pattern) }
    }
}

internal fun mapEncompassesMap(pattern: Map<String, Pattern>, otherPattern: Map<String, Pattern>, thisResolverWithNullType: Resolver, otherResolverWithNullType: Resolver, typeStack: TypeStack = emptySet()): Result {
    val myRequiredKeys = pattern.keys.filter { !isOptional(it) }
    val otherRequiredKeys = otherPattern.keys.filter { !isOptional(it) }

    val missingFixedKey = myRequiredKeys.find { it !in otherRequiredKeys }
    if (missingFixedKey != null)
        return MissingKeyError(missingFixedKey).missingKeyToResult("key").breadCrumb(missingFixedKey)

    return pattern.keys.asSequence().map { key ->
        val bigger = pattern.getValue(key)
        val smaller = otherPattern[key] ?: otherPattern[withoutOptionality(key)]

        when {
            smaller != null -> biggerEncompassesSmaller(bigger, smaller, thisResolverWithNullType, otherResolverWithNullType, typeStack).breadCrumb(key)
            else -> Result.Success()
        }
    }.find { it is Result.Failure } ?: Result.Success()
}
