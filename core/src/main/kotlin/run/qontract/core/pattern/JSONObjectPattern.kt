package run.qontract.core.pattern

import run.qontract.core.*
import run.qontract.core.utilities.mapZip
import run.qontract.core.utilities.stringToPatternMap
import run.qontract.core.value.*

data class JSONObjectPattern(override val pattern: Map<String, Pattern> = emptyMap()) : Pattern {
    constructor(jsonContent: String) : this(stringToPatternMap(jsonContent))

    override fun encompasses2(otherPattern: Pattern, thisResolver: Resolver, otherResolver: Resolver): Result {
        if(otherPattern !is JSONObjectPattern)
            return Result.Failure("Expected tabular json type, got ${otherPattern.typeName}")

        val myRequiredKeys = pattern.keys.filter { !isOptional(it) }
        val otherRequiredKeys = otherPattern.pattern.keys.filter { !isOptional(it) }

        val missingFixedKey = myRequiredKeys.find { it !in otherRequiredKeys }
        if(missingFixedKey != null)
            return Result.Failure("Key $missingFixedKey was missing", breadCrumb = missingFixedKey)

        val thisResolverWithNumberType = withNumberTypePattern(thisResolver)
        val otherResolverWithNumberType = withNumberTypePattern(otherResolver)

        val result = pattern.keys.asSequence().map { key ->
            val bigger = pattern.getValue(key)
            val smaller = otherPattern.pattern[key] ?: otherPattern.pattern[withoutOptionality(key)]

            Pair(key,
                    if(smaller != null)
                        bigger.encompasses2(resolvedHop(smaller, otherResolverWithNumberType), thisResolverWithNumberType, otherResolverWithNumberType)
                    else Result.Success())
        }.find { it.second is Result.Failure }

        return result?.second?.breadCrumb(breadCrumb = result?.first) ?: Result.Success()
    }

    override fun matches(sampleData: Value?, resolver: Resolver): Result {
        if(sampleData !is JSONObjectValue)
            return mismatchResult("JSON object", sampleData)

        val missingKey = resolver.findMissingKey(pattern, sampleData.jsonObject)
        if(missingKey != null)
            return Result.Failure("Missing key $missingKey")

        mapZip(pattern, sampleData.jsonObject).forEach { (key, patternValue, sampleValue) ->
            when (val result = withNumberTypePattern(resolver).matchesPattern(key, patternValue, sampleValue)) {
                is Result.Failure -> return result.breadCrumb(key)
            }
        }

        return Result.Success()
    }

    override fun generate(resolver: Resolver) = JSONObjectValue(generate(pattern, resolver))

    override fun newBasedOn(row: Row, resolver: Resolver): List<JSONObjectPattern> =
            newBasedOn(pattern, row, resolver).map { JSONObjectPattern(it) }
    override fun parse(value: String, resolver: Resolver): Value = parsedJSONStructure(value)
    override fun encompasses(otherPattern: Pattern, resolver: Resolver): Boolean = otherPattern is JSONObjectPattern
    override val typeName: String = "json object"
}

fun generate(jsonPattern: Map<String, Pattern>, resolver: Resolver): Map<String, Value> =
    jsonPattern.mapKeys { entry -> withoutOptionality(entry.key) }.mapValues { (key, pattern) ->
        attempt(breadCrumb = key) { resolver.generate(key, pattern) }
    }
