package run.qontract.core.value

import run.qontract.core.pattern.DeferredPattern
import run.qontract.core.pattern.JSONObjectPattern
import run.qontract.core.pattern.toJSONObjectPattern
import run.qontract.core.pattern.Pattern
import run.qontract.core.pattern.toTabularPattern
import run.qontract.core.utilities.valueMapToPrettyJsonString

data class JSONObjectValue(val jsonObject: Map<String, Value> = emptyMap()) : Value {
    override val httpContentType = "application/json"

    override fun displayableValue() = toStringValue()
    override fun toStringValue() = valueMapToPrettyJsonString(jsonObject)
    override fun displayableType(): String = "json object"
    override fun exactMatchElseType(): Pattern = toJSONObjectPattern(jsonObject.mapValues { it.value.exactMatchElseType() })
    override fun type(): Pattern = JSONObjectPattern()

    override fun toString() = valueMapToPrettyJsonString(jsonObject)

    override fun typeDeclarationWithKey(key: String, types: Map<String, Pattern>, examples: ExampleDeclaration): Pair<TypeDeclaration, ExampleDeclaration> {
        val (jsonTypeMap, newTypes, newExamples) = dictionaryToDeclarations(jsonObject, types, examples)

        val newType = toTabularPattern(jsonTypeMap.mapValues {
            DeferredPattern(it.value.pattern)
        })

        val newTypeName = examples.getNewName(key.capitalize(), newTypes.keys)

        val typeDeclaration = TypeDeclaration("($newTypeName)", newTypes.plus(newTypeName to newType))

        return Pair(typeDeclaration, newExamples)
    }

    override fun listOf(valueList: List<Value>): Value {
        return JSONArrayValue(valueList)
    }

    override fun typeDeclarationWithoutKey(exampleKey: String, types: Map<String, Pattern>, examples: ExampleDeclaration): Pair<TypeDeclaration, ExampleDeclaration> =
            typeDeclarationWithKey(exampleKey, types, examples)

    fun getString(key: String): String {
        return (jsonObject.getValue(key) as StringValue).string
    }

    fun getBoolean(key: String): Boolean {
        return (jsonObject.getValue(key) as BooleanValue).booleanValue
    }

    fun getInt(key: String): Int {
        return (jsonObject.getValue(key) as NumberValue).number.toInt()
    }

    fun getJSONObject(key: String): Map<String, Value> {
        return (jsonObject.getValue(key) as JSONObjectValue).jsonObject
    }

    fun getJSONObjectValue(key: String): JSONObjectValue {
        return jsonObject.getValue(key) as JSONObjectValue
    }

    fun getJSONArray(key: String): List<Value> {
        return (jsonObject.getValue(key) as JSONArrayValue).list
    }
}

internal fun dictionaryToDeclarations(jsonObject: Map<String, Value>, types: Map<String, Pattern>, examples: ExampleDeclaration): Triple<Map<String, DeferredPattern>, Map<String, Pattern>, ExampleDeclaration> {
    return jsonObject
            .entries
            .fold(Triple(emptyMap(), types, examples)) { acc, entry ->
                val (jsonTypeMap, accTypes, accExamples) = acc
                val (key, value) = entry

                val (newTypes, newExamples) = value.typeDeclarationWithKey(key, accTypes, accExamples)
                Triple(jsonTypeMap.plus(key to DeferredPattern(newTypes.typeValue)), newTypes.types, newExamples)
            }
}
