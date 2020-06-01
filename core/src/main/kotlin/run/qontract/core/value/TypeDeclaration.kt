package run.qontract.core.value

import run.qontract.core.pattern.*

data class TypeDeclaration(val typeValue: String, val types: Map<String, Pattern> = emptyMap(), val collidingName: String? = null)

fun convergeTypeDeclarations(converged: TypeDeclaration, current: TypeDeclaration): TypeDeclaration {
    return try {
        val differences = converged.types.filterKeys { it !in current.types }.plus(current.types.filterKeys { it !in converged.types })

        val similarities = converged.types.filterKeys { it in current.types }.mapValues {
            val (pattern1, pattern2) = listOf(converged, current).map { typeDeclaration -> typeDeclaration.types.getValue(it.key) as TabularPattern }
            converge(pattern1, pattern2)
        }

        TypeDeclaration(converged.typeValue, differences.plus(similarities))
    } catch(e: ShortCircuitException) {
        println(e.localizedMessage)
        converged
    }
}

fun converge(pattern1: TabularPattern, pattern2: TabularPattern): TabularPattern {
    val json1 = pattern1.pattern
    val json2 = pattern2.pattern

    val missingIn2 = json1.filterKeys { it !in json2 }.mapKeys { "${it.key}?" }
    val missingIn1 = json2.filterKeys { it !in json1 }.mapKeys { "${it.key}?" }

    val common = json1.filterKeys { it in json2 }.mapValues {
        val val1 = json1.getValue(it.key) as DeferredPattern
        val val2 = json2.getValue(it.key) as DeferredPattern

        when {
            val1 == val2 -> val1
            val1.pattern == "(null)" -> DeferredPattern("(${withoutPatternDelimiters(val2.pattern)}?)", val1.key)
            val2.pattern == "(null)" -> DeferredPattern("(${withoutPatternDelimiters(val1.pattern)}?)", val1.key)
            else -> throw(ShortCircuitException("Found two different types (${val1.pattern} and ${val2.pattern}) in one of the lists, can't converge on a common type for it"))
        }
    }

    return TabularPattern(common.plus(missingIn1).plus(missingIn2))
}

class ShortCircuitException(message: String) : Throwable() {

}
