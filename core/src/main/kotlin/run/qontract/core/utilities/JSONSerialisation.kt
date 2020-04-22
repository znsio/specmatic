package run.qontract.core.utilities

import kotlinx.serialization.ImplicitReflectionSerializer
import kotlinx.serialization.UnstableDefault
import kotlinx.serialization.json.*
import kotlinx.serialization.stringify
import run.qontract.core.pattern.*
import run.qontract.core.value.*

@OptIn(UnstableDefault::class)
val indentedJson = Json(JsonConfiguration(prettyPrint = true))

@OptIn(UnstableDefault::class)
val lenientJson = Json(JsonConfiguration(isLenient = true))

@OptIn(ImplicitReflectionSerializer::class, UnstableDefault::class)
fun valueArrayToJsonString(value: List<Value>): String {
    val data = valueListToElements(value)

    return indentedJson.stringify(data)
}

fun toMap(value: Value?) = jsonStringToValueMap(value.toString())

@OptIn(ImplicitReflectionSerializer::class, UnstableDefault::class)
internal fun prettifyJsonString(content: String): String = indentedJson.stringify(lenientJson.parseJson(content))

@OptIn(UnstableDefault::class)
fun stringToPatternMap(stringContent: String): Map<String, Pattern>  {
    val data = lenientJson.parseJson(stringContent).jsonObject.toMap()
    return convertToMapPattern(data)
}

@OptIn(UnstableDefault::class)
fun jsonStringToValueMap(stringContent: String): Map<String, Value>  {
    val data = lenientJson.parseJson(stringContent).jsonObject.toMap()
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
        else -> throw ContractException("Unknown value type: ${jsonElement.javaClass.name}")
    }
}

fun toLiteralPattern(jsonElement: JsonLiteral): Pattern =
    when {
        jsonElement.isString -> parsedPattern(jsonElement.content)
        jsonElement.booleanOrNull != null -> ExactMatchPattern(BooleanValue(jsonElement.boolean))
        jsonElement.intOrNull != null -> ExactMatchPattern(NumberValue(jsonElement.int))
        jsonElement.longOrNull != null -> ExactMatchPattern(NumberValue(jsonElement.long))
        jsonElement.floatOrNull != null -> ExactMatchPattern(NumberValue(jsonElement.float))
        else -> throw ContractException("Can't recognise the type of $jsonElement")
    }

private fun toValue(jsonElement: JsonElement): Value =
    when (jsonElement) {
        is JsonNull -> NullValue
        is JsonObject -> JSONObjectValue(jsonElement.toMap().mapValues { toValue(it.value) })
        is JsonArray -> JSONArrayValue(jsonElement.toList().map { toValue(it) })
        is JsonLiteral -> toLiteralValue(jsonElement)
        else -> throw ContractException("Unknown value type: ${jsonElement.javaClass.name}")
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

fun convertToArrayValue(data: List<JsonElement>): List<Value> =
    data.map { toValue(it) }

fun convertToArrayPattern(data: List<JsonElement>): List<Pattern> =
    data.map { toPattern(it) }

@OptIn(ImplicitReflectionSerializer::class, UnstableDefault::class)
fun valueMapToPrettyJsonString(value: Map<String, Value>): String {
    val data = mapToStringElement(value)
    return indentedJson.stringify(data)
}

@OptIn(ImplicitReflectionSerializer::class, UnstableDefault::class)
fun valueMapToPlainJsonString(value: Map<String, Value>): String {
    val data = mapToStringElement(value)
    return lenientJson.stringify(data)
}

fun mapToStringElement(data: Map<String, Value>): Map<String, JsonElement> {
    return data.mapValues { valueToJsonElement(it.value) }
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

fun valueListToElements(values: List<Value>): JsonArray {
    return JsonArray(values.map { valueToJsonElement(it) })
}

@OptIn(UnstableDefault::class)
fun jsonStringToValueArray(value: String): List<Value> {
    val data = lenientJson.parseJson(value).jsonArray.toList()
    return convertToArrayValue(data)
}

@OptIn(UnstableDefault::class)
fun stringTooPatternArray(value: String): List<Pattern> {
    val data = lenientJson.parseJson(value).jsonArray.toList()
    return convertToArrayPattern(data)
}
