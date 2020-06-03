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

    override fun typeDeclaration(typeName: String): Pair<TypeDeclaration, ExampleDeclaration> {
        val rawTypeMap = jsonObject.mapValues { it.value.typeDeclaration(it.key.capitalize()) }

        val newType = TabularPattern(rawTypeMap.mapValues {
            DeferredPattern(it.value.first.typeValue)
        })

        val newTypeName = generateSequence(typeName) { "${it}_" }.first { it !in rawTypeMap }.let { "($it)" }
        val collidingName = if(newTypeName != typeName) typeName else null

        val mergedTypeMap = rawTypeMap.entries.fold(emptyMap<String, Pattern>()) { acc, entry ->
            acc.plus(entry.value.first.types)
        }.plus(newTypeName to newType)

        val examples = rawTypeMap.entries.map { it.value.second }
        val allKeys = examples.flatMap { it.examples.keys }
        val uniqueKeys = allKeys.toSet()

        val typeDeclaration = TypeDeclaration(newTypeName, mergedTypeMap, collidingName)

        if(allKeys.size > uniqueKeys.size) {
            println("Duplicate keys names found, skipping the generation of examples")
            return Pair(typeDeclaration, ExampleDeclaration())
        }

        val consolidatedExamples = examples.fold(ExampleDeclaration()) { acc, exampleDeclaration ->
            ExampleDeclaration(acc.examples.plus(exampleDeclaration.examples))
        }

        val newExampleEntries = rawTypeMap.entries.mapNotNull { (key, declarations) ->
            val (_, exampleDeclaration) = declarations
            exampleDeclaration.newValue?.let { Pair(key, it) }
        }

        if(newExampleEntries.any { it.first in consolidatedExamples.examples}) {
            println("Duplicate keys names found, skipping the generation of examples")
            return Pair(typeDeclaration, ExampleDeclaration())
        }

        return Pair(typeDeclaration, newExampleEntries.fold(consolidatedExamples) { acc, entry ->
            ExampleDeclaration(acc.examples.plus(entry))
        })
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
