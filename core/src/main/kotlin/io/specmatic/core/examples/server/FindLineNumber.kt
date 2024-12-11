package io.specmatic.core.examples.server

import CustomJsonNodeFactory
import CustomParserFactory
import com.fasterxml.jackson.core.JsonLocation
import com.fasterxml.jackson.core.JsonToken
import com.fasterxml.jackson.databind.JsonNode
import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.databind.node.ArrayNode
import com.jayway.jsonpath.Configuration
import com.jayway.jsonpath.DocumentContext
import com.jayway.jsonpath.JsonPath
import com.jayway.jsonpath.Option
import com.jayway.jsonpath.spi.json.JacksonJsonNodeJsonProvider
import com.jayway.jsonpath.spi.mapper.JacksonMappingProvider
import java.io.File

fun findLineNumber(filePath: File, jsonPath: String): Int? {
    val objectMapper = ObjectMapper()
    val parser = objectMapper.createParser(filePath)
    var token: JsonToken? = parser.nextToken()

    while (token != null) {
        if (token == JsonToken.FIELD_NAME && parser.currentName() == jsonPath.split(".").last()) {
            return parser.currentTokenLocation().lineNr
        } else if (token == JsonToken.START_ARRAY) {
            parser.skipChildren()
        }
        token = parser.nextToken()
    }

    return getLineNumberOfParentNode(filePath, jsonPath).filterNotNull().firstOrNull()
}

private fun getLineNumberOfParentNode(filePath: File, jsonPath: String): List<Int?> {
    val customParserFactory = CustomParserFactory()
    val objectMapper = ObjectMapper(customParserFactory)
    val config = Configuration.builder()
        .mappingProvider(JacksonMappingProvider(objectMapper))
        .jsonProvider(JacksonJsonNodeJsonProvider(objectMapper))
        .options(Option.ALWAYS_RETURN_LIST)
        .build()

    val factory = CustomJsonNodeFactory(
        objectMapper.getDeserializationConfig().getNodeFactory(),
        customParserFactory
    )
    val parsedDocument: DocumentContext = JsonPath.parse(filePath, config)
    val dollarJsonPath = "$.${jsonPath}"
    val pathParts = dollarJsonPath.split(".")
    val requiredPath = JsonPath.compile(pathParts.dropLast(1).joinToString("."))

    val findings: Any? =
        parsedDocument.read(requiredPath)


    return when (findings) {
        is ArrayNode -> findings.map { node ->
            getLineNumberFromNode(node, objectMapper,factory)
        }
        is JsonNode -> getLineNumbersFromJsonNode(findings, objectMapper,factory)

        else -> emptyList()
    }
}

private fun getLineNumbersFromJsonNode(node: JsonNode, objectMapper: ObjectMapper,factory: CustomJsonNodeFactory): List<Int?> {
    val lineNumbers = mutableListOf<Int?>()
    node.fields().forEach { (_, value) ->
        lineNumbers.add(getLineNumberFromNode(value, objectMapper,factory))
    }
    return lineNumbers
}

private fun getLineNumberFromNode(node: JsonNode, objectMapper: ObjectMapper,factory: CustomJsonNodeFactory): Int? {
    val location: JsonLocation = factory.getLocationForNode(node)?: return null;
    return 0;
}

