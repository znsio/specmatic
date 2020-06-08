package run.qontract.core.value

import run.qontract.core.pattern.*

data class TypeDeclaration(val typeValue: String, val types: Map<String, Pattern> = emptyMap())

fun toExampleDeclaration(examples: Map<String, String>): ExampleDeclaration {
    return ExampleDeclaration(examples.filterNot { isPatternToken(it.value) })
}

data class ExampleDeclaration constructor(val examples: Map<String, String> = emptyMap(), val messages: List<String> = emptyList()) {
    fun plus(more: ExampleDeclaration): ExampleDeclaration {
        val duplicateMessage = messageWhenDuplicateKeysExist(more, examples)
        for(message in duplicateMessage)
            println(duplicateMessage)

        return this.copy(examples = examples.plus(more.examples.filterNot { isPatternToken(it.value) }), messages = messages.plus(more.messages).plus(duplicateMessage))
    }

    fun plus(more: Pair<String, String>): ExampleDeclaration = when {
        isPatternToken(more.second) -> this
        else -> this.copy(examples = examples.plus(more))
    }
}

internal fun messageWhenDuplicateKeysExist(newExamples: ExampleDeclaration, examples: Map<String, String>): List<String> {
    val duplicateKeys = newExamples.examples.keys.filter { it in examples }.filter { key ->
        val oldValue = examples.getValue(key)
        val newValue = newExamples.examples.getValue(key)

        oldValue != newValue
    }

    return when {
        duplicateKeys.isNotEmpty() -> {
            val keysCsv = duplicateKeys.joinToString(", ")
            listOf("Duplicate keys with different values found: $keysCsv")
        }
        else -> emptyList()
    }
}

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
            compatibleArrayTypes(val1.pattern, val2.pattern) -> concreteArrayType(val1.pattern, val2.pattern)
            else -> throw(ShortCircuitException("Found two different types (${val1.pattern} and ${val2.pattern}) in one of the lists, can't converge on a common type for it"))
        }
    }

    return TabularPattern(common.plus(missingIn1).plus(missingIn2))
}

fun concreteArrayType(type1: String, type2: String): DeferredPattern {
    return DeferredPattern(when (type1) {
        "[]" -> type2
        else -> type1
    })
}

fun compatibleArrayTypes(type1: String, type2: String): Boolean {
    fun cleanup(type: String): String = "(${withoutOptionality(withoutPatternDelimiters(type))})"
    return (isRepeatingPattern(cleanup(type1)) && type2 == "[]") || (type1 == "[]" && isRepeatingPattern(cleanup(type2)))
}

class ShortCircuitException(message: String) : Exception(message)

fun primitiveTypeDeclarationWithKey(key: String, examples: ExampleDeclaration, displayableType: String, stringValue: String): Pair<TypeDeclaration, ExampleDeclaration> {
    val (newTypeName, exampleKey) = when (key) {
        !in examples.examples -> Pair(displayableType, key)
        else -> {
            val exampleKey = getNewTypeName(key, examples.examples.keys)
            Pair("$exampleKey: ${withoutPatternDelimiters(displayableType)}", exampleKey)
        }
    }

    return Pair(TypeDeclaration("(${newTypeName})"), examples.plus(exampleKey to stringValue))
}

fun primitiveTypeDeclarationWithoutKey(key: String, examples: ExampleDeclaration, displayableType: String, stringValue: String): Pair<TypeDeclaration, ExampleDeclaration> {
    val (newTypeName, exampleKey) = when (key) {
        !in examples.examples -> Pair("$key: $displayableType", key)
        else -> {
            val exampleKey = getNewTypeName(key, examples.examples.keys)
            Pair("$exampleKey: ${withoutPatternDelimiters(displayableType)}", exampleKey)
        }
    }

    return Pair(TypeDeclaration("(${newTypeName})"), examples.plus(exampleKey to stringValue))
}
