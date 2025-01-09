package io.specmatic.core.config.v2

import io.specmatic.core.config.v1.Source
import io.specmatic.core.config.v1.SourceProvider

data class ContractConfig(
    val type: SourceProvider = SourceProvider.filesystem,
    val url: String? = null,
    val branch: String? = null,
    val directory: String? = null,
    val provides: List<String>? = null,
    val consumes: List<String>? = null
) {
    fun transform(): Source {
        return Source(
            provider = this.type,
            repository = this.url,
            branch = this.branch,
            test = this.provides,
            stub = this.consumes,
            directory = this.directory
        )
    }
}