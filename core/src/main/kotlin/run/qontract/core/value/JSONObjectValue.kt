package run.qontract.core.value

import run.qontract.core.pattern.*
import run.qontract.core.utilities.valueMapToPrettyJsonString

data class JSONObjectValue(val jsonObject: Map<String, Value> = emptyMap()) : Value {
    override val httpContentType = "application/json"

    override fun displayableValue() = toStringValue()
    override fun toStringValue() = valueMapToPrettyJsonString(jsonObject)
    override fun displayableType(): String = "json object"
    override fun toExactType(): Pattern = JSONObjectPattern(jsonObject.mapValues { it.value.toExactType() })
    override fun type(): Pattern = JSONObjectPattern()

    override fun toString() = valueMapToPrettyJsonString(jsonObject)

    override fun typeDeclaration(typeName: String): TypeDeclaration {
        val rawTypeMap = jsonObject.mapValues { it.value.typeDeclaration(it.key.capitalize()) }

        val newType = TabularPattern(rawTypeMap.mapValues { DeferredPattern(it.value.typeValue) })

        val newTypeName = generateSequence(typeName) { "${it}_" }.first { it !in rawTypeMap }.let { "($it)" }

        val mergedTypeMap = rawTypeMap.entries.fold(emptyMap<String, Pattern>()) { acc, entry ->
            acc.plus(entry.value.types)
        }.plus(newTypeName to newType)

        return TypeDeclaration(newTypeName, mergedTypeMap)
    }

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
