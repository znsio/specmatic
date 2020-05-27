package run.qontract.core

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import run.qontract.core.pattern.*
import run.qontract.core.value.JSONObjectValue
import run.qontract.core.value.TypeDeclaration

class ConvertJSONToTabular {
    @Test
    fun `json object with no nesting`() {
        val typeDeclaration = tabularType("""{"name": "John Doe"}""")
        val requestBody = typeDeclaration.types.getValue("(RequestBody)") as TabularPattern
        assertThat(requestBody.pattern.getValue("name")).isEqualTo(DeferredPattern("(string)"))
    }

    @Test
    fun `when one of the json object values is another json object`() {
        val typeDeclaration = tabularType("""{"name": "John Doe", "address": {"street": "High Street"}}""")
        val requestBody = typeDeclaration.types.getValue("(RequestBody)") as TabularPattern
        assertThat(requestBody.pattern.getValue("name")).isEqualTo(DeferredPattern("(string)"))
        assertThat(requestBody.pattern.getValue("address")).isEqualTo(DeferredPattern("(Address)"))

        val addressType = typeDeclaration.types.getValue("(Address)") as TabularPattern
        assertThat(addressType.pattern.getValue("street")).isEqualTo(DeferredPattern("(string)"))
    }

    @Test
    fun `when one of the json object values is a json array`() {
        val typeDeclaration = tabularType("""{"name": "John Doe", "phone_numbers": [12345, 54321]}""")
        val requestBody = typeDeclaration.types.getValue("(RequestBody)") as TabularPattern
        assertThat(requestBody.pattern.getValue("name")).isEqualTo(DeferredPattern("(string)"))
        assertThat(requestBody.pattern.getValue("phone_numbers")).isEqualTo(DeferredPattern("(number*)"))
    }

    @Test
    fun `prints the available types in contract form`() {
        val patternFromValue = tabularType("RequestBody","""{"name": "John Doe", "phone_numbers": [12345, 54321]}""")
        assertThat(patternFromValue.typeValue).isEqualTo("(RequestBody)")

        val stringValue = patternFromValueToString(patternFromValue.types).trim()
        assertThat(stringValue).isEqualTo("""Given type RequestBody
| name | (string) |
| phone_numbers | (number*) |""")
    }
}

fun patternFromValueToString(patterns: Map<String, Pattern>): String {
    val blankLine = "\n\n"
    val gherkinPrefixes = listOf("Given").plus(if(patterns.size > 1) 1.until(patterns.size).map { "And" } else emptyList())
    return patterns.entries.zip(gherkinPrefixes).map { (entry, prefix) ->
        toQontractString(prefix, entry.key, entry.value)
    }.filter { it.isNotEmpty() }.joinToString(blankLine)
}

fun toQontractString(gherkinPrefix: String, key: String, type: Pattern): String {
    val title = "$gherkinPrefix type ${withoutPatternDelimiters(key)}"

    val table = when(type) {
        is DeferredPattern -> "| $key | (${type.pattern}*) |"
        is JSONObjectPattern -> patternMapToString(type.pattern)
        is TabularPattern -> patternMapToString(type.pattern)
        else -> "| $key | ${type.pattern} |"
    }

    return "$title\n$table"
}

//======================

private fun patternMapToString(json: Map<String, Pattern>): String {
    return json.entries.joinToString("\n") {
        "| ${it.key} | ${it.value} |"
    }
}

private fun tabularType(text: String): TypeDeclaration {
    val value = parsedValue(text)

    if(value !is JSONObjectValue)
        throw ContractException("Only a json value can be converted into tabular syntax.")

    return value.typeDeclaration("RequestBody")
}

private fun tabularType(name: String, text: String): TypeDeclaration {
    val value = parsedValue(text)

    if(value !is JSONObjectValue)
        throw ContractException("Only a json value can be converted into tabular syntax.")

    return value.typeDeclaration(name)
}
