package run.qontract.core.utilities

import kotlinx.serialization.ImplicitReflectionSerializer
import kotlinx.serialization.json.*
import kotlinx.serialization.stringify
import run.qontract.core.ContractParseException
import run.qontract.core.pattern.*
import run.qontract.core.value.*

@OptIn(ImplicitReflectionSerializer::class)
fun valueArrayToJsonString(value: List<Value>): String {
    val data = valueListToElements(value)
    return Json.indented.stringify(data)
}

fun toMap(value: Value?) = jsonStringToMap(value.toString())

fun jsonStringToMap(stringContent: String): MutableMap<String, Any?>  {
    val data = Json.plain.parseJson(stringContent).jsonObject.toMap()
    return convertToMapAny(data)
}

fun convertToMapAny(data: Map<String, JsonElement>): MutableMap<String, Any?> {
    return data.mapValues { toAnyValue(it.value) }.toMutableMap()
}

private fun toAnyValue(value: JsonElement): Any? =
        when (value) {
            is JsonNull -> null
            is JsonLiteral -> if(value.isString) value.content else value.booleanOrNull ?: value.intOrNull ?: value.longOrNull ?: value.floatOrNull ?: value.doubleOrNull
            is JsonObject -> convertToMapAny(value.toMap()).toMutableMap()
            is JsonArray -> convertToArrayAny(value.toList()).toMutableList()
        }

fun stringToPatternMap(stringContent: String): Map<String, Pattern>  {
    val data = Json.nonstrict.parseJson(stringContent).jsonObject.toMap()
    return convertToMapPattern(data)
}

fun jsonStringToValueMap(stringContent: String): Map<String, Value>  {
    val data = Json.nonstrict.parseJson(stringContent).jsonObject.toMap()
    return convertToMapValue(data)
}

fun convertToMapValue(data: Map<String, JsonElement>): Map<String, Value> {
    return data.mapValues { toValue(it.value) }
}

fun convertToMapPattern(data: Map<String, JsonElement>): Map<String, Pattern> =
    data.mapValues { toPattern(it.value) }

fun toPattern(jsonElement: JsonElement): Pattern {
    return when (jsonElement) {
        is JsonNull -> NullPattern
        is JsonObject -> JSONObjectPattern(jsonElement.toMap().mapValues { toPattern(it.value) })
        is JsonArray -> JSONArrayPattern(jsonElement.toList().map { toPattern(it) })
        is JsonLiteral -> toLiteralPattern(jsonElement)
        else -> throw ContractParseException("Unknown value type: ${jsonElement.javaClass.name}")
    }
}

fun toLiteralPattern(jsonElement: JsonLiteral): Pattern =
    when {
        jsonElement.isString -> parsedPattern(jsonElement.content)
        jsonElement.booleanOrNull != null -> ExactMatchPattern(BooleanValue(jsonElement.boolean))
        jsonElement.intOrNull != null -> ExactMatchPattern(NumberValue(jsonElement.int))
        jsonElement.longOrNull != null -> ExactMatchPattern(NumberValue(jsonElement.long))
        jsonElement.floatOrNull != null -> ExactMatchPattern(NumberValue(jsonElement.float))
        else -> throw ContractParseException("Can't recognise the type of $jsonElement")
    }

private fun toValue(jsonElement: JsonElement): Value =
    when (jsonElement) {
        is JsonNull -> NullValue
        is JsonObject -> JSONObjectValue(jsonElement.toMap().mapValues { toValue(it.value) })
        is JsonArray -> JSONArrayValue(jsonElement.toList().map { toValue(it) })
        is JsonLiteral -> toLiteralValue(jsonElement)
        else -> throw ContractParseException("Unknown value type: ${jsonElement.javaClass.name}")
    }

fun toLiteralValue(jsonElement: JsonLiteral): Value =
    when {
        jsonElement.isString -> StringValue(jsonElement.content)
        jsonElement.booleanOrNull != null -> BooleanValue(jsonElement.boolean)
        jsonElement.intOrNull != null -> NumberValue(jsonElement.int)
        jsonElement.longOrNull != null -> NumberValue(jsonElement.long)
        jsonElement.floatOrNull != null -> NumberValue(jsonElement.float)
        else -> NumberValue(jsonElement.double)
    }

fun convertToArrayAny(data: List<JsonElement>): List<Any?> =
    data.map { toAnyValue(it) }

fun convertToArrayValue(data: List<JsonElement>): List<Value> =
    data.map { toValue(it) }

fun convertToArrayPattern(data: List<JsonElement>): List<Pattern> =
    data.map { toPattern(it) }

@OptIn(ImplicitReflectionSerializer::class)
fun nativeMapToJsonString(value: Map<String, Any?>): String {
    val data = nativeMapToStringElement(value)
    return Json.indented.stringify(data)
}

@OptIn(ImplicitReflectionSerializer::class)
fun valueMapToPrettyJsonString(value: Map<String, Value>): String {
    val data = mapToStringElement(value)
    return Json.indented.stringify(data)
}

fun nativeMapToStringElement(data: Map<String, Any?>): Map<String, JsonElement> {
    return data.mapValues { anyToJsonElement(it.value) }
}

fun mapToStringElement(data: Map<String, Value>): Map<String, JsonElement> {
    return data.mapValues { valueToJsonElement(it.value) }
}

private fun anyToJsonElement(value: Any?): JsonElement {
    return when (value) {
        is List<*> -> listAnyToElements(value as List<Any?>)
        is Map<*, *> -> JsonObject(nativeMapToStringElement(value as Map<String, Any?>))
        is Number -> JsonLiteral(value)
        is Boolean -> JsonLiteral(value)
        is String -> JsonLiteral(value)
        else -> JsonNull
    }
}

private fun valueToJsonElement(value: Value): JsonElement {
    return when (value) {
        is JSONArrayValue -> valueListToElements(value.list)
        is JSONObjectValue -> JsonObject(mapToStringElement(value.jsonObject))
        is NumberValue -> JsonLiteral(value.number)
        is BooleanValue -> JsonLiteral(value.booleanValue)
        is StringValue -> JsonLiteral(value.string)
        else -> JsonNull
    }
}

fun listAnyToElements(values: List<Any?>): JsonArray {
    return JsonArray(values.map { anyToJsonElement(it) })
}

fun valueListToElements(values: List<Value>): JsonArray {
    return JsonArray(values.map { valueToJsonElement(it) })
}

fun jsonStringToValueArray(value: String): List<Value> {
    val data = Json.nonstrict.parseJson(value).jsonArray.toList()
    return convertToArrayValue(data)
}

fun stringTooPatternArray(value: String): List<Pattern> {
    val data = Json.nonstrict.parseJson(value).jsonArray.toList()
    return convertToArrayPattern(data)
}
