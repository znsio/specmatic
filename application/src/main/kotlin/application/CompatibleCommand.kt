package application

import `in`.specmatic.conversions.OpenApiSpecification
import `in`.specmatic.conversions.wsdlContentToFeature
import `in`.specmatic.core.*
import `in`.specmatic.core.git.*
import `in`.specmatic.core.log.CompositePrinter
import `in`.specmatic.core.log.Verbose
import `in`.specmatic.core.log.logger
import `in`.specmatic.core.pattern.ContractException
import `in`.specmatic.core.utilities.exceptionCauseMessage
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import picocli.CommandLine
import picocli.CommandLine.*
import java.io.File
import java.io.FileNotFoundException
import java.util.concurrent.Callable

@Configuration
open class SystemObjects {
    @Bean
    open fun getSystemGit(): GitCommand {
        return SystemGit()
    }
}

@Command(name = "git",
        mixinStandardHelpOptions = true,
        description = ["Checks backward compatibility of a contract in a git repository"])
class GitCompatibleCommand : Callable<Int> {
    @Autowired
    lateinit var gitCommand: GitCommand

    @Autowired
    lateinit var fileOperations: FileOperations

    @Command(name = "file", description = ["Compare file in working tree against HEAD"])
    fun file(@Parameters(paramLabel = "contractPath", defaultValue = ".") contractPath: String,
             @Option(names = ["--debug"], required = false, defaultValue = "false") verbose: Boolean): Int {
        if(verbose)
            logger = Verbose(CompositePrinter())

        if(!contractPath.isContractFile() && !contractPath.endsWith(".yaml") && !File(contractPath).isDirectory) {
            logger.log(invalidContractExtensionMessage(contractPath))
            return 1
        }

        return try {
            backwardCompatibleOnFileOrDirectory(contractPath, fileOperations) {
                backwardCompatibleFile(it, fileOperations, gitCommand)
            }
        } catch(e: Throwable) {
            logger.log(e)
            1
        }
    }

    @Command(name = "commits", description = ["Compare file in newer commit against older commit"])
    fun commits(@Parameters(paramLabel = "contractPath", defaultValue = ".") path: String,
                @Parameters(paramLabel = "newerCommit") newerCommit: String,
                @Parameters(paramLabel = "olderCommit") olderCommit: String,
                @Option(names = ["--debug"], required = false, defaultValue = "false") verbose: Boolean): Int {
        if(verbose)
            logger = Verbose()

        return try {
            backwardCompatibleOnFileOrDirectory(path, fileOperations) {
                backwardCompatibleCommit(it, newerCommit, olderCommit, gitCommand)
            }
        } catch(e: Throwable) {
            logger.log(e)
            1
        }
    }

    override fun call(): Int {
        CommandLine(GitCompatibleCommand()).usage(System.out)
        return 0
    }
}

@Command(name = "compatible",
        mixinStandardHelpOptions = true,
        description = ["Checks if the newer contract is backward compatible with the older one"],
        subcommands = [ GitCompatibleCommand::class ])
internal class CompatibleCommand : Callable<Unit> {
    override fun call() {
        CommandLine(CompatibleCommand()).usage(System.out)
    }
}

private fun backwardCompatibleOnFileOrDirectory(
    path: String,
    fileOperations: FileOperations,
    fn: (String) -> Outcome<Results>
): Int {
    return when {
        fileOperations.isFile(path) -> {
            val output = checkCompatibility {
                fn(path)
            }

            println(output.message)
            output.exitCode
        }
        fileOperations.isDirectory(path) -> {
            val file = File(path)
            val outputs = file.walkTopDown().filter {
                it.extension in CONTRACT_EXTENSIONS
            }.map {
                Pair(it.path, checkCompatibility {
                    fn(it.path)
                })
            }.toList()

            if(outputs.isEmpty()) {
                logger.log("No contract files were found")
                0
            } else {
                logger.log(outputs.joinToString("${System.lineSeparator()}${System.lineSeparator()}") { (path, output) ->
                    """$path:${System.lineSeparator()}${output.message.prependIndent("  ")}"""
                })

                outputs.map { (_, output) -> output.exitCode }.find { it != 0 } ?: 0
            }
        }
        else -> {
            throw ContractException("$path was of an unexpected file type.")
        }
    }
}

