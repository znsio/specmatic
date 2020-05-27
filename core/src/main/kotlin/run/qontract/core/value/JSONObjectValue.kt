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

        val mergedTypeMap = rawTypeMap.entries.fold(emptyMap<String, Pattern>()) { acc, entry ->
            acc.plus(entry.value.types)
        }.plus("($typeName)" to newType)

        return TypeDeclaration("($typeName)", mergedTypeMap)
    }
}
