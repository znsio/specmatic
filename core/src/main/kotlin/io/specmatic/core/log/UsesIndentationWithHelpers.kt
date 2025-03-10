package io.specmatic.core.log

interface UsesIndentationWithHelpers : UsesIndentation {
    fun currentIndentation(): String
}