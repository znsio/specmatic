package run.qontract.core.value

import run.qontract.core.pattern.*

data class TypeDeclaration(val typeValue: String, val types: Map<String, Pattern> = emptyMap(), val collidingName: String? = null)

fun convergeTypeDeclarations(accumulator: TypeDeclaration, newPattern: TypeDeclaration): TypeDeclaration {
    return try {
        val differences = accumulator.types.filterKeys { it !in newPattern.types }.plus(newPattern.types.filterKeys { it !in accumulator.types })

        val similarities = accumulator.types.filterKeys { it in newPattern.types }.mapValues {
            val (pattern1, pattern2) = listOf(accumulator, newPattern).map { typeDeclaration -> typeDeclaration.types.getValue(it.key) as TabularPattern }
            converge(pattern1, pattern2)
        }

        TypeDeclaration(accumulator.typeValue, differences.plus(similarities))
    } catch(e: ShortCircuitException) {
        println(e.localizedMessage)
        accumulator
    }
}

fun converge(accumulator: TabularPattern, newPattern: TabularPattern): TabularPattern {
    val json1 = accumulator.pattern
    val json2 = newPattern.pattern

    val missingIn2 = json1.filterKeys { withoutOptionality(it) !in json2 }.mapKeys { "${withoutOptionality(it.key)}?" }

    val json1KeysWithoutOptionality = json1.keys.map { withoutOptionality(it) }
    val missingIn1 = json2.filterKeys { it !in json1KeysWithoutOptionality }.mapKeys { "${it.key}?" }

    val common = json1.filterKeys { withoutOptionality(it) in json2 }.mapValues {
        val val1 = json1.getValue(it.key) as DeferredPattern
        val val2 = json2.getValue(withoutOptionality(it.key)) as DeferredPattern

        when {
            val1.pattern == "(null)" && val2.pattern == "(null)" -> DeferredPattern("(null)")
            withoutOptionality(withoutPatternDelimiters(val1.pattern)) == withoutPatternDelimiters(val2.pattern) -> val1
            val1.pattern == "(null)" -> DeferredPattern("(${withoutPatternDelimiters(val2.pattern)}?)")
            val2.pattern == "(null)" -> DeferredPattern("(${withoutOptionality(withoutPatternDelimiters(val1.pattern))}?)")
            else -> throw(ShortCircuitException("Found two different types (${val1.pattern} and ${val2.pattern}) in one of the lists, can't converge on a common type for it"))
        }
    }

    return TabularPattern(common.plus(missingIn1).plus(missingIn2))
}

class ShortCircuitException(message: String) : Throwable()
