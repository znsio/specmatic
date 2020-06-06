package run.qontract.core.value

import run.qontract.core.pattern.*
import run.qontract.core.utilities.valueArrayToJsonString

data class JSONArrayValue(val list: List<Value>) : Value {
    override val httpContentType: String = "application/json"

    override fun displayableValue(): String = toStringValue()
    override fun toStringValue() = valueArrayToJsonString(list)
    override fun displayableType(): String = "json array"
    override fun toExactType(): Pattern = JSONArrayPattern(list.map { it.toExactType() })
    override fun type(): Pattern = JSONArrayPattern()

    override fun typeDeclarationWithKey(key: String, examples: ExampleDeclaration): Pair<TypeDeclaration, ExampleDeclaration> = when {
        list.isEmpty() -> Pair(TypeDeclaration("[]"), ExampleDeclaration())
        else -> {
            val typeDeclarations = list.map {
                val (typeDeclaration, examples) = it.typeDeclarationWithKey(key, examples)
                TypeDeclaration("(${withoutPatternDelimiters(typeDeclaration.typeValue)}*)", typeDeclaration.types)
            }

            val collision = typeDeclarations.asSequence().map { it.collidingName }.filterNotNull().firstOrNull()

            when {
                collision != null -> {
                    println("Type name collision detected in type $collision, convergence of the array containing this type will be avoided")
                    Pair(typeDeclarations.first(), ExampleDeclaration())
                }
                else -> {
                    Pair(typeDeclarations.reduce { converged, current -> convergeTypeDeclarations(converged, current) }, ExampleDeclaration())
                }
            }
        }
    }

    override fun typeDeclarationWithoutKey(exampleKey: String, examples: ExampleDeclaration): Pair<TypeDeclaration, ExampleDeclaration> =
            typeDeclarationWithKey(exampleKey, examples)

    override fun toString() = valueArrayToJsonString(list)
}
