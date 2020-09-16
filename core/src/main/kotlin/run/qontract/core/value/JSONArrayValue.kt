package run.qontract.core.value

import run.qontract.core.ExampleDeclarations
import run.qontract.core.pattern.JSONArrayPattern
import run.qontract.core.pattern.Pattern
import run.qontract.core.pattern.withoutPatternDelimiters
import run.qontract.core.utilities.valueArrayToJsonString

data class JSONArrayValue(override val list: List<Value>) : Value, ListValue {
    override val httpContentType: String = "application/json"

    override fun displayableValue(): String = toStringValue()
    override fun toStringValue() = valueArrayToJsonString(list)
    override fun displayableType(): String = "json array"
    override fun exactMatchElseType(): Pattern = JSONArrayPattern(list.map { it.exactMatchElseType() })
    override fun type(): Pattern = JSONArrayPattern()

    private fun typeDeclaration(key: String, types: Map<String, Pattern>, exampleDeclarations: ExampleDeclarations, typeDeclarationsStoreCall: (Value, String, Map<String, Pattern>, ExampleDeclarations) -> Pair<TypeDeclaration, ExampleDeclarations>): Pair<TypeDeclaration, ExampleDeclarations> = when {
        list.isEmpty() -> Pair(TypeDeclaration("[]", types), exampleDeclarations)
        else -> {
            val declarations = list.map {
                val (typeDeclaration, newExamples) = typeDeclarationsStoreCall(it, key, types, exampleDeclarations)
                Pair(TypeDeclaration("(${withoutPatternDelimiters(typeDeclaration.typeValue)}*)", typeDeclaration.types), newExamples)
            }.let { declarations ->
                when {
                    list.first() is ScalarValue -> declarations.map { Pair(removeKey(it.first), exampleDeclarations) }
                    else -> declarations
                }
            }

            val newExamples = declarations.first().second
            val convergedType = declarations.map { it.first }.reduce { converged, current -> convergeTypeDeclarations(converged, current) }

            Pair(convergedType, newExamples)
        }
    }

    private fun removeKey(declaration: TypeDeclaration): TypeDeclaration {
        val newTypeValue = when {
            declaration.typeValue.contains(":") -> {
                val withoutKey = withoutPatternDelimiters(declaration.typeValue).split(":")[1].trim()
                "($withoutKey)"
            }
            else -> declaration.typeValue
        }

        return declaration.copy(typeValue = newTypeValue)
    }

    override fun typeDeclarationWithKey(key: String, types: Map<String, Pattern>, exampleDeclarations: ExampleDeclarations): Pair<TypeDeclaration, ExampleDeclarations> =
            typeDeclaration(key, types, exampleDeclarations) { value, innerKey, innerTypes, newExamples -> value.typeDeclarationWithKey(innerKey, innerTypes, newExamples) }

    override fun listOf(valueList: List<Value>): Value {
        TODO("Not yet implemented")
    }

    override fun typeDeclarationWithoutKey(exampleKey: String, types: Map<String, Pattern>, exampleDeclarations: ExampleDeclarations): Pair<TypeDeclaration, ExampleDeclarations> =
            typeDeclaration(exampleKey, types, exampleDeclarations) { value, innerKey, innerTypes, newExamples -> value.typeDeclarationWithoutKey(innerKey, innerTypes, newExamples) }

    override fun toString() = valueArrayToJsonString(list)
}
