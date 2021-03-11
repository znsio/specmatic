package run.qontract.core.value

import run.qontract.core.ExampleDeclarations
import run.qontract.core.pattern.*
import run.qontract.core.utilities.valueMapToPrettyJsonString

data class JSONObjectValue(val jsonObject: Map<String, Value> = emptyMap()) : Value {
    override val httpContentType = "application/json"

    override fun displayableValue() = toStringValue()
    override fun toStringValue() = valueMapToPrettyJsonString(jsonObject)
    override fun displayableType(): String = "json object"
    override fun exactMatchElseType(): Pattern = toJSONObjectPattern(jsonObject.mapValues { it.value.exactMatchElseType() })
    override fun type(): Pattern = JSONObjectPattern()

    override fun toString() = valueMapToPrettyJsonString(jsonObject)

    override fun typeDeclarationWithKey(key: String, types: Map<String, Pattern>, exampleDeclarations: ExampleDeclarations): Pair<TypeDeclaration, ExampleDeclarations> {
        val (jsonTypeMap, newTypes, newExamples) = dictionaryToDeclarations(jsonObject, types, exampleDeclarations)

        val newType = toTabularPattern(jsonTypeMap.mapValues {
            DeferredPattern(it.value.pattern)
        })

        val newTypeName = exampleDeclarations.getNewName(key.capitalize(), newTypes.keys)

        val typeDeclaration = TypeDeclaration("($newTypeName)", newTypes.plus(newTypeName to newType))

        return Pair(typeDeclaration, newExamples)
    }

    override fun listOf(valueList: List<Value>): Value {
        return JSONArrayValue(valueList)
    }

    override fun typeDeclarationWithoutKey(exampleKey: String, types: Map<String, Pattern>, exampleDeclarations: ExampleDeclarations): Pair<TypeDeclaration, ExampleDeclarations> =
            typeDeclarationWithKey(exampleKey, types, exampleDeclarations)

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

    fun findFirstChildByPath(path: String): Value? =
        findFirstChildByPath(path.split("."))

    private fun findFirstChildByPath(path: List<String>): Value? =
        findFirstChildByPath(path.first(), path.drop(1))

    private fun findFirstChildByPath(childName: String, rest: List<String>): Value? {
        return findFirstChildByName(childName)?.let {
            if(it is JSONObjectValue)
                findFirstChildByPath(rest)
            else
                null
        }
    }

    fun findFirstChildByName(name: String): Value? =
        jsonObject[name]
}

internal fun dictionaryToDeclarations(jsonObject: Map<String, Value>, types: Map<String, Pattern>, exampleDeclarations: ExampleDeclarations): Triple<Map<String, DeferredPattern>, Map<String, Pattern>, ExampleDeclarations> {
    return jsonObject
            .entries
            .fold(Triple(emptyMap(), types, exampleDeclarations)) { acc, entry ->
                val (jsonTypeMap, accTypes, accExamples) = acc
                val (key, value) = entry

                val (newTypes, newExamples) = value.typeDeclarationWithKey(key, accTypes, accExamples)
                Triple(jsonTypeMap.plus(key to DeferredPattern(newTypes.typeValue)), newTypes.types, newExamples)
            }
}
