package `in`.specmatic.core

import `in`.specmatic.core.pattern.*
import `in`.specmatic.core.value.JSONArrayValue
import `in`.specmatic.core.value.NullValue
import `in`.specmatic.core.value.StringValue
import `in`.specmatic.core.value.Value

data class URLPathSegmentPattern(override val pattern: Pattern, override val key: String? = null, override val typeAlias: String? = null) : Pattern, Keyed {
    override fun matches(sampleData: Value?, resolver: Resolver): Result =
            resolver.matchesPattern(key, pattern, sampleData ?: NullValue)

    override fun generate(resolver: Resolver): Value {
        return resolver.withCyclePrevention(pattern) { cyclePreventedResolver ->
            if (key != null)
                cyclePreventedResolver.generate(key, pattern)
            else pattern.generate(cyclePreventedResolver)
        }
    }

    override fun newBasedOn(row: Row, resolver: Resolver): List<URLPathSegmentPattern> =
        resolver.withCyclePrevention(pattern) { cyclePreventedResolver ->
            pattern.newBasedOn(row, cyclePreventedResolver).map { URLPathSegmentPattern(it, key) }
        }

    override fun newBasedOn(resolver: Resolver): List<URLPathSegmentPattern> =
        resolver.withCyclePrevention(pattern) { cyclePreventedResolver ->
            pattern.newBasedOn(cyclePreventedResolver).map { URLPathSegmentPattern(it, key) }
        }

    override fun negativeBasedOn(row: Row, resolver: Resolver): List<Pattern> {
        return when (pattern) {
            is ExactValuePattern -> emptyList()
            is StringPattern -> emptyList()
            else -> resolver.withCyclePrevention(pattern) { cyclePreventedResolver ->
                pattern.negativeBasedOn(row, cyclePreventedResolver).filterNot { it is NullPattern }.map { URLPathSegmentPattern(it, key) }
            }
        }
    }

    override fun parse(value: String, resolver: Resolver): Value = pattern.parse(value, resolver)

    override fun encompasses(otherPattern: Pattern, thisResolver: Resolver, otherResolver: Resolver, typeStack: TypeStack): Result {
        if(otherPattern !is URLPathSegmentPattern)
            return Result.Failure("Expected url type, got ${otherPattern.typeName}")

        return otherPattern.pattern.fitsWithin(patternSet(thisResolver), otherResolver, thisResolver, typeStack)
    }

    override fun listOf(valueList: List<Value>, resolver: Resolver): Value {
        return JSONArrayValue(valueList)
    }

    fun tryParse(token: String, resolver: Resolver): Value {
        return try {
            this.pattern.parse(token, resolver)
        } catch (e: Throwable) {
            if (isPatternToken(token) && token.contains(":"))
                StringValue(withPatternDelimiters(withoutPatternDelimiters(token).split(":".toRegex(), 2)[1]))
            else
                StringValue(token)
        }
    }

    override val typeName: String = "url path"
}