package application

import application.BackwardCompatibilityCheckCommand.CompatibilityResult.*
import io.specmatic.conversions.OpenApiSpecification
import io.specmatic.core.*
import io.specmatic.core.git.GitCommand
import io.specmatic.core.git.SystemGit
import io.specmatic.core.utilities.exitWithMessage
import io.specmatic.stub.isOpenAPI
import org.springframework.stereotype.Component
import picocli.CommandLine.Command
import java.io.File
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.Paths
import java.util.concurrent.Callable
import java.util.regex.Pattern
import kotlin.io.path.extension
import kotlin.io.path.pathString
import kotlin.system.exitProcess

const val ONE_INDENT = "  "
const val TWO_INDENTS = "${ONE_INDENT}${ONE_INDENT}"

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

        val specificationsOfChangedExternalisedExamples: Set<String> = getSpecificationsOfChangedExternalisedExamples(filesChangedInCurrentBranch)

        logFilesToBeCheckedForBackwardCompatibility(
            filesChangedInCurrentBranch,
            filesReferringToChangedSchemaFiles,
            specificationsOfChangedExternalisedExamples
        )

        val specificationsToCheck: Set<String> = filesChangedInCurrentBranch + filesReferringToChangedSchemaFiles + specificationsOfChangedExternalisedExamples

        val result = runBackwardCompatibilityCheckFor(specificationsToCheck)

        println()
        println(result.report)
        exitProcess(result.exitCode)
    }

    private fun getSpecificationsOfChangedExternalisedExamples(filesChangedInCurrentBranch: Set<String>): Set<String> {
        data class CollectedFiles(
            val specifications: MutableSet<String> = mutableSetOf(),
            val examplesMissingSpecifications: MutableList<String> = mutableListOf(),
            val ignoredFiles: MutableList<String> = mutableListOf()
        )

        val collectedFiles = filesChangedInCurrentBranch.fold(CollectedFiles()) { acc, filePath ->
            val path = Paths.get(filePath)
            val examplesDir = path.find { it.toString().endsWith("_examples") || it.toString().endsWith("_tests") }

            if (examplesDir == null) {
                acc.ignoredFiles.add(filePath)
            } else {
                val parentPath = examplesDir.parent
                val strippedPath = parentPath.resolve(examplesDir.fileName.toString().removeSuffix("_examples"))
                val specFiles = findSpecFiles(strippedPath)

                if (specFiles.isNotEmpty()) {
                    acc.specifications.addAll(specFiles.map { it.toString() })
                } else {
                    acc.examplesMissingSpecifications.add(filePath)
                }
            }
            acc
        }

        val result = collectedFiles.specifications.toMutableSet()

        collectedFiles.examplesMissingSpecifications.forEach { filePath ->
            val path = Paths.get(filePath)
            val examplesDir = path.find { it.toString().endsWith("_examples") || it.toString().endsWith("_tests") }
            if (examplesDir != null) {
                val parentPath = examplesDir.parent
                val strippedPath = parentPath.resolve(examplesDir.fileName.toString().removeSuffix("_examples"))
                val specFiles = findSpecFiles(strippedPath)
                if (specFiles.isNotEmpty()) {
                    result.addAll(specFiles.map { it.toString() })
                } else {
                    result.add("${strippedPath}.yaml")
                }
            }
        }

        return result
    }

    private fun Path.find(predicate: (Path) -> Boolean): Path? {
        var current: Path? = this
        while (current != null) {
            if (predicate(current)) {
                return current
            }
            current = current.parent
        }
        return null
    }

    private fun findSpecFiles(path: Path): List<Path> {
        val extensions = CONTRACT_EXTENSIONS
        return extensions.map { path.resolveSibling(path.fileName.toString() + it) }
            .filter { Files.exists(it) && (isOpenAPI(it.pathString) || it.extension in listOf(WSDL, CONTRACT_EXTENSION)) }
    }

    private fun runBackwardCompatibilityCheckFor(files: Set<String>): CompatibilityReport {
        val branchWithChanges = gitCommand.currentBranch()
        val treeishWithChanges = if (branchWithChanges == HEAD) gitCommand.detachedHEAD() else branchWithChanges

        try {
            val results = files.mapIndexed { index, specFilePath ->
                try {
                    println("${index.inc()}. Running the check for $specFilePath:")

                    // newer => the file with changes on the branch
                    val newer = OpenApiSpecification.fromFile(specFilePath).toFeature().loadExternalisedExamples()

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
        filesReferringToChangedFiles: Set<String>,
        specificationsOfChangedExternalisedExamples: Set<String>
    ) {

        println("Checking backward compatibility of the following files: $newLine")
        println("${ONE_INDENT}Files that have changed:")
        changedFiles.forEach { println(it.prependIndent(TWO_INDENTS)) }
        println()

        if(filesReferringToChangedFiles.isNotEmpty()) {
            println("${ONE_INDENT}Files referring to the changed files - ")
            filesReferringToChangedFiles.forEach { println(it.prependIndent(TWO_INDENTS)) }
            println()
        }

        if(specificationsOfChangedExternalisedExamples.isNotEmpty()) {
            println("${ONE_INDENT}Specifications whose externalised examples were changed - ")
            filesReferringToChangedFiles.forEach { println(it.prependIndent(TWO_INDENTS)) }
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
