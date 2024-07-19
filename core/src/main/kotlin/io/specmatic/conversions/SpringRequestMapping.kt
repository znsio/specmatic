package io.specmatic.conversions

fun convertPathParameterStyle(path: String) = path.replace(Regex("""\((.*?):.*?\)"""), "{$1}")
