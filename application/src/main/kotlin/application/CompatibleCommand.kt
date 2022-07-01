package application

import application.test.ContractExecutionListener
import `in`.specmatic.conversions.OpenApiSpecification
import `in`.specmatic.conversions.wsdlContentToFeature
import `in`.specmatic.core.*
import `in`.specmatic.core.git.*
import `in`.specmatic.core.log.CompositePrinter
import `in`.specmatic.core.log.Verbose
import `in`.specmatic.core.log.logger
import `in`.specmatic.core.pattern.ContractException
import `in`.specmatic.core.utilities.exceptionCauseMessage
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
        var tests: List<BackwardCompatibilityTest> = emptyList()
        var results: MutableList<Results> = mutableListOf()
    }

    @TestFactory
    fun contractAsTest(): Collection<DynamicTest> {
        return tests.map { test ->
            DynamicTest.dynamicTest(test.name) {
                val testResults = Results(test.execute())

                results.add(testResults)
                ResultAssert.assertThat(testResults.toResultIfAny()).isSuccess()
            }
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
    fun file(@Parameters(paramLabel = "contractPath", defaultValue = ".") contractPath: String,
             @Option(names = ["--junitReportDir"], required = false, defaultValue = "") junitReportDirName: String,
             @Option(names = ["--debug"], required = false, defaultValue = "false") verbose: Boolean): Int {
        if(verbose)
            logger = Verbose(CompositePrinter())

        if(!contractPath.isContractFile() && !contractPath.endsWith(".yaml") && !File(contractPath).isDirectory) {
            logger.log(invalidContractExtensionMessage(contractPath))
            return 1
        }

        return try {
            val (exitCode, results) = backwardCompatibleOnFileOrDirectory(contractPath, fileOperations) { path ->
                val testGenerationOutcome = generateFileBackwardCompatibilityTests(path, fileOperations, gitCommand)

                testGenerationOutcome.onSuccess { tests ->
                    JUnitBackwardCompatibilityTestRunner.tests = tests

                    val request: LauncherDiscoveryRequest = LauncherDiscoveryRequestBuilder.request()
                        .selectors(DiscoverySelectors.selectClass(JUnitBackwardCompatibilityTestRunner::class.java))
                        .build()

                    junitLauncher.discover(request)

                    if(junitReportDirName.isNotBlank()) {
                        val reportListener = LegacyXmlReportGeneratingListener(Paths.get(junitReportDirName), PrintWriter(System.out, true))
                        junitLauncher.registerTestExecutionListeners(reportListener)
                    }

                    junitLauncher.execute(request)

                    Outcome(JUnitBackwardCompatibilityTestRunner.results.firstOrNull() ?: Results())
                }
            }

            exitCode
        } catch(e: Throwable) {
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
            val (returnCode, resultsList) = backwardCompatibleOnFileOrDirectory(path, fileOperations) { path ->
                val testGenerationOutcome = generateCommitBackwardCompatibleTests(path, newerCommit, olderCommit, gitCommand)

                testGenerationOutcome.onSuccess { tests ->
                    JUnitBackwardCompatibilityTestRunner.tests = tests

                    val request: LauncherDiscoveryRequest = LauncherDiscoveryRequestBuilder.request()
                        .selectors(DiscoverySelectors.selectClass(JUnitBackwardCompatibilityTestRunner::class.java))
                        .build()

                    junitLauncher.discover(request)

                    if(junitReportDirName.isNotBlank()) {
                        val reportListener = LegacyXmlReportGeneratingListener(Paths.get(junitReportDirName), PrintWriter(System.out, true))
                        junitLauncher.registerTestExecutionListeners(reportListener)
                    }

                    junitLauncher.execute(request)

                    Outcome(JUnitBackwardCompatibilityTestRunner.results.reduce { acc, item -> acc.plus(item) })
                }
            }

//            writeJUnitReport(resultsList, junitReportDirName)

            returnCode
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
): Pair<Int, List<Results>> {
    return when {
        fileOperations.isFile(path) -> {
            val outcome: Outcome<Results> = fn(path)

            val results = outcome.result

            val output = checkCompatibility(outcome)

            println(output.message)

            Pair(output.exitCode, listOfNotNull(results))
        }
        fileOperations.isDirectory(path) -> {
            val file = File(path)
            val outputs = file.walkTopDown().filter {
                it.extension in CONTRACT_EXTENSIONS
            }.map {
                val results = fn(it.path)
                Triple(it.path, checkCompatibility(results), results)
            }.toList()

            if(outputs.isEmpty()) {
                logger.log("No contract files were found")
                Pair(0, emptyList())
            } else {
                logger.log(outputs.joinToString("${System.lineSeparator()}${System.lineSeparator()}") { (path, output) ->
                    """$path:${System.lineSeparator()}${output.message.prependIndent("  ")}"""
                })

                val returnCode: Int = outputs.map { (_, output) -> output.exitCode }.find { it != 0 } ?: 0

                Pair(returnCode, outputs.map { it.third.result }.filterNotNull())
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

internal fun checkCompatibility(results: Outcome<Results>): CompatibilityOutput =
    try {
        compatibilityMessage(results)
    } catch(e: Throwable) {
        CompatibilityOutput(1, "Could not run backwad compatibility check, got exception\n${exceptionCauseMessage(e)}")
    }
