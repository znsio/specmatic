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

    override fun typeDeclaration(typeName: String): TypeDeclaration = when {
        list.isEmpty() -> TypeDeclaration("[]")
        else -> {
            val typeDeclarations = list.map {
                val typeDeclaration = it.typeDeclaration(typeName)
                TypeDeclaration("(${withoutPatternDelimiters(typeDeclaration.typeValue)}*)", typeDeclaration.types)
            }

            val collision = typeDeclarations.asSequence().map { it.collidingName }.filterNotNull().firstOrNull()

            when {
                collision != null -> {
                    println("Type name collision detected in type $collision, convergence of the array containing this type will be avoided")
                    typeDeclarations.first()
                }
                else -> {
                    typeDeclarations.reduce { converged, current -> convergeTypeDeclarations(converged, current) }
                }
            }
        }
    }

    override fun toString() = valueArrayToJsonString(list)
}
