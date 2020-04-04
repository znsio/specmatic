package run.qontract.core.pattern

import run.qontract.core.Resolver
import run.qontract.core.Result
import run.qontract.core.value.EmptyString
import run.qontract.core.value.NullValue
import run.qontract.core.value.StringValue
import run.qontract.core.value.Value
import java.nio.charset.StandardCharsets
import java.util.*

class StringPattern : Pattern {
    override fun matches(sampleData: Value?, resolver: Resolver): Result {
        return when(sampleData) {
            is StringValue, is EmptyString -> Result.Success()
            is NullValue -> Result.Failure("null is not  String")
            else -> Result.Failure("${sampleData?.value} is not a String")
        }
    }

    override fun generate(resolver: Resolver): Value {
        val array = ByteArray(5)
        val random = Random()
        for (index in array.indices) {
            array[index] = (random.nextInt(25) + 65).toByte()
        }
        return StringValue(String(array, StandardCharsets.UTF_8))
    }

    override fun newBasedOn(row: Row, resolver: Resolver): List<Pattern> = listOf(this)
    override fun parse(value: String, resolver: Resolver): Value = StringValue(value)

    override val pattern: Any = "(string)"
    override fun toString(): String = pattern.toString()
}