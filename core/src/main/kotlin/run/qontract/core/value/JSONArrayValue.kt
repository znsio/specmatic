package run.qontract.core.value

import run.qontract.core.pattern.JSONArrayPattern
import run.qontract.core.pattern.Pattern
import run.qontract.core.pattern.withoutPatternDelimiters
import run.qontract.core.utilities.valueArrayToJsonString

data class JSONArrayValue(val list: List<Value>) : Value {
    override val httpContentType: String = "application/json"

    override fun displayableValue(): String = toStringValue()
    override fun toStringValue() = valueArrayToJsonString(list)
    override fun displayableType(): String = "json array"
    override fun exactMatchElseType(): Pattern = JSONArrayPattern(list.map { it.exactMatchElseType() })
    override fun type(): Pattern = JSONArrayPattern()

    private fun typeDeclaration(key: String, types: Map<String, Pattern>, examples: ExampleDeclaration, typeDeclarationCall: (Value, String, Map<String, Pattern>, ExampleDeclaration) -> Pair<TypeDeclaration, ExampleDeclaration>): Pair<TypeDeclaration, ExampleDeclaration> = when {
        list.isEmpty() -> Pair(TypeDeclaration("[]", types), examples)
        else -> {
            val declarations = list.map {
                val (typeDeclaration, newExamples) = typeDeclarationCall(it, key, types, examples)
                Pair(TypeDeclaration("(${withoutPatternDelimiters(typeDeclaration.typeValue)}*)", typeDeclaration.types), newExamples)
            }.let { declarations ->
                when {
                    list.first() is ScalarValue -> declarations.map { Pair(removeKey(it.first), examples) }
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

    override fun typeDeclarationWithKey(key: String, types: Map<String, Pattern>, examples: ExampleDeclaration): Pair<TypeDeclaration, ExampleDeclaration> =
            typeDeclaration(key, types, examples) { value, innerKey, innerTypes, newExamples -> value.typeDeclarationWithKey(innerKey, innerTypes, newExamples) }

    override fun typeDeclarationWithoutKey(exampleKey: String, types: Map<String, Pattern>, examples: ExampleDeclaration): Pair<TypeDeclaration, ExampleDeclaration> =
            typeDeclaration(exampleKey, types, examples) { value, innerKey, innerTypes, newExamples -> value.typeDeclarationWithoutKey(innerKey, innerTypes, newExamples) }

    override fun toString() = valueArrayToJsonString(list)
}
