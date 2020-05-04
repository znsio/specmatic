package run.qontract.core.pattern

import run.qontract.core.Resolver
import run.qontract.core.Result
import run.qontract.core.value.StringValue
import run.qontract.core.value.Value
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter

object DateTimePattern : Pattern {
    override fun matches(sampleData: Value?, resolver: Resolver): Result = when (sampleData) {
        is StringValue -> resultOf {
            parse(sampleData.string, resolver)
            Result.Success()
        }
        else -> Result.Failure("DateTime types can only be represented using strings")
    }

    override fun generate(resolver: Resolver): StringValue =
            StringValue(LocalDateTime.now().format(DateTimeFormatter.ISO_DATE_TIME))

    override fun newBasedOn(row: Row, resolver: Resolver): List<DateTimePattern> = listOf(this)

    override fun parse(value: String, resolver: Resolver): StringValue =
            attempt {
                DateTimeFormatter.ISO_DATE_TIME.parse(value)
                StringValue(value)
            }

    override fun encompasses(otherPattern: Pattern, resolver: Resolver): Boolean = otherPattern is DateTimePattern
    override val description: String = "datetime"

    override val pattern = "(datetime)"
}