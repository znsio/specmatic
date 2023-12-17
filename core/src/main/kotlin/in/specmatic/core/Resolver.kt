package `in`.specmatic.core

import `in`.specmatic.core.pattern.*
import `in`.specmatic.core.utilities.exceptionCauseMessage
import `in`.specmatic.core.value.JSONArrayValue
import `in`.specmatic.core.value.StringValue
import `in`.specmatic.core.value.True
import `in`.specmatic.core.value.Value

val actualMatch: (resolver: Resolver, factKey: String?, pattern: Pattern, sampleValue: Value) -> Result = { resolver: Resolver, factKey: String?, pattern: Pattern, sampleValue: Value ->
    resolver.actualPatternMatch(factKey, pattern, sampleValue)
}

val matchAnything: (resolver: Resolver, factKey: String?, pattern: Pattern, sampleValue: Value) -> Result = { resolver: Resolver, factKey: String?, pattern: Pattern, sampleValue: Value ->
    Result.Success()
}

val actualParse: (resolver: Resolver, pattern: Pattern, rowValue: String) -> Value = { resolver: Resolver, pattern: Pattern, rowValue: String ->
    resolver.actualParse(pattern, rowValue)
}

val alwaysReturnStringValue: (resolver: Resolver, pattern: Pattern, rowValue: String) -> Value = { resolver: Resolver, pattern: Pattern, rowValue: String ->
    StringValue(rowValue)
}

