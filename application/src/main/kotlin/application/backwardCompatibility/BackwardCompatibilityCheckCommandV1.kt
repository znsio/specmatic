package application.backwardCompatibility

import io.specmatic.conversions.OpenApiSpecification
import io.specmatic.core.*
import io.specmatic.core.git.GitCommand
import io.specmatic.core.git.SystemGit
import io.specmatic.core.log.logger
import io.specmatic.core.utilities.exitWithMessage
import io.specmatic.stub.isOpenAPI
import picocli.CommandLine.Option
import java.io.File
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.Paths
import java.util.concurrent.Callable
import java.util.regex.Pattern
import kotlin.io.path.extension
import kotlin.io.path.pathString
import kotlin.system.exitProcess

//@Component
//@Command(
//    name = "backwardCompatibilityCheck",
//    aliases = ["backward-compatibility-check"],
//    mixinStandardHelpOptions = true,
//    description = ["Checks backward compatibility of a directory across the current HEAD and the base branch"]
//)
// TODO - knock this off.
class BackwardCompatibilityCheckCommandV1() : Callable<Unit> {
    private val gitCommand: GitCommand = SystemGit()
    private val newLine = System.lineSeparator()

    @Option(names = ["--base-branch"], description = ["Base branch to compare the changes against"], required = false)
    var baseBranch: String = gitCommand.currentRemoteBranch()

    companion object {
        private const val HEAD = "HEAD"
        private const val MARGIN_SPACE = "  "
        private const val ONE_INDENT = "  "
        private const val TWO_INDENTS = "${ONE_INDENT}${ONE_INDENT}"
    }

    override fun call() {
        val filesChangedInCurrentBranch = getChangedSpecsInCurrentBranch()
        val filesReferringToChangedSchemaFiles = filesReferringToChangedSchemaFiles(filesChangedInCurrentBranch)
        val specificationsOfChangedExternalisedExamples =
            getSpecificationsOfChangedExternalisedExamples(filesChangedInCurrentBranch)

        logFilesToBeCheckedForBackwardCompatibility(
            filesChangedInCurrentBranch,
            filesReferringToChangedSchemaFiles,
            specificationsOfChangedExternalisedExamples
        )

        val specificationsToCheck: Set<String> =
            filesChangedInCurrentBranch +
                    filesReferringToChangedSchemaFiles +
                    specificationsOfChangedExternalisedExamples

        val result = try {
            runBackwardCompatibilityCheckFor(
                files = specificationsToCheck,
                baseBranch = baseBranch
            )
        } catch(e: Throwable) {
            logger.newLine()
            logger.newLine()
            logger.log(e)
            exitProcess(1)
        }

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

    fun runBackwardCompatibilityCheckFor(files: Set<String>, baseBranch: String): CompatibilityReport {
        val branchWithChanges = gitCommand.currentBranch()
        val treeishWithChanges = if (branchWithChanges == HEAD) gitCommand.detachedHEAD() else branchWithChanges

        try {
            val results = files.mapIndexed { index, specFilePath ->
                try {
                    println("${index.inc()}. Running the check for $specFilePath:")

                    // newer => the file with changes on the branch
                    val newer = getFeatureFromSpecPath(specFilePath)
                    val unusedExamples = getUnusedExamples(newer)

                    val olderFile = gitCommand.getFileInTheBaseBranch(
                        specFilePath,
                        treeishWithChanges,
                        baseBranch
                    )
                    if (olderFile == null) {
                        println("$specFilePath is a new file.$newLine")
                        return@mapIndexed CompatibilityResult.PASSED
                    }

                    gitCommand.stash()
                    gitCommand.checkout(baseBranch)
                    // older => the same file on the default (e.g. main) branch
                    val older = getFeatureFromSpecPath(olderFile.path)
                    gitCommand.stashPop()

                    val backwardCompatibilityResult = checkBackwardCompatibility(older, newer)

                    if (backwardCompatibilityResult.success()) {
                        println(
                            "$newLine The file $specFilePath is backward compatible.$newLine".prependIndent(
                                MARGIN_SPACE
                            )
                        )
                        println()
                        var errorsFound = false

                        if(!areExamplesValid(newer, "newer")) {
                            println(
                                "$newLine *** Examples in $specFilePath are not valid. ***$newLine".prependIndent(
                                    MARGIN_SPACE
                                )
                            )
                            println()
                            errorsFound = true
                        }

                        if(unusedExamples.isNotEmpty()) {
                            println(
                                "$newLine *** Some examples for $specFilePath could not be loaded. ***$newLine".prependIndent(
                                    MARGIN_SPACE
                                )
                            )
                            println()
                            errorsFound = true
                        }

                        if(errorsFound) CompatibilityResult.FAILED
                        else CompatibilityResult.PASSED
                    } else {
                        println("$newLine ${backwardCompatibilityResult.report().prependIndent(MARGIN_SPACE)}")
                        println(
                            "$newLine *** The file $specFilePath is NOT backward compatible. ***$newLine".prependIndent(
                                MARGIN_SPACE
                            )
                        )
                        println()
                        CompatibilityResult.FAILED
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

    fun logFilesToBeCheckedForBackwardCompatibility(
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
        }.filter { it.isFile && it.isValidSpec() }
    }

    private fun getChangedSpecsInCurrentBranch(): Set<String> {
        return gitCommand.getFilesChangedInCurrentBranch(baseBranch).filter {
            File(it).exists() && File(it).isValidSpec()
        }.toSet().also {
            if(it.isEmpty()) exitWithMessage("${newLine}No specs were changed, skipping the check.$newLine")
        }
    }

    private fun File.isValidSpec(): Boolean {
        if (this.extension !in CONTRACT_EXTENSIONS) return false
        return OpenApiSpecification.isParsable(this.path)
    }

    private fun areExamplesValid(feature: Feature, which: String): Boolean {
        return try {
            feature.validateExamplesOrException()
            true
        } catch (t: Throwable) {
            println()
            false
        }
    }

    private fun getUnusedExamples(feature: Feature): Set<String> {
        return feature.loadExternalisedExamplesAndListUnloadableExamples().second
    }

    private fun getFeatureFromSpecPath(path: String): Feature {
        return OpenApiSpecification.fromFile(path).toFeature()
    }

    private fun checkBackwardCompatibility(oldFeature: Feature, newFeature: Feature): Results {
        return testBackwardCompatibility(oldFeature, newFeature)
    }
}
