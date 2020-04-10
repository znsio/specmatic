package run.qontract.core.pattern

import run.qontract.core.Resolver
import run.qontract.core.Result
import run.qontract.core.mismatchResult
import run.qontract.core.value.EmptyString
import run.qontract.core.value.StringValue
import run.qontract.core.value.Value
import java.nio.charset.StandardCharsets
import java.util.*

class StringPattern : Pattern {
    override fun matches(sampleData: Value?, resolver: Resolver): Result {
        return when(sampleData) {
            is StringValue, is EmptyString -> Result.Success()
            else -> mismatchResult("string", sampleData)
        }
    }

    override fun generate(resolver: Resolver): Value = StringValue(randomString())

    override fun newBasedOn(row: Row, resolver: Resolver): List<Pattern> = listOf(this)
    override fun parse(value: String, resolver: Resolver): Value = StringValue(value)
    override fun matchesPattern(pattern: Pattern, resolver: Resolver): Boolean = pattern is StringPattern
    override val description: String = "string"

    override val pattern: Any = "(string)"
    override fun toString(): String = pattern.toString()
}

fun randomString(): String {
    val array = ByteArray(5)
    val random = Random()
    for (index in array.indices) {
        array[index] = (random.nextInt(25) + 65).toByte()
    }
    val randomStringValue = String(array, StandardCharsets.UTF_8)
    return randomStringValue
}
