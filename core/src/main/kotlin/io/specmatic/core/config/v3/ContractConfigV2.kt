package io.specmatic.core.config.v3

import com.fasterxml.jackson.annotation.JsonIgnore
import com.fasterxml.jackson.annotation.JsonProperty
import com.fasterxml.jackson.databind.annotation.JsonDeserialize
import io.specmatic.core.Source
import io.specmatic.core.SourceProvider

data class ContractConfigV2(
    @JsonIgnore
    val contractSource: ContractSourceV2? = null,
    val provides: List<String>? = null,
    val consumes: List<Consumes>? = null
) {
    constructor(
        @JsonProperty("git") git: GitContractSourceV2? = null,
        @JsonProperty("filesystem") filesystem: FileSystemContractSourceV2? = null,
        provides: List<String>? = null,
        @JsonDeserialize(using = ConsumesDeserializer::class)
        consumes: List<Consumes>? = null
    ) : this(
        contractSource = git ?: filesystem,
        provides = provides,
        consumes = consumes
    )

    constructor(source: Source) : this(
        contractSource = when {
            source.provider == SourceProvider.git -> GitContractSourceV2(source)
            source.directory != null -> FileSystemContractSourceV2(source)
            else -> null
        },
        provides = source.test,
        consumes = source.stub
    )

    @JsonProperty("git")
    fun getGitSource(): GitContractSourceV2? {
        return contractSource as? GitContractSourceV2
    }

    @JsonProperty("filesystem")
    fun getFilesystemSource(): FileSystemContractSourceV2? {
        return contractSource as? FileSystemContractSourceV2
    }

    fun transform(): Source {
        return this.contractSource?.transform(provides, consumes) ?: Source(test = provides, stub = consumes)
    }

    fun interface ContractSourceV2 {
        fun transform(provides: List<String>?, consumes: List<Consumes>?): Source
    }

    data class GitContractSourceV2(
        val url: String? = null,
        val branch: String? = null
    ) : ContractSourceV2 {
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

    data class FileSystemContractSourceV2(
        val directory: String = "."
    ) : ContractSourceV2 {
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