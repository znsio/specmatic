package io.specmatic.core.examples.server

import CustomJsonNodeFactory
import CustomParserFactory
import com.fasterxml.jackson.core.JsonLocation
import com.fasterxml.jackson.databind.JsonNode
import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.databind.node.ArrayNode
import com.fasterxml.jackson.databind.node.IntNode
import com.jayway.jsonpath.Configuration
import com.jayway.jsonpath.DocumentContext
import com.jayway.jsonpath.JsonPath
import com.jayway.jsonpath.Option
import com.jayway.jsonpath.spi.json.JacksonJsonNodeJsonProvider
import com.jayway.jsonpath.spi.mapper.JacksonMappingProvider
import java.io.File

fun findLineNumber(filePath: File, jsonPath: String): Int? {
    val customParserFactory: CustomParserFactory = CustomParserFactory()
    val om: ObjectMapper = ObjectMapper(customParserFactory)
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
    val objectMapper = ObjectMapper()
    val rootNode: JsonNode = objectMapper.readTree(filePath)
    val pathParts = jsonPath.split(".").dropLast(1);
    var textArrayNode: JsonNode = rootNode;
    for (part in pathParts) {
        textArrayNode = textArrayNode.findPath(part)
    }

    val location: JsonLocation = factory.getLocationForNode(textArrayNode)?: return null;
    return location.lineNr;

}
