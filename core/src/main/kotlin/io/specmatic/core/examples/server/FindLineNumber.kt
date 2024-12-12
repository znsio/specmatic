package io.specmatic.core.examples.server

import CustomJsonNodeFactory
import CustomParserFactory
import com.fasterxml.jackson.core.JsonLocation
import com.fasterxml.jackson.core.JsonParser
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

    if (!hasArray(jsonPath)) {
        while (token != null) {
            if (token == JsonToken.FIELD_NAME && parser.currentName() == jsonPath.split(".").last()) {
                return parser.currentTokenLocation().lineNr
            } else if (token == JsonToken.START_ARRAY) {
                parser.skipChildren()
            }
            token = parser.nextToken()
        }
    } else {
        return findInArray(filePath, jsonPath, parser)
    }

    return getLineNumberOfParentNode(filePath, jsonPath).filterNotNull().firstOrNull()
}

private fun hasArray(jsonPath: String): Boolean {
    return jsonPath.contains("[") && jsonPath.contains("]")
}


fun findInArray(filePath: File, jsonPath: String, parser: JsonParser): Int? {
    val pathSegments = jsonPath.split(".")
    val edgeNode = pathSegments.last()
    val nonEdgeNode = pathSegments.dropLast(1).lastOrNull() ?: return null

    val arrayIndex = extractArrayIndex(nonEdgeNode)
    val edgeNodeWithoutIndex = nonEdgeNode.substringBefore("[")

    var insideTargetArray = false
    var currentIndex = 0
    val contextStack = mutableListOf<String>()
    var token: JsonToken? = parser.nextToken()
    var resultLine: Int? = null

    while (token != null) {
        when (token) {
            JsonToken.START_ARRAY -> {
                if (contextStack.size > 1 && contextStack.last() == edgeNodeWithoutIndex) {
                    insideTargetArray = true
                }
                contextStack.add("ARRAY")
            }
            JsonToken.START_OBJECT -> {
                if(contextStack.lastOrNull() == "ARRAY") {
                    contextStack.add("ARRAY-OBJECT")
                }
                else
                {
                    contextStack.add("OBJECT")
                }
            }
            JsonToken.FIELD_NAME -> {
                if (parser.currentName() == edgeNode && insideTargetArray && currentIndex.toString() == arrayIndex) {
                    resultLine = parser.currentTokenLocation().lineNr
                }

                if (parser.currentName() == edgeNodeWithoutIndex && !insideTargetArray) {
                    contextStack.add(edgeNodeWithoutIndex)
                    insideTargetArray = true
                }
            }

            JsonToken.END_OBJECT -> {
                if (contextStack.lastOrNull() == "OBJECT") {
                    contextStack.removeAt(contextStack.size - 1)
                }
                else if(contextStack.lastOrNull() == "ARRAY-OBJECT")
                {
                    currentIndex++
                    contextStack.removeAt(contextStack.size - 1)
                }
            }
            JsonToken.END_ARRAY -> {
                // Increment the index when exiting an array
                if (insideTargetArray && contextStack.lastOrNull() == "ARRAY") {
                    contextStack.removeAt(contextStack.size - 1)
                    insideTargetArray = false
                }
            }
            else -> {}
        }
        if (resultLine == null) {
            token = parser.nextToken()
        } else {
            break
        }
    }

    return resultLine?: getLineNumberOfParentNode(filePath, jsonPath).firstOrNull()
}

private fun extractArrayIndex(edgeNode: String): String {
    return edgeNode.substringAfter("[").substringBefore("]")  // Extract index part of the node like "0"
}


private fun getLineNumberOfParentNode(filePath: File, jsonPath: String): List<Int?> {
    val customParserFactory = CustomParserFactory()
    val objectMapper = ObjectMapper(customParserFactory)
    val factory = CustomJsonNodeFactory(
        objectMapper.getDeserializationConfig().getNodeFactory(),
        customParserFactory
    )
    objectMapper.setConfig(objectMapper.getDeserializationConfig().with(factory))
    val config = Configuration.builder()
        .mappingProvider(JacksonMappingProvider(objectMapper))
        .jsonProvider(JacksonJsonNodeJsonProvider(objectMapper))
        .options(Option.ALWAYS_RETURN_LIST)
        .build()


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
    return location.lineNr;
}