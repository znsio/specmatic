package application.backwardCompatibility

import io.specmatic.core.IFeature
import io.specmatic.core.Results
import io.specmatic.core.git.GitCommand
import io.specmatic.core.git.SystemGit
import io.specmatic.core.log.logger
import picocli.CommandLine.Option
import java.io.File
import java.nio.file.Paths
import java.util.concurrent.Callable
import java.util.regex.Pattern
import kotlin.io.path.pathString
import kotlin.system.exitProcess

abstract class BackwardCompatibilityCheckBaseCommand : Callable<Unit> {
    private val gitCommand: GitCommand = SystemGit()
    private val newLine = System.lineSeparator()

    @Option(
        names = ["--base-branch"],
        description = [
            "Base branch to compare the changes against",
            "Default value is the local origin HEAD of the current branch"
        ],
        required = false
    )
    var baseBranch: String? = null

    @Option(names = ["--target-path"], description = ["Specification file or folder to run the check against"], required = false)
    var targetPath: String = ""

    abstract fun checkBackwardCompatibility(oldFeature: IFeature, newFeature: IFeature): Results
    abstract fun File.isValidSpec(): Boolean
    abstract fun getFeatureFromSpecPath(path: String): IFeature

    abstract fun regexForMatchingReferred(schemaFileName: String): String
    abstract fun getSpecsOfChangedExternalisedExamples(
        filesChangedInCurrentBranch: Set<String>
    ): Set<String>

    open fun areExamplesValid(feature: IFeature, which: String): Boolean = true
    open fun getUnusedExamples(feature: IFeature): Set<String> = emptySet()

