package run.qontract.core.value

import run.qontract.core.pattern.isPatternToken

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

fun toExampleDeclaration(examples: Map<String, String>): ExampleDeclaration {
    return ExampleDeclaration(examples.filterNot { isPatternToken(it.value) })
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
