package run.qontract.core.utilities

import kotlinx.serialization.ImplicitReflectionSerializer
import kotlinx.serialization.json.*
import kotlinx.serialization.stringify
import run.qontract.core.ContractParseException
import run.qontract.core.value.*

@OptIn(ImplicitReflectionSerializer::class)
fun arrayToJsonString(value: List<Any?>): String {
    val data = listToElements(value)
    return Json.nonstrict.stringify(data)
}
fun toMap(bytes: ByteArray) = jsonStringToMap(String(bytes))
fun toMap(value: Value?) = jsonStringToMap(value.toString())

@OptIn(ImplicitReflectionSerializer::class)
fun jsonStringToMap(stringContent: String): MutableMap<String, Any?>  {
    val data = Json.nonstrict.parseJson(stringContent).jsonObject.toMap()
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

private fun toValue(jsonElement: JsonElement): Value =
    when (jsonElement) {
        is JsonNull -> NullValue()
        is JsonObject -> JSONObjectValue2(jsonElement.toMap().mapValues { toValue(it.value) })
        is JsonArray -> JSONArrayValue2(jsonElement.toList().map { toValue(it) })
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

fun convertToArrayAny(data: List<JsonElement>): List<Any> {
    return data.map { toValue(it) }
}

@OptIn(ImplicitReflectionSerializer::class)
fun mapToJsonString(value: Map<String, Any?>): String {
    val data = mapToStringElement(value)
    return Json.nonstrict.stringify(data)
}

fun mapToStringElement(data: Map<String, Any?>): Map<String, JsonElement> {
    return data.mapValues { toJsonElement(it.value) }
}

private fun toJsonElement(value: Any?): JsonElement {
    return when (value) {
        is List<*> -> listToElements(value as List<Any?>)
        is Map<*, *> -> JsonObject(mapToStringElement(value as Map<String, Any?>))
        is Number -> JsonLiteral(value)
        is Boolean -> JsonLiteral(value)
        is String -> JsonLiteral(value)
        else -> JsonNull
    }
}

fun listToElements(values: List<Any?>): JsonArray {
    return JsonArray(values.map { toJsonElement(it) })
}

fun jsonStringToArray(value: String): MutableList<Any?> {
    val data = Json.nonstrict.parseJson(value).jsonArray.toList()
    return convertToArrayAny(data).toMutableList()
}