data class Resolver(
    val factStore: FactStore = CheckFacts(),
    val mockMode: Boolean = false,
    val newPatterns: Map<String, Pattern> = emptyMap(),
    val findKeyErrorCheck: KeyCheck = DefaultKeyCheck,
    val context: Map<String, String> = emptyMap(),
    val mismatchMessages: MismatchMessages = DefaultMismatchMessages,
    val isNegative: Boolean = false,
    val patternMatchStrategy: (resolver: Resolver, factKey: String?, pattern: Pattern, sampleValue: Value) -> Result = actualMatch,
    val parseStrategy: (resolver: Resolver, pattern: Pattern, rowValue: String) -> Value = actualParse,
    val generativeTestingEnabled: Boolean = false,
    val cyclePreventionStack: List<Pattern> = listOf(),
    val defaultExampleResolver: DefaultExampleResolver = DoNotUseDefaultExample()
) {
    constructor(facts: Map<String, Value> = emptyMap(), mockMode: Boolean = false, newPatterns: Map<String, Pattern> = emptyMap()) : this(CheckFacts(facts), mockMode, newPatterns)
    constructor() : this(emptyMap(), false)

    val patterns = builtInPatterns.plus(newPatterns)

    fun withUnexpectedKeyCheck(unexpectedKeyCheck: UnexpectedKeyCheck): Resolver {
        return this.copy(findKeyErrorCheck = this.findKeyErrorCheck.withUnexpectedKeyCheck(unexpectedKeyCheck))
    }

    fun disableOverrideUnexpectedKeycheck(): Resolver {
        return this.copy(findKeyErrorCheck = this.findKeyErrorCheck.disableOverrideUnexpectedKeycheck())
    }

    fun findKeyError(pattern: Map<String, Any>, actual: Map<String, Any>): KeyError? {
        return findKeyErrorList(pattern, actual).firstOrNull()
    }

    fun findKeyErrorList(pattern: Map<String, Any>, actual: Map<String, Any>): List<KeyError> {
        return findKeyErrorCheck.validateAll(pattern, actual)
    }

    fun matchesPattern(factKey: String?, pattern: Pattern, sampleValue: Value): Result {
        return patternMatchStrategy(this, factKey, pattern, sampleValue)
    }

    fun actualPatternMatch(factKey: String?, pattern: Pattern, sampleValue: Value): Result {
        if (mockMode
                && sampleValue is StringValue
                && isPatternToken(sampleValue.string)
                && pattern.encompasses(getPattern(sampleValue.string), this, this).isSuccess())
            return Result.Success()

        return pattern.matches(sampleValue, this).ifSuccess {
            if (factKey != null && factStore.has(factKey)) {
                val result = factStore.match(sampleValue, factKey)

                if(result is Result.Failure) {
                    result.reason("Resolver was not able to match fact $factKey with value $sampleValue.")
                }
            }

            Result.Success()
        }
    }

    fun getPattern(patternValue: String): Pattern =
        when {
            isPatternToken(patternValue) -> {
                val resolvedPattern = patterns[patternValue] ?: parsedPattern(patternValue, null)
                when {
                    resolvedPattern is DeferredPattern && resolvedPattern.pattern == patternValue ->
                        throw ContractException("Type $patternValue does not exist")
                    else -> resolvedPattern
                }
            }
            else -> throw ContractException("$patternValue is not a type")
        }

    fun <T> withCyclePrevention(pattern: Pattern, toResult: (r: Resolver) -> T) : T {
        return withCyclePrevention(pattern, false, toResult)!!
    }

    /**
     * Returns non-null if no cycle. If there is a cycle then ContractException(cycle=true) is thrown - unless
     * returnNullOnCycle=true in which case null is returned. Null is never returned if returnNullOnCycle=false.
     */
    fun <T> withCyclePrevention(pattern: Pattern, returnNullOnCycle: Boolean = false, toResult: (r: Resolver) -> T) : T? {
        val count = cyclePreventionStack.filter { it == pattern }.size
        val newCyclePreventionStack = cyclePreventionStack.plus(pattern)

        try {
            if (count > 1)
                // Terminate what would otherwise be an infinite cycle.
                throw ContractException("Invalid pattern cycle: $newCyclePreventionStack", isCycle = true)

            return toResult(copy(cyclePreventionStack = newCyclePreventionStack))
        } catch (e: ContractException) {
            if (!e.isCycle || !returnNullOnCycle)
                throw e

            // Returns null if (and only if) a cycle has been detected and returnNullOnCycle=true
            return null
        }
    }

    fun generate(factKey: String, pattern: Pattern): Value {
        if (!factStore.has(factKey))
            return pattern.generate(this)

        return when(val fact = factStore.get(factKey)) {
            is StringValue ->
                try {
                    pattern.parse(fact.string, this)
                } catch (e: Throwable) {
                    throw ContractException("""Value $fact in fact $factKey is not a $pattern""")
                }
            True -> pattern.generate(this)
            else -> {
                when(val matchResult = pattern.matches(fact, this)) {
                    is Result.Failure -> throw ContractException(matchResult.toFailureReport())
                    else -> fact
                }
            }
        }
    }

    fun findKeyErrorListCaseInsensitive(pattern: Map<String, Pattern>, actual: Map<String, StringValue>): List<KeyError> {
        return findKeyErrorCheck.validateAllCaseInsensitive(pattern, actual)
    }

    fun parse(pattern: Pattern, rowValue: String): Value {
        return parseStrategy(this, pattern, rowValue)
    }

    fun actualParse(pattern: Pattern, rowValue: String): Value {
        return pattern.parse(rowValue, this)
    }

    fun invalidRequestResolver(): Resolver {
        return this.copy(patternMatchStrategy = matchAnything, parseStrategy = alwaysReturnStringValue)
    }

    fun generatedPatternsForGenerativeTests(pattern: Pattern, key: String): List<Pattern> =
        // TODO generate value outside
        if(generativeTestingEnabled) {
            withCyclePrevention(pattern, isOptional(key)) { cyclePreventedResolver ->
                pattern.newBasedOn(Row(), cyclePreventedResolver)
            } ?: emptyList()
        } else {
            emptyList()
        }

    fun resolveExample(example: String?, pattern: Pattern): Value? {
        return defaultExampleResolver.resolveExample(example, pattern, this)
    }

    fun resolveExample(example: String?, pattern: List<Pattern>): Value? {
        return defaultExampleResolver.resolveExample(example, pattern, this)
    }

    fun resolveExample(example: List<String?>?, pattern: Pattern): JSONArrayValue? {
        return defaultExampleResolver.resolveExample(example, pattern, this)
    }

    fun generateHttpRequests(body: Pattern, row: Row, requestBodyAsIs: Pattern, value: Value): List<Pattern> {
        // TODO generate value outside
        return if(this.generativeTestingEnabled) {
            val requestsFromFlattenedRow: List<Pattern> =
                this.withCyclePrevention(body) { cyclePreventedResolver ->
                    body.newBasedOn(row.flattenRequestBodyIntoRow(), cyclePreventedResolver)
                }

            if(requestsFromFlattenedRow.none { p -> p.encompasses(requestBodyAsIs, this, this, emptySet()) is Result.Success }) {
                requestsFromFlattenedRow.plus(listOf(requestBodyAsIs))
            } else {
                requestsFromFlattenedRow
            }
        } else {
            listOf(ExactValuePattern(value))
        }
    }

    fun generateHttpRequests(body: Pattern, row: Row): List<Pattern> {
        // TODO generate value outside
        return if(this.generativeTestingEnabled) {
            val vanilla = this.withCyclePrevention(body) { cyclePreventedResolver ->
                body.newBasedOn(Row(), cyclePreventedResolver)
            }
            val fromExamples = this.withCyclePrevention(body) { cyclePreventedResolver ->
                body.newBasedOn(row, cyclePreventedResolver)
            }
            val remainingVanilla = vanilla.filterNot { vanillaType ->
                fromExamples.any { typeFromExamples ->
                    vanillaType.encompasses(
                        typeFromExamples,
                        this,
                        this
                    ).isSuccess()
                }
            }

            fromExamples.plus(remainingVanilla)
        } else {
            this.withCyclePrevention(body) { cyclePreventedResolver ->
                body.newBasedOn(row, cyclePreventedResolver)
            }
        }

    }

    fun resolveRow(row: Row): Row {
        return if(this.generativeTestingEnabled) Row() else row
    }

    fun generateKeySubLists(key: String, subList: List<String>): List<List<String>> {
        return if(this.generativeTestingEnabled && isOptional(key)) {
            listOf(subList, subList + key)
        } else listOf(subList + key)
    }
}

