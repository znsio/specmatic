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
        }
        token = parser.nextToken()
    }
    val lineNumbers = getLineNumberOfParentNode(filePath, jsonPath)
    return lineNumbers.filterNotNull().firstOrNull()
}

private fun getLineNumberOfParentNode(filePath: File, jsonPath: String): List<Int?> {
    val customParserFactory = CustomParserFactory()
    val om = ObjectMapper(customParserFactory)
    val factory = CustomJsonNodeFactory(
        om.getDeserializationConfig().getNodeFactory(),
        customParserFactory
    )
    om.setConfig(om.getDeserializationConfig().with(factory))
    val config = Configuration.builder()
        .mappingProvider(JacksonMappingProvider(om))
        .jsonProvider(JacksonJsonNodeJsonProvider(om))
        .options(Option.ALWAYS_RETURN_LIST)
        .build()

    val parsedDocument: DocumentContext = JsonPath.parse(filePath, config)
    val dollarJsonPath = "$.${jsonPath}";
    val pathParts = dollarJsonPath.split(".")
    val requiredPath = JsonPath.compile(pathParts.dropLast(1).joinToString("."))
    val findings: JsonNode = parsedDocument.read(requiredPath)
    val lineNumbers = findings.map { finding ->
        val location: JsonLocation = factory.getLocationForNode(finding) ?: return@map null
        location.getLineNr()
    }
    return lineNumbers
}

