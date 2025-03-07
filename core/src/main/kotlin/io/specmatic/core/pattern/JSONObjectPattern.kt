package io.specmatic.core.pattern

import io.specmatic.core.*
import io.specmatic.core.pattern.config.NegativePatternConfiguration
import io.specmatic.core.utilities.mapZip
import io.specmatic.core.utilities.stringToPatternMap
import io.specmatic.core.utilities.withNullPattern
import io.specmatic.core.value.*
import java.util.Optional

fun toJSONObjectPattern(jsonContent: String, typeAlias: String?): JSONObjectPattern =
    toJSONObjectPattern(stringToPatternMap(jsonContent), typeAlias)

fun toJSONObjectPattern(map: Map<String, Pattern>, typeAlias: String? = null): JSONObjectPattern {
    val missingKeyStrategy: UnexpectedKeyCheck = when ("...") {
        in map -> IgnoreUnexpectedKeys
        else -> ValidateUnexpectedKeys
    }

    return JSONObjectPattern(map.minus("..."), missingKeyStrategy, typeAlias)
}

sealed interface AdditionalProperties {
    fun updatePatternMap(patternMap: Map<String, Pattern>, valueMap: Map<String, Value>): Map<String, Pattern>

    data object NoAdditionalProperties : AdditionalProperties {
        override fun updatePatternMap(patternMap: Map<String, Pattern>, valueMap: Map<String, Value>): Map<String, Pattern> {
            return patternMap
        }
    }

    data object FreeForm : AdditionalProperties {
        override fun updatePatternMap(patternMap: Map<String, Pattern>, valueMap: Map<String, Value>): Map<String, Pattern> {
            val extraKeys = valueMap.excludingPatternKeys(patternMap)
            return patternMap + extraKeys.associateWith { AnyValuePattern }
        }
    }

    data class PatternConstrained(val pattern: Pattern): AdditionalProperties {
        override fun updatePatternMap(patternMap: Map<String, Pattern>, valueMap: Map<String, Value>): Map<String, Pattern> {
            val extraKeys = valueMap.excludingPatternKeys(patternMap)
            return patternMap + extraKeys.associateWith { pattern }
        }
    }

    fun Map<String, Value>.excludingPatternKeys(pattern: Map<String, Pattern>): Set<String> {
        val patternKeys = pattern.keys.map(::withoutOptionality).toSet()
        return keys.minus(patternKeys)
    }
}

