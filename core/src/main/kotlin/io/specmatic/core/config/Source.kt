package io.specmatic.core.config

import com.fasterxml.jackson.annotation.JsonAlias

enum class SourceProvider { git, filesystem, web }

data class Source(
    @field:JsonAlias("type")
    val provider: SourceProvider = SourceProvider.filesystem,
    val repository: String? = null,
    val branch: String? = null,
    @field:JsonAlias("provides")
    val test: List<String>? = null,
    @field:JsonAlias("consumes")
    val stub: List<String>? = null,
    val directory: String? = null,
)