package `in`.specmatic.core.pattern

import `in`.specmatic.core.*
import `in`.specmatic.core.utilities.mapZip
import `in`.specmatic.core.utilities.stringToPatternMap
import `in`.specmatic.core.utilities.withNullPattern
import `in`.specmatic.core.value.JSONArrayValue
import `in`.specmatic.core.value.JSONObjectValue
import `in`.specmatic.core.value.Value

fun toJSONObjectPattern(jsonContent: String, typeAlias: String?): JSONObjectPattern = toJSONObjectPattern(stringToPatternMap(jsonContent), typeAlias)

fun toJSONObjectPattern(map: Map<String, Pattern>, typeAlias: String? = null): JSONObjectPattern {
    val missingKeyStrategy: UnexpectedKeyCheck = when ("...") {
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
        val resolverWithNullType = withNullPattern(resolver).withUnexpectedKeyCheck(unexpectedKeyCheck)
        if (sampleData !is JSONObjectValue)
            return mismatchResult("JSON object", sampleData, resolver.mismatchMessages)

        val keyErrors: List<Result.Failure> = resolverWithNullType.findKeyErrorList(pattern, sampleData.jsonObject).map {
            it.missingKeyToResult("key", resolver.mismatchMessages).breadCrumb(it.name)
        }

        val results: List<Result.Failure> = mapZip(pattern, sampleData.jsonObject).map { (key, patternValue, sampleValue) ->
            resolverWithNullType.matchesPattern(key, patternValue, sampleValue).breadCrumb(key)
        }.filterIsInstance<Result.Failure>()

        val failures = keyErrors.plus(results)

        return if(failures.isEmpty())
            Result.Success()
        else
            Result.Failure.fromFailures(failures)
    }

    override fun generate(resolver: Resolver): JSONObjectValue =
        JSONObjectValue(generate(pattern, withNullPattern(resolver)))

    override fun newBasedOn(row: Row, resolver: Resolver): List<JSONObjectPattern> =
        forEachKeyCombinationIn(pattern.minus("..."), row) { pattern ->
            newBasedOn(pattern, row, withNullPattern(resolver))
        }.map { toJSONObjectPattern(it) }

    override fun newBasedOn(resolver: Resolver): List<JSONObjectPattern> =
        allOrNothingCombinationIn(pattern.minus("...")) { pattern ->
            newBasedOn(pattern, withNullPattern(resolver))
        }.map { toJSONObjectPattern(it) }

    override fun negativeBasedOn(row: Row, resolver: Resolver): List<Pattern> =
        allOrNothingCombinationIn(pattern.minus("...")) { pattern ->
            negativeBasedOn(pattern, row, withNullPattern(resolver))
        }.map { toJSONObjectPattern(it) }

    override fun parse(value: String, resolver: Resolver): Value = parsedJSONObject(value, resolver.mismatchMessages)
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

    val missingFixedKeyErrors: List<Result.Failure> = myRequiredKeys.filter { it !in otherRequiredKeys }.map { missingFixedKey ->
        MissingKeyError(missingFixedKey).missingKeyToResult("key", thisResolverWithNullType.mismatchMessages).breadCrumb(withoutOptionality(missingFixedKey))
    }

    val keyErrors = pattern.keys.map { key ->
        val bigger = pattern.getValue(key)
        val smaller = otherPattern[key] ?: otherPattern[withoutOptionality(key)]

        when {
            smaller != null -> biggerEncompassesSmaller(bigger, smaller, thisResolverWithNullType, otherResolverWithNullType, typeStack).breadCrumb(withoutOptionality(key))
            else -> Result.Success()
        }
    }

    return Result.fromResults(missingFixedKeyErrors.plus(keyErrors))
}
