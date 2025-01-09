package io.specmatic.core.config.v1

data class Environment(
    val baseurls: Map<String, String>? = null,
    val variables: Map<String, String>? = null
)