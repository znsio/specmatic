package io.specmatic.core.config.v2

import com.fasterxml.jackson.annotation.JsonIgnore
import com.fasterxml.jackson.annotation.JsonProperty
import com.fasterxml.jackson.databind.annotation.JsonDeserialize
import io.specmatic.core.Source
import io.specmatic.core.SourceProvider
import io.specmatic.core.config.v3.Consumes
import io.specmatic.core.config.v3.ConsumesDeserializer

data class ContractConfig(
    @JsonIgnore
    val contractSource: ContractSource? = null,
    val provides: List<String>? = null,
    val consumes: List<Consumes>? = null
) {
    @Suppress("unused")
    constructor(
        @JsonProperty("git") git: GitContractSource? = null,
        @JsonProperty("filesystem") filesystem: FileSystemContractSource? = null,
        @JsonProperty("provides") provides: List<String>? = null,
        @JsonDeserialize(using = ConsumesDeserializer::class) @JsonProperty("consumes") consumes: List<Consumes>? = null
    ) : this(
        contractSource = git ?: filesystem,
        provides = provides,
        consumes = consumes
    )

    constructor(source: Source) : this(
        contractSource = when {
            source.provider == SourceProvider.git -> GitContractSource(source)
            source.directory != null -> FileSystemContractSource(source)
            else -> null
        },
        provides = source.test,
        consumes = source.stub
    )

    @JsonProperty("git")
    @Suppress("unused")
    fun getGitSource(): GitContractSource? {
        return contractSource as? GitContractSource
    }

    @JsonProperty("filesystem")
    @Suppress("unused")
    fun getFilesystemSource(): FileSystemContractSource? {
        return contractSource as? FileSystemContractSource
    }

    fun transform(): Source {
        return this.contractSource?.transform(provides, consumes) ?: Source(test = provides, stub = consumes)
    }

    fun interface ContractSource {
        fun transform(provides: List<String>?, consumes: List<Consumes>?): Source
    }

    data class GitContractSource(
        val url: String? = null,
        val branch: String? = null
    ) : ContractSource {
        constructor(source: Source) : this(source.repository, source.branch)

        override fun transform(provides: List<String>?, consumes: List<Consumes>?): Source {
            return Source(
                provider = SourceProvider.git,
                repository = this.url,
                branch = this.branch,
                test = provides,
                stub = consumes.orEmpty()
            )
        }
    }

    data class FileSystemContractSource(
        val directory: String = "."
    ) : ContractSource {
        constructor(source: Source) : this(source.directory ?: ".")

        override fun transform(provides: List<String>?, consumes: List<Consumes>?): Source {
            return Source(
                provider = SourceProvider.filesystem,
                directory = this.directory,
                test = provides,
                stub = consumes.orEmpty()
            )
        }
    }
}