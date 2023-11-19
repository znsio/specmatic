package `in`.specmatic.core.pattern

import `in`.specmatic.core.*
import `in`.specmatic.core.utilities.mapZip
import `in`.specmatic.core.utilities.stringToPatternMap
import `in`.specmatic.core.utilities.withNullPattern
import `in`.specmatic.core.value.JSONArrayValue
import `in`.specmatic.core.value.JSONObjectValue
import `in`.specmatic.core.value.Value
import java.util.Optional

fun toJSONObjectPattern(jsonContent: String, typeAlias: String?): JSONObjectPattern = toJSONObjectPattern(stringToPatternMap(jsonContent), typeAlias)

fun toJSONObjectPattern(map: Map<String, Pattern>, typeAlias: String? = null): JSONObjectPattern {
    val missingKeyStrategy: UnexpectedKeyCheck = when ("...") {
        in map -> IgnoreUnexpectedKeys
        else -> ValidateUnexpectedKeys
    }

    return JSONObjectPattern(map.minus("..."), missingKeyStrategy, typeAlias)
}

data class JSONObjectPattern(override val pattern: Map<String, Pattern> = emptyMap(), private val unexpectedKeyCheck: UnexpectedKeyCheck = ValidateUnexpectedKeys, override val typeAlias: String? = null, val minProperties: Int? = null, val maxProperties: Int? = null) : Pattern {
    override fun equals(other: Any?): Boolean = when (other) {
        is JSONObjectPattern -> this.pattern == other.pattern
        else -> false
    }

    override fun encompasses(otherPattern: Pattern, thisResolver: Resolver, otherResolver: Resolver, typeStack: TypeStack): Result {
        val thisResolverWithNullType = withNullPattern(thisResolver)
        val otherResolverWithNullType = withNullPattern(otherResolver)

        return when (otherPattern) {
            is ExactValuePattern -> otherPattern.fitsWithin(listOf(this), otherResolverWithNullType, thisResolverWithNullType, typeStack)
            is TabularPattern -> {
                mapEncompassesMap(pattern, otherPattern.pattern, thisResolverWithNullType, otherResolverWithNullType, typeStack)
            }
            is JSONObjectPattern -> {
                val propertyLimitResults: List<Result.Failure> = olderPropertyLimitsEncompassNewer(this, otherPattern)
                mapEncompassesMap(pattern, otherPattern.pattern, thisResolverWithNullType, otherResolverWithNullType, typeStack, propertyLimitResults)
            }
            else -> Result.Failure("Expected json type, got ${otherPattern.typeName}")
        }
    }

    private fun olderPropertyLimitsEncompassNewer(newer: JSONObjectPattern, older: JSONObjectPattern): List<Result.Failure> {
        val minPropertiesResult = if(older.minProperties != null && newer.minProperties != null && older.minProperties > newer.minProperties)
            Result.Failure("Expected at least ${older.minProperties} properties, got ${newer.minProperties}")
        else
            Result.Success()

        val maxPropertiesResult = if(older.maxProperties != null && newer.maxProperties != null && older.maxProperties < newer.maxProperties)
            Result.Failure("Expected at most ${older.maxProperties} properties, got ${newer.maxProperties}")
        else
            Result.Success()

        return listOf(minPropertiesResult, maxPropertiesResult).filterIsInstance<Result.Failure>()
    }

    override fun generateWithAll(resolver: Resolver): Value {
        return attempt(breadCrumb = "HEADERS") {
            JSONObjectValue(pattern.filterNot { it.key == "..." }.mapKeys {
                attempt(breadCrumb = it.key) {
                    withoutOptionality(it.key)
                }
            }.mapValues {
                it.value.generateWithAll(resolver)
            })
        }
    }

    override fun listOf(valueList: List<Value>, resolver: Resolver): Value {
        return JSONArrayValue(valueList)
    }

    override fun matches(sampleData: Value?, resolver: Resolver): Result {
        val resolverWithNullType = withNullPattern(resolver)
        if (sampleData !is JSONObjectValue)
            return mismatchResult("JSON object", sampleData, resolver.mismatchMessages)

        val minCountErrors: List<Result.Failure> = if(sampleData.jsonObject.keys.size < (minProperties ?: 0))
            listOf(Result.Failure("Expected at least $minProperties properties, got ${sampleData.jsonObject.keys.size}"))
        else
            emptyList()

        val maxCountErrors: List<Result.Failure> = if(sampleData.jsonObject.keys.size > (maxProperties ?: Int.MAX_VALUE))
            listOf(Result.Failure("Expected at most $maxProperties properties, got ${sampleData.jsonObject.keys.size}"))
        else
            emptyList()

        val keyErrors: List<Result.Failure> = resolverWithNullType.findKeyErrorList(pattern, sampleData.jsonObject).map {
            it.missingKeyToResult("key", resolver.mismatchMessages).breadCrumb(it.name)
        }

        val results: List<Result.Failure> = mapZip(pattern, sampleData.jsonObject).map { (key, patternValue, sampleValue) ->
            resolverWithNullType.matchesPattern(key, patternValue, sampleValue).breadCrumb(key)
        }.filterIsInstance<Result.Failure>()

        val failures: List<Result.Failure> = minCountErrors + maxCountErrors + keyErrors + results

        return if(failures.isEmpty())
            Result.Success()
        else
            Result.Failure.fromFailures(failures)
    }

