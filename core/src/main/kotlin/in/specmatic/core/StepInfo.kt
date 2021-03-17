package `in`.specmatic.core

import io.cucumber.messages.Messages

data class StepInfo(val text: String, val rowsList: MutableList<Messages.GherkinDocument.Feature.TableRow>, val raw: Messages.GherkinDocument.Feature.Step) {
    val line = text.trim()
    val words = line.split("\\s+".toRegex(), 2)
    val originalKeyword = words[0]
    val keyword = originalKeyword.toUpperCase()
    val rest = if (words.size == 2) words[1] else ""

    val docString: String = if(raw.hasDocString()) raw.docString.content else ""

    val isEmpty = line.isEmpty()
}