package run.qontract.core.utilities

import kotlinx.serialization.*
import kotlinx.serialization.json.*

import run.qontract.core.pattern.*
import run.qontract.core.value.*

val indentedJson = Json {
    prettyPrint = true
}

val lenientJson = Json {
    isLenient = true
}

fun valueArrayToJsonString(value: List<Value>): String {
    val data = valueListToElements(value)

    return indentedJson.encodeToString(data)
}

fun toMap(value: Value?) = jsonStringToValueMap(value.toString())

fun stringToPatternMap(stringContent: String): Map<String, Pattern>  {
    val data = lenientJson.parseToJsonElement(stringContent).jsonObject.toMap()
    try {
        return convertToMapPattern(data)
    }
    catch (e: Throwable) {
        println(e.message)
        println(e.toString())
        throw e
    }
}

fun jsonStringToValueMap(stringContent: String): Map<String, Value>  {
    val data = lenientJson.parseToJsonElement(stringContent).jsonObject.toMap()
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
        is JsonObject -> toJSONObjectPattern(jsonElement.toMap().mapValues { toPattern(it.value) })
        is JsonArray -> JSONArrayPattern(jsonElement.toList().map { toPattern(it) })
        is JsonPrimitive -> toLiteralPattern(jsonElement)
        else -> throw ContractException("Unknown value type: ${jsonElement.javaClass.name}")
    }
}

fun toLiteralPattern(jsonElement: JsonPrimitive): Pattern =
    when {
        jsonElement.isString -> parsedPattern(jsonElement.content)
        jsonElement.booleanOrNull != null -> ExactValuePattern(BooleanValue(jsonElement.boolean))
        jsonElement.intOrNull != null -> ExactValuePattern(NumberValue(jsonElement.int))
        jsonElement.longOrNull != null -> ExactValuePattern(NumberValue(jsonElement.long))
        jsonElement.floatOrNull != null -> ExactValuePattern(NumberValue(jsonElement.float))
        else -> throw ContractException("Can't recognise the type of $jsonElement")
    }

private fun toValue(jsonElement: JsonElement): Value =
    when (jsonElement) {
        is JsonNull -> NullValue
        is JsonObject -> JSONObjectValue(jsonElement.toMap().mapValues { toValue(it.value) })
        is JsonArray -> JSONArrayValue(jsonElement.toList().map { toValue(it) })
        is JsonPrimitive -> toLiteralValue(jsonElement)
        else -> throw ContractException("Unknown value type: ${jsonElement.javaClass.name}")
    }

fun toLiteralValue(jsonElement: JsonPrimitive): Value =
    when {
        jsonElement.isString -> StringValue(jsonElement.content)
        jsonElement.booleanOrNull != null -> BooleanValue(jsonElement.boolean)
        jsonElement.intOrNull != null -> NumberValue(jsonElement.int)
        jsonElement.longOrNull != null -> NumberValue(jsonElement.long)
        jsonElement.floatOrNull != null -> NumberValue(jsonElement.float)
        else -> NumberValue(jsonElement.double)
    }

fun convertToArrayValue(data: List<JsonElement>): List<Value> =
    data.map { toValue(it) }

fun convertToArrayPattern(data: List<JsonElement>): List<Pattern> =
    data.map { toPattern(it) }

fun valueMapToPrettyJsonString(value: Map<String, Value>): String {
    val data = mapToStringElement(value)
    return indentedJson.encodeToString(data)
}

fun valueMapToPlainJsonString(value: Map<String, Value>): String {
    val data = mapToStringElement(value)
    return lenientJson.encodeToString(data)
}

fun mapToStringElement(data: Map<String, Value>): Map<String, JsonElement> {
    return data.mapValues { valueToJsonElement(it.value) }
}

private fun valueToJsonElement(value: Value): JsonElement {
    return when (value) {
        is JSONArrayValue -> valueListToElements(value.list)
        is JSONObjectValue -> JsonObject(mapToStringElement(value.jsonObject))
        is NumberValue -> JsonPrimitive(value.number)
        is BooleanValue -> JsonPrimitive(value.booleanValue)
        is StringValue -> JsonPrimitive(value.string)
        else -> JsonNull
    }
}

fun valueListToElements(values: List<Value>): JsonArray {
    return JsonArray(values.map { valueToJsonElement(it) })
}

fun jsonStringToValueArray(value: String): List<Value> {
    val data = lenientJson.parseToJsonElement(value).jsonArray.toList()
    return convertToArrayValue(data)
}

fun stringTooPatternArray(value: String): List<Pattern> {
    val data = lenientJson.parseToJsonElement(value).jsonArray.toList()
    return convertToArrayPattern(data)
}
