package application

import application.BackwardCompatibilityCheckCommand.CompatibilityResult.*
import io.specmatic.conversions.OpenApiSpecification
import io.specmatic.core.CONTRACT_EXTENSIONS
import io.specmatic.core.Feature
import io.specmatic.core.git.GitCommand
import io.specmatic.core.git.SystemGit
import io.specmatic.core.testBackwardCompatibility
import io.specmatic.core.utilities.exitWithMessage
import org.springframework.stereotype.Component
import picocli.CommandLine.Command
import java.io.File
import java.util.concurrent.Callable
import java.util.regex.Pattern
import kotlin.system.exitProcess

@Component
@Command(
    name = "backwardCompatibilityCheck",
    mixinStandardHelpOptions = true,
    description = ["Checks backward compatibility of a directory across the current HEAD and the main branch"]
)
class BackwardCompatibilityCheckCommand(
    private val gitCommand: GitCommand = SystemGit(),
) : Callable<Unit> {

    private val newLine = System.lineSeparator()

    companion object {
        private const val HEAD = "HEAD"
        private const val MARGIN_SPACE = "  "
    }

    override fun call() {
        val filesChangedInCurrentBranch: Set<String> = getOpenAPISpecFilesChangedInCurrentBranch()
        if (filesChangedInCurrentBranch.isEmpty()) exitWithMessage("${newLine}No OpenAPI spec files were changed, skipping the check.$newLine")

        val filesReferringToChangedSchemaFiles = filesReferringToChangedSchemaFiles(filesChangedInCurrentBranch)

        val specificationsToCheck: Set<String> = filesChangedInCurrentBranch + filesReferringToChangedSchemaFiles


        logFilesToBeCheckedForBackwardCompatibility(
            filesChangedInCurrentBranch,
            filesReferringToChangedSchemaFiles
        )

        val result = runBackwardCompatibilityCheckFor(specificationsToCheck)

        println()
        println(result.report)
        exitProcess(result.exitCode)
    }

    private fun runBackwardCompatibilityCheckFor(files: Set<String>): CompatibilityReport {
        val branchWithChanges = gitCommand.currentBranch()
        val treeishWithChanges = if (branchWithChanges == HEAD) gitCommand.detachedHEAD() else branchWithChanges

        try {
            val results = files.mapIndexed { index, specFilePath ->
                try {
                    println("${index.inc()}. Running the check for $specFilePath:")

                    // newer => the file with changes on the branch
                    val newer = OpenApiSpecification.fromFile(specFilePath).toFeature()

                    val olderFile = gitCommand.getFileInTheDefaultBranch(specFilePath, treeishWithChanges)
                    if (olderFile == null) {
                        println("$specFilePath is a new file.$newLine")
                        return@mapIndexed PASSED
                    }

                    gitCommand.checkout(gitCommand.defaultBranch())

                    // older => the same file on the default (e.g. main) branch
                    val older = OpenApiSpecification.fromFile(olderFile.path).toFeature()

                    val backwardCompatibilityResult = testBackwardCompatibility(older, newer)

                    if (backwardCompatibilityResult.success()) {
                        println(
                            "$newLine The file $specFilePath is backward compatible.$newLine".prependIndent(
                                MARGIN_SPACE
                            )
                        )

                        if(!examplesAreValid(newer, "newer")) {
                            println(
                                "$newLine *** Examples in $specFilePath are not valid. ***$newLine".prependIndent(
                                    MARGIN_SPACE
                                )
                            )

                            FAILED
                        }
                        else
                            PASSED
                    } else {
                        println("$newLine ${backwardCompatibilityResult.report().prependIndent(MARGIN_SPACE)}")
                        println(
                            "$newLine *** The file $specFilePath is NOT backward compatible. ***$newLine".prependIndent(
                                MARGIN_SPACE
                            )
                        )
                        FAILED
                    }
                } finally {
                    gitCommand.checkout(treeishWithChanges)
                }
            }

            return CompatibilityReport(results)
        } finally {
            gitCommand.checkout(treeishWithChanges)
        }
    }

    private fun examplesAreValid(feature: Feature, which: String): Boolean {
        return try {
            feature.validateExamplesOrException()
            true
        } catch (t: Throwable) {
            println()
            false
        }
    }

    private fun logFilesToBeCheckedForBackwardCompatibility(
        changedFiles: Set<String>,
        filesReferringToChangedFiles: Set<String>
    ) {
        val INDENT = "  "

        println("Checking backward compatibility of the following files: $newLine")
        println("${INDENT}Files that have changed:")
        changedFiles.forEach { println(it.prependIndent("${INDENT}${INDENT}")) }
        println()

        if(filesReferringToChangedFiles.isNotEmpty()) {
            println("${INDENT}Files referring to the changed files - ")
            filesReferringToChangedFiles.forEach { println(it.prependIndent("${INDENT}${INDENT}")) }
            println()
        }

        println("-".repeat(20))
        println()
    }

    internal fun filesReferringToChangedSchemaFiles(inputFiles: Set<String>): Set<String> {
        if (inputFiles.isEmpty()) return emptySet()

        val inputFileNames = inputFiles.map { File(it).name }
        val result = allOpenApiSpecFiles().filter {
            it.readText().trim().let { specContent ->
                inputFileNames.any { inputFileName ->
                    val pattern = Pattern.compile("\\b$inputFileName\\b")
                    val matcher = pattern.matcher(specContent)
                    matcher.find()
                }
            }
        }.map { it.path }.toSet()

        return result.flatMap {
            filesReferringToChangedSchemaFiles(setOf(it)).ifEmpty { setOf(it) }
        }.toSet()
    }

    internal fun allOpenApiSpecFiles(): List<File> {
        return File(".").walk().toList().filterNot {
            ".git" in it.path
        }.filter { it.isFile && it.isOpenApiSpec() }
    }

    private fun getOpenAPISpecFilesChangedInCurrentBranch(): Set<String> {
        return gitCommand.getFilesChangeInCurrentBranch().filter {
            File(it).exists() && File(it).isOpenApiSpec()
        }.toSet()
    }

    private fun File.isOpenApiSpec(): Boolean {
        if (this.extension !in CONTRACT_EXTENSIONS) return false
        return OpenApiSpecification.isParsable(this.path)
    }

    class CompatibilityReport(results: List<CompatibilityResult>) {
        val report: String
        val exitCode: Int

        init {
            val failed: Boolean = results.any { it == FAILED }
            val failedCount = results.count { it == FAILED }
            val passedCount = results.count { it == PASSED }

            report = "Files checked: ${results.size} (Passed: ${passedCount}, Failed: $failedCount)"
            exitCode = if(failed) 1 else 0
        }

    }

    enum class CompatibilityResult {
        PASSED, FAILED
    }
}