interface DefaultExampleResolver {
    fun resolveExample(example: String?, pattern: Pattern, resolver: Resolver): Value?
    fun resolveExample(example: List<String?>?, pattern: Pattern, resolver: Resolver): JSONArrayValue?
    fun resolveExample(example: String?, pattern: List<Pattern>, resolver: Resolver): Value?
}

class UseDefaultExample : DefaultExampleResolver {
    override fun resolveExample(example: String?, pattern: Pattern, resolver: Resolver): Value? {
        if(example == null)
            return null

        val value = pattern.parse(example, resolver)
        val exampleMatchResult = pattern.matches(value, Resolver())

        if(exampleMatchResult.isSuccess())
            return value

        throw ContractException("Example \"$example\" does not match ${pattern.typeName} type")
    }

    override fun resolveExample(example: String?, pattern: List<Pattern>, resolver: Resolver): Value? {
        if(example == null)
            return null

        val matchResults = pattern.asSequence().map {
            try {
                val value = it.parse(example, Resolver())
                Pair(it.matches(value, Resolver()), value)
            } catch(e: Throwable) {
                Pair(Result.Failure(exceptionCauseMessage(e)), null)
            }
        }

        return matchResults.firstOrNull { it.first.isSuccess() }?.second
            ?: throw ContractException("Example \"$example\" does not match:\n${Result.fromResults(matchResults.map { it.first }.toList()).reportString()}")
    }

    override fun resolveExample(example: List<String?>?, pattern: Pattern, resolver: Resolver): JSONArrayValue? {
        if(example == null)
            return null

        val items = example.mapIndexed { index, s ->
            attempt(breadCrumb = "[$index (example)]") {
                pattern.parse(s ?: "", resolver)
            }
        }

        return JSONArrayValue(items)
    }
}

class DoNotUseDefaultExample : DefaultExampleResolver {
    override fun resolveExample(example: String?, pattern: Pattern, resolver: Resolver): Value? {
        return null
    }

    override fun resolveExample(example: List<String?>?, pattern: Pattern, resolver: Resolver): JSONArrayValue? {
        return null
    }

    override fun resolveExample(example: String?, pattern: List<Pattern>, resolver: Resolver): Value? {
        return null
    }

}