package run.qontract.core

data class GherkinStatement(val statement: String, val prefix: String) {
    fun toGherkinString() = "$prefix $statement"
}