data class JSONObjectPattern(
    override val pattern: Map<String, Pattern> = emptyMap(),
    private val unexpectedKeyCheck: UnexpectedKeyCheck = ValidateUnexpectedKeys,
    override val typeAlias: String? = null,
    val minProperties: Int? = null,
    val maxProperties: Int? = null,
    val additionalProperties: AdditionalProperties = AdditionalProperties.NoAdditionalProperties
) : Pattern, PossibleJsonObjectPatternContainer {

    override fun fixValue(value: Value, resolver: Resolver): Value {
        if (resolver.matchesPattern(null, this, value).isSuccess()) return value
        val valueMap = (value as? JSONObjectValue)?.jsonObject.orEmpty()
        val adjustedPattern = additionalProperties.updatePatternMap(pattern, valueMap)

        return JSONObjectValue(
            fix(jsonPatternMap = adjustedPattern, jsonValueMap = valueMap, resolver = resolver, jsonPattern = this)
        )
    }

    override fun eliminateOptionalKey(value: Value, resolver: Resolver): Value {
        if (value !is JSONObjectValue) return value

        val mandatoryObjectPatternMap  = this.pattern.filterKeys { !isOptional(it) }
        val mandatoryObjectMap = value.jsonObject.mapNotNull { (key, actualValue) ->
            val patternForKey = mandatoryObjectPatternMap[key] ?: return@mapNotNull null
            key to patternForKey.eliminateOptionalKey(actualValue, resolver)
        }.toMap()

        return JSONObjectValue(mandatoryObjectMap)
    }

    override fun addTypeAliasesToConcretePattern(concretePattern: Pattern, resolver: Resolver, typeAlias: String?): Pattern {
        if (additionalProperties is AdditionalProperties.FreeForm && pattern.isEmpty()) return concretePattern
        if (concretePattern !is JSONObjectPattern) throw ContractException("Expected json object type but got ${concretePattern.typeName}")

        val updatedPatternMapWithTypeAliases = concretePattern.pattern.mapValues { (key, concreteChildPattern) ->
            val originalChildPattern =
                this.pattern[key]
                    ?: this.pattern["$key?"]
                    ?: return@mapValues concreteChildPattern
            originalChildPattern.addTypeAliasesToConcretePattern(concreteChildPattern, resolver)
        }

        return concretePattern.copy(
            typeAlias = typeAlias ?: this.typeAlias,
            pattern = updatedPatternMapWithTypeAliases
        )
    }

    override fun fillInTheBlanks(value: Value, resolver: Resolver): ReturnValue<Value> {
        val jsonObject = value as? JSONObjectValue ?: return HasFailure("Can't generate object value from partial of type ${value.displayableType()}")

        val mapWithKeysInPartial = jsonObject.jsonObject.mapValues { (name, value) ->
            val valuePattern = pattern[name] ?: pattern["$name?"] ?: return@mapValues HasFailure<Value>(
                Result.Failure(
                    resolver.mismatchMessages.unexpectedKey("header", name)
                )
            ).breadCrumb(name)

            val returnValue = if (value is StringValue && isPatternToken(value.string)) {
                try {
                    val generatedValue = resolver.generate(typeAlias, name, resolver.getPattern(value.string))
                    val matchResult = valuePattern.matches(generatedValue, resolver)

                    if (matchResult is Result.Failure)
                        HasFailure(matchResult, "Could not generate value for key $name")
                    else
                        HasValue(generatedValue)
                } catch(e: Throwable) {
                    HasException(e)
                }
            } else if (value is ScalarValue) {
                val matchResult = valuePattern.matches(value, resolver)

                val returnValue: ReturnValue<Value> = if (matchResult is Result.Failure)
                    HasFailure(matchResult)
                else
                    HasValue(value)

                returnValue
            } else {
                valuePattern.fillInTheBlanks(value, resolver.plusDictionaryLookupDetails(typeAlias, name))
            }

            returnValue.breadCrumb(name)
        }.mapFold()

        val mapWithMissingKeysGenerated = pattern.filterKeys {
            !it.endsWith("?") && it !in jsonObject.jsonObject
        }.mapValues { (name, valuePattern) ->
            try {
                HasValue(resolver.generate(typeAlias, name, valuePattern))
            } catch(e: Throwable) {
                HasException(e)
            }.breadCrumb(name)
        }.mapFold()

        return mapWithKeysInPartial.combine(mapWithMissingKeysGenerated) { entriesInPartial, missingEntries ->
            jsonObject.copy(jsonObject = entriesInPartial + missingEntries)
        }
    }

    override fun removeKeysNotPresentIn(keys: Set<String>, resolver: Resolver): Pattern {
        if (keys.isEmpty()) return this
        return this.copy(pattern = pattern.filterKeys {
            withoutOptionality(it) in keys
        }.mapKeys {
            withoutOptionality(it.key)
        })
    }

    override fun jsonObjectPattern(resolver: Resolver): JSONObjectPattern? {
        return this
    }

    override fun equals(other: Any?): Boolean = when (other) {
        is JSONObjectPattern -> this.pattern == other.pattern
        else -> false
    }

    override fun resolveSubstitutions(
        substitution: Substitution,
        value: Value,
        resolver: Resolver,
        key: String?
    ): ReturnValue<Value> {
        if(value !is JSONObjectValue)
            return HasFailure(Result.Failure("Cannot resolve substitutions, expected object but got ${value.displayableType()}"))

        val updatedMap = value.jsonObject.mapValues { (key, value) ->
            val pattern = attempt("Could not find key in json object", key) { pattern[key] ?: pattern["$key?"] ?: throw MissingDataException("Could not find key $key") }
            pattern.resolveSubstitutions(substitution, value, resolver, key).breadCrumb(key)
        }

        return updatedMap.mapFold().ifValue { value.copy(it) }
    }

    override fun getTemplateTypes(key: String, value: Value, resolver: Resolver): ReturnValue<Map<String, Pattern>> {
        if(value !is JSONObjectValue)
            return HasFailure(Result.Failure("Cannot resolve data substitutions, expected object but got ${value.displayableType()}"))

        val initialValue: ReturnValue<Map<String, Pattern>> = HasValue(emptyMap())

        return pattern.mapKeys {
            withoutOptionality(it.key)
        }.entries.fold(initialValue) { acc, (key, valuePattern) ->
            value.jsonObject[key]?.let { valueInObject ->
                val additionalTemplateTypes = valuePattern.getTemplateTypes(key, valueInObject, resolver)
                acc.assimilate(additionalTemplateTypes) { data, additional -> data + additional }
            } ?: acc
        }
    }

    override fun encompasses(
        otherPattern: Pattern,
        thisResolver: Resolver,
        otherResolver: Resolver,
        typeStack: TypeStack
    ): Result {
        val thisResolverWithNullType = withNullPattern(thisResolver)
        val otherResolverWithNullType = withNullPattern(otherResolver)

        return when (otherPattern) {
            is ExactValuePattern -> otherPattern.fitsWithin(
                listOf(this),
                otherResolverWithNullType,
                thisResolverWithNullType,
                typeStack
            )

            is TabularPattern -> {
                mapEncompassesMap(
                    pattern,
                    otherPattern.pattern,
                    thisResolverWithNullType,
                    otherResolverWithNullType,
                    typeStack
                )
            }

            is JSONObjectPattern -> {
                val propertyLimitResults: List<Result.Failure> = olderPropertyLimitsEncompassNewer(this, otherPattern)
                mapEncompassesMap(
                    pattern,
                    otherPattern.pattern,
                    thisResolverWithNullType,
                    otherResolverWithNullType,
                    typeStack,
                    propertyLimitResults
                )
            }

            else -> Result.Failure("Expected json type, got ${otherPattern.typeName}")
        }
    }

    private fun olderPropertyLimitsEncompassNewer(
        newer: JSONObjectPattern,
        older: JSONObjectPattern
    ): List<Result.Failure> {
        val minPropertiesResult =
            if (older.minProperties != null && newer.minProperties != null && older.minProperties > newer.minProperties)
                Result.Failure("Expected at least ${older.minProperties} properties, got ${newer.minProperties}")
            else
                Result.Success()

        val maxPropertiesResult =
            if (older.maxProperties != null && newer.maxProperties != null && older.maxProperties < newer.maxProperties)
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

    private fun shouldMakePropertyMandatory(pattern: Pattern, resolver: Resolver): Boolean {
        if (!resolver.allPatternsAreMandatory) return false

        val patternToCheck = when(pattern) {
            is ListPattern -> pattern.typeAlias?.let { pattern } ?: pattern.pattern
            else -> pattern.typeAlias?.let { pattern } ?: this
        }

        return !resolver.hasSeenPattern(patternToCheck)
    }

    private fun addPatternToSeen(pattern: Pattern, resolver: Resolver): Resolver {
        val patternToAdd = when(pattern) {
            is ListPattern -> pattern.typeAlias?.let { pattern } ?: pattern.pattern
            else -> pattern.typeAlias?.let { pattern } ?: this
        }

        return resolver.addPatternAsSeen(patternToAdd)
    }

    override fun matches(sampleData: Value?, resolver: Resolver): Result {
        val resolverWithNullType = withNullPattern(resolver)
        if (sampleData !is JSONObjectValue)
            return mismatchResult("JSON object", sampleData, resolver.mismatchMessages)

        val minCountErrors: List<Result.Failure> = if (sampleData.jsonObject.keys.size < (minProperties ?: 0))
            listOf(Result.Failure("Expected at least $minProperties properties, got ${sampleData.jsonObject.keys.size}"))
        else
            emptyList()

        val maxCountErrors: List<Result.Failure> =
            if (sampleData.jsonObject.keys.size > (maxProperties ?: Int.MAX_VALUE))
                listOf(Result.Failure("Expected at most $maxProperties properties, got ${sampleData.jsonObject.keys.size}"))
            else
                emptyList()

        val adjustedPattern = pattern.mapKeys {
            if (shouldMakePropertyMandatory(it.value, resolver)) {
                withoutOptionality(it.key)
            } else it.key
        }.let { additionalProperties.updatePatternMap(it, sampleData.jsonObject) }

        val keyErrors: List<Result.Failure> = resolverWithNullType.findKeyErrorList(adjustedPattern, sampleData.jsonObject).map {
            if (pattern[it.name] != null) {
                it.missingKeyToResult("key", resolver.mismatchMessages).breadCrumb(it.name)
            } else it.missingOptionalKeyToResult("key", resolver.mismatchMessages).breadCrumb(it.name)
        }

        val updatedResolver = resolverWithNullType.addPatternAsSeen(this)

        data class ResultWithDiscriminatorStatus(val result: Result, val isDiscriminator: Boolean)

        val resultsWithDiscriminator: List<ResultWithDiscriminatorStatus> =
            mapZip(adjustedPattern, sampleData.jsonObject).map { (key, patternValue, sampleValue) ->
                val innerResolver = addPatternToSeen(patternValue, updatedResolver)
                val result = innerResolver.matchesPattern(key, patternValue, sampleValue).breadCrumb(key)

                val isDiscrimintor = patternValue.isDiscriminator()

                val cleanedUpResult = if(!isDiscrimintor && result is Result.Failure) {
                    result.removeReasonsFromCauses()
                } else {
                    result
                }

                ResultWithDiscriminatorStatus(cleanedUpResult, isDiscrimintor)
            }

        val results: List<Result.Failure> = resultsWithDiscriminator
            .map { it.result }
            .filterIsInstance<Result.Failure>()
            .distinctBy {
                it.reportString()
            }

        val failures: List<Result.Failure> = minCountErrors + maxCountErrors + keyErrors + results

        return if (failures.isEmpty())
            Result.Success()
        else {
            val discriminatorMatchFound = resultsWithDiscriminator.any { it.isDiscriminator && it.result.isSuccess() }

            val combinedFailure = Result.Failure.fromFailures(failures)

            if(discriminatorMatchFound)
                return combinedFailure.withFailureReason(FailureReason.FailedButDiscriminatorMatched)

            val discriminatorMisMatchFound = resultsWithDiscriminator.any { it.isDiscriminator && !it.result.isSuccess() }
            if(discriminatorMisMatchFound)
                return combinedFailure.withFailureReason(FailureReason.DiscriminatorMismatch)

            return combinedFailure.withFailureReason(FailureReason.FailedButObjectTypeMatched)
        }
    }

    override fun generate(resolver: Resolver): JSONObjectValue {
        val pattern = if (resolver.allowOnlyMandatoryKeysInJsonObject) {
            getPatternWithOmittedOptionalFields(this.pattern, resolver)
        } else {
            this.pattern
        }

        return JSONObjectValue(
            generate(
                selectPropertiesWithinMaxAndMin(pattern, minProperties, maxProperties),
                withNullPattern(resolver),
                this
            )
        )
    }

    private fun getPatternWithOmittedOptionalFields(pattern: Map<String, Pattern>, resolver: Resolver): Map<String, Pattern> {
        return pattern.filterKeys { it.endsWith("?").not() }.map { entry ->
            val (key, valuePattern) = entry

            resolvedHop(valuePattern, resolver).let { resolvedValuePattern ->
                if (resolvedValuePattern !is JSONObjectPattern) {
                    return@map entry.toPair()
                }

                key to resolvedValuePattern.copy(
                    pattern = getPatternWithOmittedOptionalFields(resolvedValuePattern.pattern, resolver)
                )
            }
        }.toMap()
    }

    override fun newBasedOn(row: Row, resolver: Resolver): Sequence<ReturnValue<Pattern>> =
        allOrNothingCombinationIn(
            pattern.minus("..."),
            resolver.resolveRow(row),
            minProperties,
            maxProperties
        ) { pattern ->
            newMapBasedOn(pattern, row, withNullPattern(resolver))
        }.map { it: ReturnValue<Map<String, Pattern>> ->
            it.ifValue {
                toJSONObjectPattern(it.mapKeys { (key, _) ->
                    withoutOptionality(key)
                }, typeAlias)
            }
    }

    override fun newBasedOn(resolver: Resolver): Sequence<JSONObjectPattern> =
        allOrNothingCombinationIn(
            pattern.minus("..."),
            Row(),
            null,
            null, returnValues { pattern: Map<String, Pattern> -> newBasedOn(pattern, withNullPattern(resolver)) }
        ).map { it.value }.map { toJSONObjectPattern(it, typeAlias) }

    override fun negativeBasedOn(row: Row, resolver: Resolver, config: NegativePatternConfiguration): Sequence<ReturnValue<Pattern>> =
        allOrNothingCombinationIn(pattern.minus("...")) { pattern ->
            AllNegativePatterns().negativeBasedOn(pattern, row, withNullPattern(resolver), config)
        }.map { it.ifValue { toJSONObjectPattern(it, typeAlias) } }

    override fun parse(value: String, resolver: Resolver): Value = parsedJSONObject(value, resolver.mismatchMessages)
    override fun hashCode(): Int = pattern.hashCode()

    fun updateWithDiscriminatorValue(discriminatorPropertyName: String, discriminatorValue: String, resolver: Resolver): ReturnValue<Pattern> {
        val actualDiscriminatorPropertyName =
            if(discriminatorPropertyName in pattern)
                discriminatorPropertyName
            else if("$discriminatorPropertyName?" in pattern)
                    "$discriminatorPropertyName?"
            else
                return HasValue(this)

        val discriminatorPattern = pattern.getValue(actualDiscriminatorPropertyName)

        if(discriminatorPattern is ExactValuePattern && discriminatorPattern.discriminator)
            return HasValue(this)

        val candidateDiscriminatorValue = discriminatorPattern.parse(discriminatorValue, resolver)
        val matchResult = discriminatorPattern.matches(candidateDiscriminatorValue, resolver)

        if(matchResult is Result.Failure)
            return HasFailure(matchResult.breadCrumb(discriminatorPropertyName))

        val updatedPattern = pattern.plus(actualDiscriminatorPropertyName to ExactValuePattern(candidateDiscriminatorValue, discriminator = true))

        return HasValue(this.copy(updatedPattern))
    }

    override val typeName: String = "json object"

    fun keysInNonOptionalFormat(): Set<String> {
        return this.pattern.map { withoutOptionality(it.key) }.toSet()
    }

    fun patternForKey(key: String): Pattern? {
        return pattern[withoutOptionality(key)] ?: pattern[withOptionality(key)]
    }
}

fun generate(jsonPatternMap: Map<String, Pattern>, resolver: Resolver, jsonPattern: JSONObjectPattern): Map<String, Value> {
    val resolverWithNullType = withNullPattern(resolver)

    val optionalProps = jsonPatternMap.keys.filter { isOptional(it) }.map { withoutOptionality(it) }

    return jsonPatternMap
        .mapKeys { entry -> withoutOptionality(entry.key) }
        .mapValues { (key, pattern) ->
            attempt(breadCrumb = key) {
                // Handle cycle (represented by null value) by marking this property as removable
                val canBeOmitted = optionalProps.contains(key)

                val value = Optional.ofNullable(
                    resolverWithNullType.withCyclePrevention(
                        jsonPattern,
                        key,
                        canBeOmitted
                    ) {
                        it.generate(jsonPattern.typeAlias, key, pattern)
                    })

                if (value.isPresent || resolverWithNullType.hasSeenLookupPath(jsonPattern, key))
                    return@attempt value

                val resolverWithCycleMarker = resolverWithNullType.cyclePast(jsonPattern, key)

                val valueWithOneCycle = Optional.ofNullable(
                    resolverWithCycleMarker.withCyclePrevention(
                        jsonPattern,
                        key,
                        canBeOmitted
                    ) {
                        it.generate(jsonPattern.typeAlias, key, pattern)
                    })

                valueWithOneCycle
            }
        }
        .filterValues { it.isPresent }
        .mapValues { (_, opt) -> opt.get() }
}

fun fix(jsonPatternMap: Map<String, Pattern>, jsonValueMap: Map<String, Value>, resolver: Resolver, jsonPattern: JSONObjectPattern): Map<String, Value> {
    val defaultKeyToPatternValuePair = adjustedPattern(jsonPatternMap, resolver)
        .map { (key, pattern) -> key to Pair(pattern, NullValue) }
        .toMap()

    val keyToPatternValuePair = jsonValueMap.mapNotNull { (key, value) ->
        val pattern = jsonPatternMap[key] ?: jsonPatternMap["$key?"]
        if (pattern == null && resolver.findKeyErrorCheck.unexpectedKeyCheck is ValidateUnexpectedKeys) return@mapNotNull null
        key to Pair(pattern, value)
    }.toMap()

    val finalMap = defaultKeyToPatternValuePair.plus(keyToPatternValuePair)
    return finalMap.mapValues { (key, patternToValue) ->
        val (pattern, value) = patternToValue
        if (pattern == null) return@mapValues Optional.of(value)

        attempt(breadCrumb = key) {
            val returnNullOnCycle = jsonPatternMap.containsKey("$key?")
            val fixedValue = Optional.ofNullable(
                resolver.withCyclePrevention(jsonPattern, key, returnNullOnCycle) {
                    it.fix(jsonPattern.typeAlias, key, pattern, value)
                }
            )

            if (fixedValue.isPresent || resolver.hasSeenLookupPath(jsonPattern, key)) return@attempt fixedValue

            val resolverWithCycleMarker = resolver.cyclePast(jsonPattern, key)
            Optional.ofNullable(
                resolverWithCycleMarker.withCyclePrevention(jsonPattern, key, returnNullOnCycle) {
                    it.fix(jsonPattern.typeAlias, key, pattern, value)
                }
            )
        }
    }
    .filterValues { it.isPresent }
    .mapValues { (_, opt) -> opt.get() }
}

private fun adjustedPattern(jsonPatternMap: Map<String, Pattern>, resolver: Resolver): Map<String, Pattern> {
    if (!resolver.allPatternsAreMandatory) return jsonPatternMap.filterKeys { !isOptional(it) }
    return jsonPatternMap.mapKeys { withoutOptionality(it.key) }
}

private fun selectPropertiesWithinMaxAndMin(
    jsonPattern: Map<String, Pattern>,
    minProperties: Int?,
    maxProperties: Int?
): Map<String, Pattern> {
    val withAtMostMaxProperties = selectAtMostMaxProperties(jsonPattern, maxProperties)

    return selectAtMostMinProperties(withAtMostMaxProperties, minProperties)
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

            if (selectedKeys.size < minProperties)
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
    if (mandatoryKeys.size > maxProperties)
        throw ContractException("Cannot generate a JSON object with at most $maxProperties properties as there are ${mandatoryKeys.size} mandatory properties in the specification.")

    val optionalKeys = properties.keys.filter { isOptional(it) }
    val countOfOptionalKeysToPick = maxProperties - mandatoryKeys.size
    val selectedOptionalKeys = optionalKeys.shuffled().take(countOfOptionalKeysToPick)
    val selectedKeys = mandatoryKeys + selectedOptionalKeys

    properties.filterKeys { it in selectedKeys }
} else
    properties

internal fun mapEncompassesMap(
    pattern: Map<String, Pattern>,
    otherPattern: Map<String, Pattern>,
    thisResolverWithNullType: Resolver,
    otherResolverWithNullType: Resolver,
    typeStack: TypeStack = emptySet(),
    previousResults: List<Result.Failure> = emptyList()
): Result {
    val myRequiredKeys = pattern.keys.filter { !isOptional(it) }
    val otherRequiredKeys = otherPattern.keys.filter { !isOptional(it) }

    val missingFixedKeyErrors: List<Result.Failure> =
        myRequiredKeys.filter { it !in otherRequiredKeys }.map { missingFixedKey ->
            MissingKeyError(missingFixedKey).missingKeyToResult("key", thisResolverWithNullType.mismatchMessages)
                .breadCrumb(withoutOptionality(missingFixedKey))
        }

    val keyErrors = pattern.keys.map { key ->
        val bigger = pattern.getValue(key)
        val smaller = otherPattern[key] ?: otherPattern[withoutOptionality(key)]

        when {
            smaller != null -> biggerEncompassesSmaller(
                bigger,
                smaller,
                thisResolverWithNullType,
                otherResolverWithNullType,
                typeStack
            ).breadCrumb(withoutOptionality(key))

            else -> Result.Success()
        }
    }

    return Result.fromResults(previousResults + missingFixedKeyErrors + keyErrors)
}
