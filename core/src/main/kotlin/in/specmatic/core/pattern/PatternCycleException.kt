package `in`.specmatic.core.pattern

data class PatternCycleException(val msg: String, val cycle: List<Pattern>,) : Exception("$msg. cycle=$cycle")
