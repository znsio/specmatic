package io.specmatic.core

import com.networknt.schema.JsonSchema
import com.networknt.schema.JsonSchemaFactory
import com.networknt.schema.ValidationMessage
import com.fasterxml.jackson.databind.JsonNode
import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.dataformat.yaml.YAMLFactory
import com.networknt.schema.SpecVersion
import java.io.File
import java.io.InputStreamReader
import java.net.URL
import java.nio.file.Files

class SchemaValidator {

    private val objectMapper = ObjectMapper(YAMLFactory())

    fun loadSchemaFromUrl(schemaUrl: String): JsonSchema {
        val url = URL(schemaUrl)
        val schemaNode: JsonNode = objectMapper.readTree(InputStreamReader(url.openStream()))
        val factory = JsonSchemaFactory.getInstance(SpecVersion.VersionFlag.V7)
        return factory.getSchema(schemaNode)
    }

    fun validateYamlAgainstSchema(file: File, schemaUrl: String) {
        val text = String(Files.readAllBytes(file.toPath()))
        val schema = loadSchemaFromUrl(schemaUrl)
        val jsonNode: JsonNode = objectMapper.readTree(text)
        val validationMessages: Set<ValidationMessage> = schema.validate(jsonNode)
        if (validationMessages.isEmpty()) {
            println("Yaml is valid according to the schema.")
        } else {
            println("Yaml is invalid. Validation errors:")
            validationMessages.forEach { message ->
                println(" - ${message.message}")
            }
        }
    }
}