    override fun generate(resolver: Resolver): JSONObjectValue {
        return selectPropertiesWithinMaxAndMin(pattern, minProperties, maxProperties).let {
            JSONObjectValue(generate(it, withNullPattern(resolver)))
        }
    }

    override fun newBasedOn(row: Row, resolver: Resolver): List<JSONObjectPattern> =
        allOrNothingCombinationIn(pattern.minus("..."), if(resolver.generativeTestingEnabled) Row() else row, minProperties, maxProperties) { pattern ->
            newBasedOn(pattern, row, withNullPattern(resolver))
        }.map { toJSONObjectPattern(it.mapKeys { (key, _) ->
            withoutOptionality(key)
        }) }

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

    val optionalProps = jsonPattern.keys.filter { isOptional(it) }.map { withoutOptionality(it) }

    return jsonPattern
        .mapKeys { entry -> withoutOptionality(entry.key) }
        .mapValues { (key, pattern) ->
            attempt(breadCrumb = key) {
                // Handle cycle (represented by null value) by marking this property as removable
                Optional.ofNullable(resolverWithNullType.withCyclePrevention(pattern, optionalProps.contains(key)) {
                    it.generate(key, pattern)
                })
            }
        }
        .filterValues { it.isPresent }
        .mapValues { (key, opt) -> opt.get()}
}

private fun selectPropertiesWithinMaxAndMin(
    jsonPattern: Map<String, Pattern>,
    minProperties: Int?,
    maxProperties: Int?
): Map<String, Pattern> {
    val withAtMostMaxProperties = selectAtMostMaxProperties(jsonPattern, maxProperties)
    val withinMinAndMaxProperties = selectAtMostMinProperties(withAtMostMaxProperties, minProperties)

    return withinMinAndMaxProperties
}

private fun selectAtMostMinProperties(
    properties: Map<String, Pattern>,
    minProperties: Int?
): Map<String, Pattern> {
    return if (minProperties != null) {
        val mandatoryKeys = properties.keys.filter { !isOptional(it) }
        val optionalKeys = properties.keys.filter { isOptional(it) }

        if (mandatoryKeys.size >= minProperties)
            properties.filterKeys { it in mandatoryKeys }
        else {
            val countOfOptionalKeysToPick = minProperties - mandatoryKeys.size
            val selectedOptionalKeys = optionalKeys.shuffled().take(countOfOptionalKeysToPick)
            val selectedKeys = mandatoryKeys + selectedOptionalKeys

            if(selectedKeys.size < minProperties)
                throw ContractException("Cannot generate a JSON object with at least $minProperties properties as there are only ${selectedKeys.size} properties in the specification.")

            properties.filterKeys { it in selectedKeys }
        }
    } else
        properties
}


private fun selectAtMostMaxProperties(
    properties: Map<String, Pattern>,
    maxProperties: Int?
) = if (maxProperties != null) {
    val mandatoryKeys = properties.keys.filter { !isOptional(it) }
    if(mandatoryKeys.size > maxProperties)
        throw ContractException("Cannot generate a JSON object with at most $maxProperties properties as there are ${mandatoryKeys.size} mandatory properties in the specification.")

    val optionalKeys = properties.keys.filter { isOptional(it) }
    val countOfOptionalKeysToPick = maxProperties - mandatoryKeys.size
    val selectedOptionalKeys = optionalKeys.shuffled().take(countOfOptionalKeysToPick)
    val selectedKeys = mandatoryKeys + selectedOptionalKeys

    properties.filterKeys { it in selectedKeys }
} else
    properties

internal fun mapEncompassesMap(pattern: Map<String, Pattern>, otherPattern: Map<String, Pattern>, thisResolverWithNullType: Resolver, otherResolverWithNullType: Resolver, typeStack: TypeStack = emptySet(), previousResults: List<Result.Failure> = emptyList()): Result {
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

    return Result.fromResults(previousResults + missingFixedKeyErrors + keyErrors)
}
