package io.specmatic.core.config.v2

import io.specmatic.core.Source
import io.specmatic.core.SourceProvider

data class ContractConfig(
    val git: GitConfig? = null,
    val filesystem: FileSystemConfig? = null,
    val provides: List<String>? = null,
    val consumes: List<String>? = null
) {
    constructor(source: Source) : this(
        git = when (source.provider) {
            SourceProvider.git -> GitConfig(url = source.repository, branch = source.branch)
            else -> null
        },
        filesystem = when (source.provider) {
            SourceProvider.filesystem -> FileSystemConfig(directory = source.directory)
            else -> null
        },
        provides = source.test,
        consumes = source.stub
    )

    fun transform(): Source {
        return when {
            git != null -> Source(
                provider = SourceProvider.git,
                repository = git.url,
                branch = git.branch,
                test = provides,
                stub = consumes
            )

            else -> Source(
                directory = filesystem?.directory,
                provider = SourceProvider.filesystem,
                test = provides,
                stub = consumes,
            )
        }
    }
}

data class GitConfig(
    val url: String? = null,
    val branch: String? = null
)

data class FileSystemConfig(
    val directory: String? = null
)