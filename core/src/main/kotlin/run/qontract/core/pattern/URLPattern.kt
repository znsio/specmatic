package run.qontract.core.pattern

import run.qontract.core.Resolver
import run.qontract.core.Result
import run.qontract.core.value.JSONArrayValue
import run.qontract.core.value.StringValue
import run.qontract.core.value.Value
import java.net.URI

data class URLPattern(val scheme: URLScheme = URLScheme.HTTPS): Pattern {
    override val pattern: String = "(url)"

    override fun matches(sampleData: Value?, resolver: Resolver): Result {
        return when (sampleData) {
            is StringValue -> {
                if(scheme.matches(parse(sampleData.string, resolver))) {
                    Result.Success()
                } else Result.Failure("Expected ${sampleData.string} to be ${scheme.type}")
            }
            else -> Result.Failure("URLs can only be held in strings.")
        }
    }

    override fun generate(resolver: Resolver): StringValue =
            StringValue("${scheme.prefix}${randomString().toLowerCase()}.com/${randomString().toLowerCase()}")

    override fun newBasedOn(row: Row, resolver: Resolver): List<Pattern> = listOf(this)

    override fun parse(value: String, resolver: Resolver): StringValue = StringValue(URI.create(value).toString())

    override fun encompasses(otherPattern: Pattern, thisResolver: Resolver, otherResolver: Resolver): Result {
        return when(otherPattern) {
            this -> Result.Success()
            is URLPattern -> Result.Failure("Expected ${scheme.type}, got ${otherPattern.scheme.type}")
            else -> Result.Failure("Expected $typeName, got ${otherPattern.typeName}")
        }
    }

    override fun listOf(valueList: List<Value>, resolver: Resolver): Value {
        return JSONArrayValue(valueList)
    }

    override val typeName: String = "url"
}
