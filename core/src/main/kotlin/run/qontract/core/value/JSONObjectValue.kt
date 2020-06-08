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

    override fun typeDeclarationWithKey(key: String, examples: ExampleDeclaration): Pair<TypeDeclaration, ExampleDeclaration> {
        val (typeDeclarations, newExamples) = dictionaryToDeclarations(jsonObject, examples)

        val newType = TabularPattern(typeDeclarations.mapValues {
            DeferredPattern(it.value.typeValue)
        })

        val newTypeName = getNewTypeName(key.capitalize(), typeDeclarations.keys)

        val mergedTypeMap = typeDeclarations.entries.fold(emptyMap<String, Pattern>()) { acc, entry ->
            acc.plus(entry.value.types)
        }.plus(newTypeName to newType)

        val typeDeclaration = TypeDeclaration("($newTypeName)", mergedTypeMap)

        return Pair(typeDeclaration, newExamples)
    }

    override fun typeDeclarationWithoutKey(exampleKey: String, examples: ExampleDeclaration): Pair<TypeDeclaration, ExampleDeclaration> =
            typeDeclarationWithKey(exampleKey, examples)

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

fun dictionaryToDeclarations(jsonObject: Map<String, Value>, examples: ExampleDeclaration): Pair<Map<String, TypeDeclaration>, ExampleDeclaration> {
    return jsonObject
            .entries
            .fold(Pair(emptyMap(), examples)) { acc, entry ->
                val (typeDeclaration, newExamples) = entry.value.typeDeclarationWithKey(entry.key, acc.second)
                Pair(acc.first.plus(mapOf(entry.key to typeDeclaration)), newExamples)
            }
}
