package io.specmatic.core.config

data class Environment(
    val baseurls: Map<String, String>? = null,
    val variables: Map<String, String>? = null
)