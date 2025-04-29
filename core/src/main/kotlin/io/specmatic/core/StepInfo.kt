package io.specmatic.core

import io.cucumber.messages.types.Step
import io.cucumber.messages.types.TableRow
import kotlin.jvm.optionals.getOrNull

data class StepInfo(val text: String, val rowsList: MutableList<TableRow>, val raw: Step) {
    val line = text.trim()
    val words = line.split("\\s+".toRegex(), 2)
    val originalKeyword = words[0]
    val keyword = originalKeyword.uppercase()
    val rest = if (words.size == 2) words[1] else ""

    val docString: String = raw.docString.getOrNull()?.content.orEmpty()

    val isEmpty = line.isEmpty()
}