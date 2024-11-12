package io.specmatic.core.examples.server

import io.specmatic.core.pattern.ContractException
import io.specmatic.core.pattern.attempt
import io.specmatic.core.pattern.parsedJSONObject
import io.specmatic.core.value.JSONObjectValue
import io.specmatic.core.value.StringValue
import io.specmatic.core.value.Value
import java.io.File

data class SchemaExample(val json: JSONObjectValue, val file: File) {
    companion object {
        const val SCHEMA_IDENTIFIER = "schema"
        const val VALUE_IDENTIFIER = "value"

        fun toSchemaExample(patternName: String, value: Value): JSONObjectValue {
            return JSONObjectValue(
                mapOf(
                    SCHEMA_IDENTIFIER to StringValue(patternName),
                    VALUE_IDENTIFIER to value
                )
            )
        }
    }
    constructor(file: File) : this(parsedJSONObject(file.readText()), file)

    val getSchemaBasedOn = attempt("Error reading schema in example") {
        json.findFirstChildByPath(SCHEMA_IDENTIFIER)?.toStringLiteral()
            ?: throw ContractException("Schema Identifier '${SCHEMA_IDENTIFIER}' not found")
    }

    val value = attempt("Error reading value in example") {
        json.findFirstChildByPath(VALUE_IDENTIFIER) ?: throw ContractException("Example value identifier '${VALUE_IDENTIFIER}' not found")
    }
}