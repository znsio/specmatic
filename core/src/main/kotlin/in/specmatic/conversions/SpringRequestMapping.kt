package `in`.specmatic.conversions

public fun convertPathParameterStyle(path: String) = path.replace(Regex("""\((.*?):.*?\)"""), "{$1}")
