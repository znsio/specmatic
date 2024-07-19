package io.specmatic.core

data class GherkinStatement(val statement: String, val prefix: String) {
    fun toGherkinString() = "$prefix $statement"
}