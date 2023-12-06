package `in`.specmatic.core.pattern

import `in`.specmatic.core.Resolver
import `in`.specmatic.core.Result
import `in`.specmatic.core.value.JSONArrayValue
import `in`.specmatic.core.value.StringValue
import `in`.specmatic.core.value.Value
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter

private const val RFC3339_DATETIME_FORMAT = "yyyy-MM-dd"

object DatePattern : Pattern, ScalarType {
    override fun matches(sampleData: Value?, resolver: Resolver): Result = when (sampleData) {
        is StringValue -> resultOf {
            parse(sampleData.string, resolver)
            Result.Success()
        }
        else -> Result.Failure("Date types can only be represented using strings")
    }

    override fun generate(resolver: Resolver): StringValue = currentDateInRFC3339Format()

    override fun newBasedOn(row: Row, resolver: Resolver): List<DatePattern> = listOf(this)

    override fun newBasedOn(resolver: Resolver): List<DatePattern> = listOf(this)
    override fun negativeBasedOn(row: Row, resolver: Resolver): List<Pattern> {
        return listOf(NullPattern)
    }

    override fun parse(value: String, resolver: Resolver): StringValue =
        attempt {
            DateTimeFormatter.ISO_DATE.parse(value)
            StringValue(value)
        }

    override fun encompasses(otherPattern: Pattern, thisResolver: Resolver, otherResolver: Resolver, typeStack: TypeStack): Result {
        return encompasses(this, otherPattern, thisResolver, otherResolver, typeStack)
    }

    override fun listOf(valueList: List<Value>, resolver: Resolver): Value {
        return JSONArrayValue(valueList)
    }

    override val typeAlias: String?
        get() = null

    override val typeName: String = "date"

    override val pattern = "(date)"

    override fun toString() = pattern
}

private fun currentDateInRFC3339Format() = StringValue(
    LocalDateTime.now().format(
        DateTimeFormatter.ofPattern(
            RFC3339_DATETIME_FORMAT
        )
    )
)
