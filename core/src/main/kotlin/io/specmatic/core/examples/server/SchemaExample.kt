package io.specmatic.core.examples.server

import io.specmatic.core.pattern.*
import io.specmatic.core.value.NullValue
import io.specmatic.core.value.StringValue
import io.specmatic.core.value.Value
import java.io.File

data class SchemaExample(val json: Value, val file: File) {
    companion object {
        val SCHEMA_IDENTIFIER_REGEX = Regex("^resource\\.(.*)\\.example\\.json$")

        fun toSchemaExampleFileName(patternName: String): String {
            return SCHEMA_IDENTIFIER_REGEX.replace(patternName, "resource.$1.example.json")
        }

        fun matchesFilePattern(file: File): Boolean {
            return SCHEMA_IDENTIFIER_REGEX.matches(file.name)
        }

        fun fromFile(file: File): ReturnValue<SchemaExample> {
            if (!matchesFilePattern(file)) {
                return HasFailure("Skipping file ${file.canonicalPath}, because didn't match pattern ${SCHEMA_IDENTIFIER_REGEX.pattern}")
            }

            return HasValue(SchemaExample(file))
        }
    }

    constructor(file: File) : this(json = attempt("Error reading example file ${file.canonicalPath}") { parsedValue(file.readText()) }, file = file)

    val getSchemaBasedOn = attempt(breadCrumb = "Error parsing schema from example name ${file.name}") {
        SCHEMA_IDENTIFIER_REGEX.find(file.name)?.groupValues?.get(1)
            ?: throw ContractException("File name didn't match pattern ${SCHEMA_IDENTIFIER_REGEX.pattern}")
    }

    val value = when(json) {
        is StringValue -> json.takeUnless { it.string == "null" || it.string.isEmpty() } ?: NullValue
        else -> json
    }
}