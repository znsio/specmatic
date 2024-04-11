package `in`.specmatic.core.pattern

import `in`.specmatic.core.Resolver
import `in`.specmatic.core.Result
import `in`.specmatic.core.mismatchResult
import `in`.specmatic.core.value.JSONArrayValue
import `in`.specmatic.core.value.StringValue
import `in`.specmatic.core.value.Value
import java.nio.charset.StandardCharsets
import java.util.*

data class StringPattern (
    override val typeAlias: String? = null,
    val minLength: Int? = null,
    val maxLength: Int? = null,
    override val example: String? = null
) : Pattern, ScalarType, HasDefaultExample {
    init {
        require(minLength?.let { maxLength?.let { minLength <= maxLength } }
            ?: true) { """maxLength cannot be less than minLength""" }
    }

    override fun matches(sampleData: Value?, resolver: Resolver): Result {
        return when (sampleData) {
            is StringValue -> {
                if (minLength != null && sampleData.toStringLiteral().length < minLength) return mismatchResult(
                    "string with minLength $minLength",
                    sampleData, resolver.mismatchMessages
                )
                if (maxLength != null && sampleData.toStringLiteral().length > maxLength) return mismatchResult(
                    "string with maxLength $maxLength",
                    sampleData, resolver.mismatchMessages
                )
                return Result.Success()
            }
            else -> mismatchResult("string", sampleData, resolver.mismatchMessages)
        }
    }

    override fun encompasses(
        otherPattern: Pattern,
        thisResolver: Resolver,
        otherResolver: Resolver,
        typeStack: TypeStack
    ): Result {
        return encompasses(this, otherPattern, thisResolver, otherResolver, typeStack)
    }

    override fun listOf(valueList: List<Value>, resolver: Resolver): Value {
        return JSONArrayValue(valueList)
    }

    private val randomStringLength: Int =
        when {
            minLength != null && 5 < minLength -> minLength
            maxLength != null && 5 > maxLength -> maxLength
            else -> 5
        }
    
    override fun generate(resolver: Resolver): Value = resolver.resolveExample(example, this) ?: StringValue(randomString(randomStringLength))

    override fun newBasedOn(row: Row, resolver: Resolver): Sequence<Pattern> = sequenceOf(this)
    override fun newBasedOn(resolver: Resolver): Sequence<Pattern> = sequenceOf(this)

    override fun negativeBasedOnR(row: Row, resolver: Resolver): Sequence<ReturnValue<Pattern>> {
        return sequenceOf(NullPattern, NumberPattern(), BooleanPattern()).map {
            HasValue(it, "Expected type in spec was $typeName, trying out a ${it.typeName}")
        }
    }

    override fun negativeBasedOn(row: Row, resolver: Resolver): Sequence<Pattern> {
        return negativeBasedOnR(row, resolver).map { it.value }
    }

    override fun parse(value: String, resolver: Resolver): Value = StringValue(value)
    override val typeName: String = "string"

    override val pattern: Any = "(string)"
    override fun toString(): String = pattern.toString()
}

fun randomString(length: Int = 5): String {
    val array = ByteArray(length)
    val random = Random()
    for (index in array.indices) {
        array[index] = (random.nextInt(25) + 65).toByte()
    }
    return String(array, StandardCharsets.UTF_8)
}
