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

    override fun typeDeclarationWithKey(key: String, types: Map<String, Pattern>, examples: ExampleDeclaration): Pair<TypeDeclaration, ExampleDeclaration> {
        val (typeDeclarations, newTypes, newExamples) = dictionaryToDeclarations2(jsonObject, types, examples)

        val newType = TabularPattern(typeDeclarations.mapValues {
            DeferredPattern(it.value.pattern)
        })

        val newTypeName = getNewTypeName(key.capitalize(), typeDeclarations.keys)

//        val mergedTypeMap = typeDeclarations.entries.fold(emptyMap<String, Pattern>()) { acc, entry ->
//            acc.plus(entry.value.types)
//        }.plus(newTypeName to newType)

        val typeDeclaration = TypeDeclaration("($newTypeName)", newTypes.plus(newTypeName to newType))

        return Pair(typeDeclaration, newExamples)
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

fun getNewTypeName(typeName: String, keys: Collection<String>): String {
    return generateSequence(typeName) { "${it}_" }.first { it !in keys }
}

fun dictionaryToDeclarations2(jsonObject: Map<String, Value>, types: Map<String, Pattern>, examples: ExampleDeclaration): Triple<Map<String, DeferredPattern>, Map<String, Pattern>, ExampleDeclaration> {
    return jsonObject
            .entries
            .fold(Triple(emptyMap(), types, examples)) { acc, entry ->
                val (jsonTypeMap, types, examples) = acc
                val (key, value) = entry

                val (newTypes, newExamples) = value.typeDeclarationWithKey(key, types, examples)
                Triple(jsonTypeMap.plus(key to DeferredPattern(newTypes.typeValue)), newTypes.types, newExamples)
            }
}
