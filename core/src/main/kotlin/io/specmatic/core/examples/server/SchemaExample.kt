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

        const val SCHEMA_BASED = "SCHEMA_BASED"
        const val NOT_SCHEMA_BASED = "NOT_SCHEMA_BASED"

        fun toSchemaExample(patternName: String, value: Value): JSONObjectValue {
            return JSONObjectValue(
                mapOf(
                    SCHEMA_IDENTIFIER to StringValue(patternName),
                    VALUE_IDENTIFIER to value
                )
            )
        }
    }

    constructor(file: File) : this(json = attempt("Error reading example file ${file.canonicalPath}") { parsedJSONObject(file.readText()) }, file = file)

    init {
        if (json.findFirstChildByPath(VALUE_IDENTIFIER) == null) {
            throw ContractException(breadCrumb = NOT_SCHEMA_BASED, errorMessage = "Skipping file ${file.canonicalPath}, because it contains non schema-based example")
        }
    }

    val getSchemaBasedOn = attempt(breadCrumb = "Error reading schema in example ${file.canonicalPath}") {
        json.findFirstChildByPath(SCHEMA_IDENTIFIER)?.toStringLiteral()
            ?:throw ContractException("Schema Identifier key '${SCHEMA_IDENTIFIER}' not found")
    }

    val value = attempt(breadCrumb = "Error reading value in example ${file.canonicalPath}") {
        json.findFirstChildByPath(VALUE_IDENTIFIER) ?: throw ContractException("Example value identifier key '${VALUE_IDENTIFIER}' not found")
    }
}