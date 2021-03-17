package `in`.specmatic.core.pattern

import `in`.specmatic.core.Resolver
import `in`.specmatic.core.Result
import `in`.specmatic.core.mismatchResult
import `in`.specmatic.core.value.EmptyString
import `in`.specmatic.core.value.JSONArrayValue
import `in`.specmatic.core.value.StringValue
import `in`.specmatic.core.value.Value
import java.nio.charset.StandardCharsets
import java.util.*

object StringPattern : Pattern, ScalarType {
    override fun matches(sampleData: Value?, resolver: Resolver): Result {
        return when(sampleData) {
            is StringValue, EmptyString -> Result.Success()
            else -> mismatchResult("string", sampleData)
        }
    }

    override fun encompasses(otherPattern: Pattern, thisResolver: Resolver, otherResolver: Resolver, typeStack: TypeStack): Result {
        return encompasses(this, otherPattern, thisResolver, otherResolver, typeStack)
    }

    override fun listOf(valueList: List<Value>, resolver: Resolver): Value {
        return JSONArrayValue(valueList)
    }

    override val typeAlias: String?
        get() = null

    override fun generate(resolver: Resolver): Value = StringValue(randomString())

    override fun newBasedOn(row: Row, resolver: Resolver): List<Pattern> = listOf(this)
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
