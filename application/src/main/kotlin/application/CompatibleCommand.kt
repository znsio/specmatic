package application

import `in`.specmatic.conversions.OpenApiSpecification
import `in`.specmatic.conversions.wsdlContentToFeature
import `in`.specmatic.core.*
import `in`.specmatic.core.git.GitCommand
import `in`.specmatic.core.git.NonZeroExitError
import `in`.specmatic.core.git.SystemGit
import `in`.specmatic.core.log.CompositePrinter
import `in`.specmatic.core.log.Verbose
import `in`.specmatic.core.log.logger
import `in`.specmatic.core.pattern.ContractException
import `in`.specmatic.core.utilities.exceptionCauseMessage
import `in`.specmatic.stub.hasOpenApiFileExtension
import `in`.specmatic.test.ResultAssert
import org.junit.jupiter.api.DynamicTest
import org.junit.jupiter.api.TestFactory
import org.junit.platform.engine.discovery.DiscoverySelectors
import org.junit.platform.launcher.Launcher
import org.junit.platform.launcher.LauncherDiscoveryRequest
import org.junit.platform.launcher.core.LauncherDiscoveryRequestBuilder
import org.junit.platform.reporting.legacy.xml.LegacyXmlReportGeneratingListener
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import picocli.CommandLine
import picocli.CommandLine.*
import java.io.File
import java.io.FileNotFoundException
import java.io.PrintWriter
import java.nio.file.Paths
import java.util.concurrent.Callable

@Configuration
open class SystemObjects {
    @Bean
    open fun getSystemGit(): GitCommand {
        return SystemGit()
    }
}

open class JUnitBackwardCompatibilityTestRunner {
    companion object {
        fun outcome(): Outcome<Results> {
            return Outcome(results.fold(Results()) { acc, results -> acc.plus(results) }.distinct())
        }

        var tests: List<BackwardCompatibilityTest> = emptyList()
        var results: MutableList<Results> = mutableListOf()
    }

    @TestFactory
    fun backwardCompatibilityTest(): Collection<DynamicTest> {
        return tests.map { test ->
            DynamicTest.dynamicTest(test.name) {
                val testResults = Results(test.execute())

                results.add(testResults)
                ResultAssert.assertThat(withoutFluff(testResults).distinct().toResultIfAny()).isSuccess()
            }
        }
    }

