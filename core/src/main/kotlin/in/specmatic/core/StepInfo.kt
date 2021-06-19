package `in`.specmatic.core

import io.cucumber.messages.types.Step
import io.cucumber.messages.types.TableRow

data class StepInfo(val text: String, val rowsList: MutableList<TableRow>, val raw: Step) {
    val line = text.trim()
    val words = line.split("\\s+".toRegex(), 2)
    val originalKeyword = words[0]
    val keyword = originalKeyword.uppercase()
    val rest = if (words.size == 2) words[1] else ""

    val docString: String = if(raw.docString != null) raw.docString.content else ""

    val isEmpty = line.isEmpty()
}