package `in`.specmatic.core

import `in`.specmatic.core.pattern.ContractException
import `in`.specmatic.core.pattern.HasDefaultExample
import `in`.specmatic.core.pattern.Pattern
import `in`.specmatic.core.pattern.attempt
import `in`.specmatic.core.utilities.exceptionCauseMessage
import `in`.specmatic.core.value.JSONArrayValue
import `in`.specmatic.core.value.Value

object UseDefaultExample : DefaultExampleResolver {
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
            ?: throw ContractException(
                "Example \"$example\" does not match:\n${
                    Result.fromResults(matchResults.map { it.first }.toList()).reportString()
                }"
            )
    }

    override fun theDefaultExampleForThisKeyIsNotOmit(valuePattern: Pattern): Boolean {
        if(valuePattern !is HasDefaultExample)
            return true

        val example = valuePattern.example

        if(example is String)
            return example !in OMIT

        return true
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