internal fun compatibilityReport(results: Results, resultMessage: String): String {
    val countsMessage = "Tests run: ${results.successCount + results.failureCount}, Passed: ${results.successCount}, Failed: ${results.failureCount}\n\n"
    val resultReport = results.report(PATH_NOT_RECOGNIZED_ERROR).trim().let {
        when {
            it.isNotEmpty() -> "$it\n\n"
            else -> it
        }
    }

    return "$countsMessage$resultReport$resultMessage".trim()
}

internal fun backwardCompatibleFile(
    contractPath: String,
    fileOperations: FileOperations,
    git: GitCommand
): Outcome<Results> {
    return try {
        logger.debug("Newer version of $contractPath")

        val newerFeature = parseContract(logger.debug(fileOperations.read(contractPath)), contractPath)
        val result = getOlderFeature(contractPath, git)

        result.onSuccess {
            Outcome(testBackwardCompatibility(it, newerFeature))
        }
    } catch(e: NonZeroExitError) {
        Outcome(Results(mutableListOf(Result.Success())), "Could not find $contractPath at HEAD")
    } catch(e: FileNotFoundException) {
        Outcome(Results(mutableListOf(Result.Success())), "Could not find $contractPath on the file system")
    }
}

internal fun backwardCompatibleCommit(
    contractPath: String,
    newerCommit: String,
    olderCommit: String,
    git: GitCommand,
): Outcome<Results> {
    val (gitRoot, relativeContractPath) = git.relativeGitPath(contractPath)

    val partial = getFileContentAtSpecifiedCommit(gitRoot)(relativeContractPath)(contractPath)

    return partial(newerCommit).onSuccess { newerGherkin ->
        val olderCommitOutcome = partial(olderCommit)

        when(olderCommitOutcome.result) {
            null -> Outcome(Results())
            else -> olderCommitOutcome.onSuccess { olderGherkin ->
                Outcome(testBackwardCompatibility(parseContract(olderGherkin, contractPath), parseContract(newerGherkin, contractPath)))
            }
        }
    }
}

internal fun parseContract(content: String, path: String): Feature {
    return when(val extension = File(path).extension) {
        "yaml" -> OpenApiSpecification.fromYAML(content, path).toFeature()
        "wsdl" -> wsdlContentToFeature(content, path)
        in CONTRACT_EXTENSIONS -> parseGherkinStringToFeature(content, path)
        else -> throw ContractException("Current file extension is $extension, but supported extensions are ${CONTRACT_EXTENSIONS.joinToString(", ")}")
    }
}

internal fun getOlderFeature(contractPath: String, git: GitCommand): Outcome<Feature> {
    if(!git.fileIsInGitDir(contractPath))
        return Outcome(null, "Older contract file must be provided, or the file must be in a git directory")

    val(contractGit, relativeContractPath) = git.relativeGitPath(contractPath)
    logger.debug("Older version of $contractPath")
    return Outcome(parseContract(logger.debug(contractGit.show("HEAD", relativeContractPath)), contractPath))
}

internal data class CompatibilityOutput(val exitCode: Int, val message: String)

internal fun compatibilityMessage(results: Outcome<Results>): CompatibilityOutput {
    return when {
        results.result == null -> CompatibilityOutput(1, results.errorMessage)
        results.result.success() -> CompatibilityOutput(0, results.errorMessage.ifEmpty { "The newer contract is backward compatible" })
        else -> CompatibilityOutput(1, compatibilityReport(results.result, "The newer contract is NOT backward compatible"))
    }
}

internal fun checkCompatibility(compatibilityCheck: () -> Outcome<Results>): CompatibilityOutput =
    try {
        val results = compatibilityCheck()
        compatibilityMessage(results)
    } catch(e: Throwable) {
        CompatibilityOutput(1, "Could not run backwad compatibility check, got exception\n${exceptionCauseMessage(e)}")
    }
