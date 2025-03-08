package io.specmatic.core.log

interface UsesIndentation {
    fun <T> withIndentation(count: Int, block: () -> T): T
}

