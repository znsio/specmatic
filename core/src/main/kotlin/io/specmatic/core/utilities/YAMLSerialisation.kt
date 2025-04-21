package io.specmatic.core.utilities

import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.databind.SerializationFeature
import com.fasterxml.jackson.dataformat.yaml.YAMLFactory
import com.fasterxml.jackson.dataformat.yaml.YAMLGenerator
import io.specmatic.core.pattern.*
import io.specmatic.core.value.*

private val yamlFactory = YAMLFactory().disable(YAMLGenerator.Feature.WRITE_DOC_START_MARKER).enable(YAMLGenerator.Feature.MINIMIZE_QUOTES)

val indentedYamlMapper: ObjectMapper = ObjectMapper(yamlFactory).apply {
    enable(SerializationFeature.INDENT_OUTPUT)
    findAndRegisterModules()
}

val unformattedAndQuotedYamlMapper = ObjectMapper(YAMLFactory().disable(YAMLGenerator.Feature.WRITE_DOC_START_MARKER)).apply {
    disable(SerializationFeature.INDENT_OUTPUT)
    findAndRegisterModules()
}

val yamlMapper: ObjectMapper = ObjectMapper(yamlFactory).apply {
    findAndRegisterModules()
}

fun yamlStringToValue(stringContent: String): Value {
    val cleaned = stringContent.cleanBOM()
    val raw: Any = yamlMapper.readValue(cleaned, Any::class.java)
    return toValue(raw)
}

fun yamlStringToPattern(stringContent: String): Pattern {
    val cleaned = stringContent.cleanBOM()
    val raw: Any = yamlMapper.readValue(cleaned, Any::class.java)
    return toPattern(raw)
}

fun valueToYamlString(value: Value, mapper: ObjectMapper = indentedYamlMapper): String {
    return mapper.writeValueAsString(valueToRaw(value))
}

private fun String.cleanBOM(): String = removePrefix(UTF_BYTE_ORDER_MARK)

private fun <T> convertToMap(data: Map<*, *>, using: (Any?) -> T): Map<String, T> {
    return data.entries.associate { (k, v) -> k.toString() to using(v) }
}

private fun toValue(any: Any?): Value {
    return when (any) {
        null -> NullValue
        is Map<*, *> -> convertToMap(any, ::toValue).let(::JSONObjectValue)
        is List<*> -> any.map(::toValue).let(::JSONArrayValue)
        is String -> StringValue(any)
        is Boolean -> BooleanValue(any)
        is Number -> NumberValue(any)
        else -> throw ContractException("Unknown value type: ${any::class.simpleName}")
    }
}

private fun toPattern(any: Any?): Pattern {
    return when (any) {
        null -> NullPattern
        is Map<*, *> -> convertToMap(any, ::toPattern).let(::JSONObjectPattern)
        is List<*> -> any.map(::toPattern).let(::JSONArrayPattern)
        is String -> parsedPattern(any)
        is Boolean -> BooleanValue(any).let(::ExactValuePattern)
        is Number -> NumberValue(any).let(::ExactValuePattern)
        else -> throw ContractException("Unknown pattern type: ${any::class.simpleName}")
    }
}

private fun valueToRaw(value: Value): Any? {
    return when (value) {
        is JSONObjectValue -> value.jsonObject.mapValues { valueToRaw(it.value) }
        is JSONArrayValue -> value.list.map(::valueToRaw)
        is StringValue -> value.string
        is BooleanValue -> value.booleanValue
        is NumberValue -> value.number
        else -> null
    }
}
