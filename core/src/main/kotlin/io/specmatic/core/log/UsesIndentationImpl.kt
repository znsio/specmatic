package io.specmatic.core.log

class UsesIndentationImpl(private var indentation: Int = 0) : UsesIndentationWithHelpers {
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