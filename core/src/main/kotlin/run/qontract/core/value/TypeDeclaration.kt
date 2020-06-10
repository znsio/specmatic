package run.qontract.core.value

import run.qontract.core.pattern.*

data class TypeDeclaration(val typeValue: String, val types: Map<String, Pattern> = emptyMap())

fun convergeTypeDeclarations(accumulator: TypeDeclaration, newPattern: TypeDeclaration): TypeDeclaration {
    val differences = accumulator.types.filterKeys { it !in newPattern.types }.plus(newPattern.types.filterKeys { it !in accumulator.types })

    val similarities = accumulator.types.filterKeys { it in newPattern.types }.mapValues {
        val (pattern1, pattern2) = listOf(accumulator, newPattern).map { typeDeclaration -> typeDeclaration.types.getValue(it.key) as TabularPattern }
        converge(pattern1, pattern2)
    }

    return TypeDeclaration(accumulator.typeValue, differences.plus(similarities))
}

fun converge(accumulator: TabularPattern, newPattern: TabularPattern): TabularPattern {
    val json1 = accumulator.pattern
    val json2 = newPattern.pattern

    val missingIn2 = json1.filterKeys { withoutOptionality(it) !in json2 }.mapKeys { "${withoutOptionality(it.key)}?" }

    val json1KeysWithoutOptionality = json1.keys.map { withoutOptionality(it) }
    val json2KeysWithoutOptionality = json2.keys.map { withoutOptionality(it) }

    val missingIn1 = json2.filterKeys { withoutOptionality(it) !in json1KeysWithoutOptionality }.mapKeys { "${withoutOptionality(it.key)}?" }

    val common = json1.filterKeys { withoutOptionality(it) in json2KeysWithoutOptionality }.mapValues {
        val val1 = json1.getValue(it.key) as DeferredPattern
        val val2 = (json2[it.key] ?: json2[withoutOptionality(it.key)] ?: json2.getValue("${it.key}?")) as DeferredPattern

        when {
            isNull(val1.pattern) && isNull(val2.pattern) -> val1
            withoutVariable(withoutOptionality(withoutPatternDelimiters(val1.pattern))) == withoutVariable(withoutOptionality(withoutPatternDelimiters(val2.pattern))) -> val1
            isNull(val1.pattern) -> DeferredPattern("(${withoutOptionality(withoutPatternDelimiters(val2.pattern.trim()))}?)")
            isNull(val2.pattern) -> DeferredPattern("(${withoutOptionality(withoutPatternDelimiters(val1.pattern.trim()))}?)")
            oneIsEmptyArray(val1.pattern, val2.pattern) -> selectConcreteArrayType(val1.pattern, val2.pattern)
            else -> {
                println("Found two different types (${val1.pattern} and ${val2.pattern}) in one of the lists, can't converge on a common type for it. Choosing ${val1.pattern} for now.")
                val1
            }
        }
    }

    val converged = common.plus(missingIn1).plus(missingIn2)

    if(converged.any { it.key.contains("??") }) {
        println(converged.keys)
    }

    return TabularPattern(converged)
}

fun isNull(type: String): Boolean {
    return when {
        !isPatternToken(type) -> false
        else -> withoutVariable(type) == "(null)"
    }
}

fun withoutVariable(type: String): String {
    return when {
        type.contains(":") -> {
            val rawType = withoutPatternDelimiters(type).split(":".toRegex(), 2)[1].trim()
            "($rawType)"
        }
        else -> type
    }
}

fun selectConcreteArrayType(type1: String, type2: String): DeferredPattern {
    return DeferredPattern(when (type1) {
        "[]" -> type2
        else -> type1
    })
}

fun oneIsEmptyArray(type1: String, type2: String): Boolean {
    fun cleanup(type: String): String = "(${withoutOptionality(withoutPatternDelimiters(type.trim()))})"
    return (isRepeatingPattern(cleanup(type1)) && type2 == "[]") || (type1 == "[]" && isRepeatingPattern(cleanup(type2)))
}

class ShortCircuitException(message: String) : Exception(message)

fun primitiveTypeDeclarationWithKey(key: String, types: Map<String, Pattern>, examples: ExampleDeclaration, displayableType: String, stringValue: String): Pair<TypeDeclaration, ExampleDeclaration> {
    val (newTypeName, exampleKey) = when (key) {
        !in examples.examples -> Pair(displayableType, key)
        else -> {
            val exampleKey = getNewTypeName(key, examples.examples.keys)
            Pair("$exampleKey: ${withoutPatternDelimiters(displayableType)}", exampleKey)
        }
    }

    return Pair(TypeDeclaration("(${newTypeName})", types), examples.plus(exampleKey to stringValue))
}

fun primitiveTypeDeclarationWithoutKey(key: String, types: Map<String, Pattern>, examples: ExampleDeclaration, displayableType: String, stringValue: String): Pair<TypeDeclaration, ExampleDeclaration> {
    val (newTypeName, exampleKey) = when (key) {
        !in examples.examples -> Pair("$key: $displayableType", key)
        else -> {
            val exampleKey = getNewTypeName(key, examples.examples.keys)
            Pair("$exampleKey: ${withoutPatternDelimiters(displayableType)}", exampleKey)
        }
    }

    return Pair(TypeDeclaration("(${newTypeName})", types), examples.plus(exampleKey to stringValue))
}