    final override fun call() {
        val filteredSpecs = getChangedSpecs(logSpecs = true)
        val result = try {
            runBackwardCompatibilityCheckFor(
                files = filteredSpecs,
                baseBranch = baseBranch()
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

    fun getChangedSpecs(logSpecs: Boolean = false): Map<String, String> {
        val filesChangedInCurrentBranch = getChangedSpecsInCurrentBranch().filter {
            it.value.contains(targetPath)
        }
        val filesReferringToChangedSchemaFiles = getSpecsReferringTo(filesChangedInCurrentBranch).associateWith { it }
        val specificationsOfChangedExternalisedExamples =
            getSpecsOfChangedExternalisedExamples(filesChangedInCurrentBranch.values.toSet()).map { it.trim() }.associateWith { it }

        if(logSpecs) {
            logFilesToBeCheckedForBackwardCompatibility(
                filesChangedInCurrentBranch,
                filesReferringToChangedSchemaFiles.keys,
                specificationsOfChangedExternalisedExamples.keys
            )
        }

        return filesChangedInCurrentBranch +
                filesReferringToChangedSchemaFiles +
                specificationsOfChangedExternalisedExamples
    }

    private fun getChangedSpecsInCurrentBranch(): Map<String, String> {
        val changedFiles = gitCommand.getFilesChangedInCurrentBranchAsMap(baseBranch())
        val deletedFiles = changedFiles.filter { (_, changed) -> File(changed).exists().not() }.values
        if(deletedFiles.isNotEmpty()) {
            logger.log("WARNING: Following files were deleted -$newLine ${deletedFiles.joinToString(newLine)}")
        }

        return changedFiles.filter { (_, changed) ->
            File(changed).exists() && File(changed).isValidSpec()
        }.also {
            if(it.isEmpty()) {
                logger.log("${newLine}No existing specs were changed, skipping the check.$newLine")
                exitProcess(0)
            }
        }
    }

    internal fun getSpecsReferringTo(schemaFiles: Map<String, String>): Set<String> {
        if (schemaFiles.isEmpty()) return emptySet()

        val inputFileNames = schemaFiles.map { File(it.value).name }
        val result = allSpecFiles().filter {
            it.readText().trim().let { specContent ->
                inputFileNames.any { inputFileName ->
                    val pattern = Pattern.compile("\\b${regexForMatchingReferred(inputFileName)}\\b")
                    val matcher = pattern.matcher(specContent)
                    matcher.find()
                }
            }
        }.map { it.path }.toSet()

        return result.map { it.trim() }.filter {
            val normalizedPath = Paths.get(it).normalize().pathString
            schemaFiles.containsKey(normalizedPath).not() && schemaFiles.containsValue(normalizedPath).not()
        }.flatMap {
            getSpecsReferringTo(mapOf(it to it)).ifEmpty { setOf(it) }
        }.toSet()
    }

    internal fun allSpecFiles(): List<File> {
        return File(".").walk().toList().filterNot {
            ".git" in it.path
        }.filter { it.isFile && it.isValidSpec() }
    }

    private fun logFilesToBeCheckedForBackwardCompatibility(
        changedFiles: Map<String, String>,
        filesReferringToChangedFiles: Set<String>,
        specificationsOfChangedExternalisedExamples: Set<String>
    ) {

        println("Checking backward compatibility of the following specs: $newLine")
        println("${ONE_INDENT}Specs that have changed:")
        changedFiles.forEach { (original, changed) ->
            if(original == changed) {
                println(changed.prependIndent(TWO_INDENTS))
            } else {
                println("${original.prependIndent(TWO_INDENTS)} -> $changed")
            }
        }
        println()

        if(filesReferringToChangedFiles.isNotEmpty()) {
            println("${ONE_INDENT}Specs referring to the changed specs - ")
            filesReferringToChangedFiles.forEach { println(it.prependIndent(TWO_INDENTS)) }
            println()
        }

        if(specificationsOfChangedExternalisedExamples.isNotEmpty()) {
            println("${ONE_INDENT}Specs whose externalised examples were changed - ")
            filesReferringToChangedFiles.forEach { println(it.prependIndent(TWO_INDENTS)) }
            println()
        }

        println("-".repeat(20))
        println()
    }

    private fun runBackwardCompatibilityCheckFor(files: Map<String, String>, baseBranch: String): CompatibilityReport {
        val branchWithChanges = gitCommand.currentBranch()
        val treeishWithChanges = if (branchWithChanges == HEAD) gitCommand.detachedHEAD() else branchWithChanges

        try {
            val results = files.entries.mapIndexed { index, (originalSpecPath, changedSpecPath) ->
                var areLocalChangesStashed = false
                try {
                    val specPathLog = when (originalSpecPath) {
                        changedSpecPath -> originalSpecPath
                        else -> "$originalSpecPath -> $changedSpecPath"
                    }
                    println("${index.inc()}. Running the check for $specPathLog:")

                    // newer => the file with changes on the branch
                    val newer = getFeatureFromSpecPath(changedSpecPath)
                    val unusedExamples = getUnusedExamples(newer)

                    areLocalChangesStashed = gitCommand.stash()
                    val olderFile = gitCommand.getFileInTheBaseBranch(
                        originalSpecPath,
                        treeishWithChanges,
                        baseBranch
                    )
                    if (olderFile == null) {
                        println("$originalSpecPath is a new file.$newLine")
                        return@mapIndexed CompatibilityResult.PASSED
                    }

                    gitCommand.checkout(baseBranch)
                    // older => the same file on the default (e.g. main) branch
                    val older = getFeatureFromSpecPath(olderFile.path)

                    val backwardCompatibilityResult = checkBackwardCompatibility(older, newer)

                    return@mapIndexed getCompatibilityResult(
                        backwardCompatibilityResult,
                        changedSpecPath,
                        newer,
                        unusedExamples
                    )
                } finally {
                    gitCommand.checkout(treeishWithChanges)
                    if (areLocalChangesStashed) gitCommand.stashPop()
                }
            }

            return CompatibilityReport(results)
        } finally {
            gitCommand.checkout(treeishWithChanges)
        }
    }

    private fun baseBranch() = baseBranch ?: gitCommand.currentRemoteBranch()

    private fun getCompatibilityResult(
        backwardCompatibilityResult: Results,
        specFilePath: String,
        newer: IFeature,
        unusedExamples: Set<String>
    ): CompatibilityResult {
        if (backwardCompatibilityResult.success()) {
            println(
                "$newLine The spec $specFilePath is backward compatible with the corresponding spec from ${baseBranch()}$newLine".prependIndent(
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

            return if(errorsFound) CompatibilityResult.FAILED
            else CompatibilityResult.PASSED
        } else {
            println("$newLine ${backwardCompatibilityResult.report().prependIndent(
                MARGIN_SPACE
            )}")
            println(
                "$newLine *** The changes to the spec $specFilePath are NOT backward compatible with the corresponding spec from ${baseBranch()}***$newLine".prependIndent(
                    MARGIN_SPACE
                )
            )
            println()
            return CompatibilityResult.FAILED
        }
    }

    companion object {
        private const val HEAD = "HEAD"
        private const val MARGIN_SPACE = "  "
        private const val ONE_INDENT = "  "
        private const val TWO_INDENTS = "${ONE_INDENT}${ONE_INDENT}"
    }
}
