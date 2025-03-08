package io.specmatic.core.log

interface UsesIndentation {
    fun <T> withIndentation(count: Int, block: () -> T): T
    fun currentIndentation(): String
}

class UsesIndentationImpl(private var indentation: Int = 0) : UsesIndentation {
    @Synchronized
    override fun <T> withIndentation(count: Int, block: () -> T): T {
        indentation += count
        return try {
            block()
        } finally {
            indentation -= count
        }
    }

    @Synchronized
    override fun currentIndentation(): String {
        return " ".repeat(indentation)
    }
}