    private fun withoutFluff(testResults: Results): Results {
        val resultsFluff1 = testResults.withoutFluff(1)

        return when {
            resultsFluff1.hasResults() -> resultsFluff1
            else -> testResults
        }
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

    @Autowired
    lateinit var junitLauncher: Launcher

    @Command(name = "file", description = ["Compare file in working tree against HEAD"])
    fun file(@Parameters(paramLabel = "contractPath", defaultValue = ".") inputContractPath: String,
             @Option(names = ["--junitReportDir"], required = false, defaultValue = "") junitReportDirName: String,
             @Option(names = ["--debug"], required = false, defaultValue = "false") verbose: Boolean): Int {
        if(verbose)
            logger = Verbose(CompositePrinter())

        if(!inputContractPath.isContractFile() && !hasOpenApiFileExtension(inputContractPath) && !File(inputContractPath).isDirectory) {
            logger.log(invalidContractExtensionMessage(inputContractPath))
            return 1
        }

        return try {
            backwardCompatibleOnFileOrDirectory(inputContractPath, fileOperations) { contractPath ->
                val testGenerationOutcome = generateFileBackwardCompatibilityTests(contractPath, fileOperations, gitCommand)

                testGenerationOutcome.onSuccess { tests ->
                    JUnitBackwardCompatibilityTestRunner.tests = tests

                    val request: LauncherDiscoveryRequest = LauncherDiscoveryRequestBuilder.request()
                        .selectors(DiscoverySelectors.selectClass(JUnitBackwardCompatibilityTestRunner::class.java))
                        .build()

                    junitLauncher.discover(request)

                    if (junitReportDirName.isNotBlank()) {
                        val reportListener = LegacyXmlReportGeneratingListener(
                            Paths.get(junitReportDirName),
                            PrintWriter(System.out, true)
                        )
                        junitLauncher.registerTestExecutionListeners(reportListener)
                    }

                    junitLauncher.execute(request)

                    JUnitBackwardCompatibilityTestRunner.outcome()
                }
            }
        } catch (e: Throwable) {
            logger.log(e)
            1
        }
    }

    @Command(name = "commits", description = ["Compare file in newer commit against older commit"])
    fun commits(@Parameters(paramLabel = "contractPath", defaultValue = ".") path: String,
                @Parameters(paramLabel = "newerCommit") newerCommit: String,
                @Parameters(paramLabel = "olderCommit") olderCommit: String,
                @Option(names = ["--junitReportDir"], required = false, defaultValue = "") junitReportDirName: String,
                @Option(names = ["--debug"], required = false, defaultValue = "false") verbose: Boolean): Int {
        if(verbose)
            logger = Verbose()

        return try {
            backwardCompatibleOnFileOrDirectory(path, fileOperations) { contractPath ->
                val testGenerationOutcome =
                    generateCommitBackwardCompatibleTests(contractPath, newerCommit, olderCommit, gitCommand)

                testGenerationOutcome.onSuccess { tests ->
                    JUnitBackwardCompatibilityTestRunner.tests = tests

                    val request: LauncherDiscoveryRequest = LauncherDiscoveryRequestBuilder.request()
                        .selectors(DiscoverySelectors.selectClass(JUnitBackwardCompatibilityTestRunner::class.java))
                        .build()

                    junitLauncher.discover(request)

                    if (junitReportDirName.isNotBlank()) {
                        val reportListener = LegacyXmlReportGeneratingListener(
                            Paths.get(junitReportDirName),
                            PrintWriter(System.out, true)
                        )
                        junitLauncher.registerTestExecutionListeners(reportListener)
                    }

                    junitLauncher.execute(request)

                    JUnitBackwardCompatibilityTestRunner.outcome()
                }
            }
        } catch (e: Throwable) {
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

interface BackwardCompatibilityScope {
    fun executeCheck(): Int
}

class FileBackwardCompatibilityScope(val path: String, val fn: (String) -> Outcome<Results>): BackwardCompatibilityScope {
    override fun executeCheck(): Int {
        val outcome: Outcome<Results> = fn(path)

        val output = checkCompatibility(outcome)

        println(output.message)

        return output.exitCode
    }
}

class DirectoryBackwardCompatibilityScope(val path: String, val fn: (String) -> Outcome<Results>): BackwardCompatibilityScope {
    override fun executeCheck(): Int {
        val file = File(path)
        val outputs = file.walkTopDown().filter {
            it.extension in CONTRACT_EXTENSIONS
        }.map {
            val results = fn(it.path)
            Triple(it.path, checkCompatibility(results), results)
        }.toList()

        return if (outputs.isEmpty()) {
            logger.log("No contract files were found")
            0
        } else {
            logger.log(outputs.joinToString("${System.lineSeparator()}${System.lineSeparator()}") { (path, output) ->
                """$path:${System.lineSeparator()}${output.message.prependIndent("  ")}"""
            })

            outputs.map { (_, output) -> output.exitCode }.find { it != 0 } ?: 0
        }
    }

}

private fun backwardCompatibleOnFileOrDirectory(
    path: String,
    fileOperations: FileOperations,
    fn: (String) -> Outcome<Results>
): Int {
    val scope = when {
        fileOperations.isFile(path) -> FileBackwardCompatibilityScope(path, fn)
        fileOperations.isDirectory(path) -> DirectoryBackwardCompatibilityScope(path, fn)
        else -> throw ContractException("$path was of an unexpected file type.")
    }

    return scope.executeCheck()
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

internal fun generateFileBackwardCompatibilityTests(
    contractPath: String,
    fileOperations: FileOperations,
    git: GitCommand): Outcome<List<BackwardCompatibilityTest>> {

    return try {
        logger.debug("Newer version of $contractPath")

        val newerFeature = parseContract(logger.debug(fileOperations.read(contractPath)), contractPath)
        val result = getOlderFeature(contractPath, git)

        result.onSuccess { olderFeature ->
            Outcome(generateBackwardCompatibilityTests(olderFeature, newerFeature))
        }
    } catch(e: NonZeroExitError) {
        Outcome(emptyList(), "Could not find $contractPath at HEAD")
    } catch(e: FileNotFoundException) {
        Outcome(emptyList(), "Could not find $contractPath on the file system")
    }
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

internal fun generateCommitBackwardCompatibleTests(
    contractPath: String,
    newerCommit: String,
    olderCommit: String,
    git: GitCommand,
): Outcome<List<BackwardCompatibilityTest>> {
    val (gitRoot, relativeContractPath) = git.relativeGitPath(contractPath)

    val partial = getFileContentAtSpecifiedCommit(gitRoot)(relativeContractPath)(contractPath)

    return partial(newerCommit).onSuccess { newerGherkin ->
        val olderCommitOutcome = partial(olderCommit)

        when(olderCommitOutcome.result) {
            null -> Outcome(emptyList())
            else -> olderCommitOutcome.onSuccess { olderGherkin ->
                Outcome(generateBackwardCompatibilityTests(parseContract(olderGherkin, contractPath), parseContract(newerGherkin, contractPath)))
            }
        }
    }
}

internal fun parseContract(content: String, path: String): Feature {
    return when(val extension = File(path).extension) {
        in OPENAPI_FILE_EXTENSIONS -> OpenApiSpecification.fromYAML(content, path).toFeature()
        WSDL -> wsdlContentToFeature(content, path)
        in CONTRACT_EXTENSIONS -> parseGherkinStringToFeature(content, path)
        else -> throw unsupportedFileExtensionContractException(path, extension)
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
        results.result.hasFailures() -> CompatibilityOutput(1, compatibilityReport(results.result, "The newer contract is NOT backward compatible"))
        else -> CompatibilityOutput(0, results.errorMessage.ifEmpty { "The newer contract is backward compatible" })
    }
}

internal fun checkCompatibility(results: Outcome<Results>): CompatibilityOutput =
    try {
        compatibilityMessage(results)
    } catch(e: Throwable) {
        CompatibilityOutput(1, "Could not run backward compatibility check, got exception\n${exceptionCauseMessage(e)}")
    }
