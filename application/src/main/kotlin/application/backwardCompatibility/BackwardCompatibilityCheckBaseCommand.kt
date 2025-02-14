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
import kotlin.io.path.absolutePathString
import kotlin.system.exitProcess

abstract class BackwardCompatibilityCheckBaseCommand : Callable<Unit> {
    private lateinit var gitCommand: GitCommand
    private val newLine = System.lineSeparator()
    private var areLocalChangesStashed = false

    @Option(
        names = ["--base-branch"],
        description = [
            "Base branch to compare the changes against",
            "Default value is the local origin HEAD of the current branch"
        ],
        required = false
    )
    var baseBranch: String? = null

    @Option(
        names = ["--target-path"],
        description = ["Specify the file or directory to limit the backward compatibility check scope. If omitted, all changed files will be checked."],
        required = false
    )
    var targetPath: String = ""

    @Option(
        names = ["--repo-dir"],
        description = ["The directory of the repository in which to run the backward compatibility check. If not provided, the check will run in the current working directory."],
        required = false
    )
    var repoDir: String = "."

    abstract fun checkBackwardCompatibility(oldFeature: IFeature, newFeature: IFeature): Results
    abstract fun parseResult(file: File): ParseResult
    abstract fun getFeatureFromSpecPath(path: String): IFeature

    abstract fun getSpecsOfChangedExternalisedExamples(
        filesChangedInCurrentBranch: Set<String>
    ): Set<String>

    open fun regexForMatchingReferred(schemaFileName: String): String = ""
    open fun areExamplesValid(feature: IFeature, which: String): Boolean = true
    open fun getUnusedExamples(feature: IFeature): Set<String> = emptySet()

    final override fun call() {
        gitCommand = SystemGit(workingDirectory = Paths.get(repoDir).absolutePathString())
        addShutdownHook()
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

        logger.log(result.report)
        exitProcess(result.exitCode)
    }

    fun getChangedSpecs(logSpecs: Boolean = false): Set<String> {
        val filesChangedInCurrentBranch = getChangedSpecsInCurrentBranch().filter {
            it.contains(targetPath)
        }.toSet()
        checkIfTheChangedSpecsAreValidOrExit(filesChangedInCurrentBranch.parseResults())

        val filesReferringToChangedSchemaFiles = getSpecsReferringTo(filesChangedInCurrentBranch)
        checkIfTheChangedSpecsAreValidOrExit(filesReferringToChangedSchemaFiles.parseResults())

        val specificationsOfChangedExternalisedExamples =
            getSpecsOfChangedExternalisedExamples(filesChangedInCurrentBranch)
        checkIfTheChangedSpecsAreValidOrExit(specificationsOfChangedExternalisedExamples.parseResults())

        if(logSpecs) {
            logFilesToBeCheckedForBackwardCompatibility(
                filesChangedInCurrentBranch,
                filesReferringToChangedSchemaFiles,
                specificationsOfChangedExternalisedExamples
            )
        }

        return filesChangedInCurrentBranch +
                filesReferringToChangedSchemaFiles +
                specificationsOfChangedExternalisedExamples
    }

    private fun Set<String>.parseResults(): Set<ParseResult> {
        return this.map { parseResult(File(it)) }.toSet()
    }

    private fun checkIfTheChangedSpecsAreValidOrExit(parseResultsOfFilesChangedInCurrentBranch: Set<ParseResult>) {
        if (parseResultsOfFilesChangedInCurrentBranch.any { it.errorMessages.isNotEmpty() }) {
            logger.log("The following changed specs are invalid: $newLine")
            var index = 1
            parseResultsOfFilesChangedInCurrentBranch.forEach { parseResult ->
                if (parseResult.errorMessages.isNotEmpty()) {
                    logger.log("$index. ${parseResult.specPath}, reason(s):")
                    parseResult.errorMessages.forEachIndexed { idx, errorMessage ->
                        logger.log("$ONE_INDENT${idx.inc()}. $errorMessage")
                    }
                    index++
                }
            }
            exitProcess(0)
        }
    }

    private fun getChangedSpecsInCurrentBranch(): Set<String> {
        return gitCommand.getFilesChangedInCurrentBranch(
            baseBranch()
        ).filter {
            File(it).exists()
        }.toSet().also {
            if (it.isEmpty()) {
                logger.log("$newLine No specs were changed, skipping the check.$newLine")
                exitProcess(0)
            }
        }
    }

    open fun getSpecsReferringTo(
        schemaFiles: Set<String>,
        visitedSpecs: MutableSet<String> = mutableSetOf()
    ): Set<String> {
        if (schemaFiles.isEmpty()) return emptySet()

        val inputFileNames = schemaFiles.map { File(it).name }
        val result = allSpecFiles().filter {
            it.readText().trim().let { specContent ->
                inputFileNames.any { inputFileName ->
                    val pattern = Pattern.compile("\\b${regexForMatchingReferred(inputFileName)}\\b")
                    val matcher = pattern.matcher(specContent)
                    matcher.find()
                }
            }
        }.map { it.path }.toSet()

        result.forEach { spec ->
            if (spec !in visitedSpecs) {
                visitedSpecs.add(spec)
                visitedSpecs += getSpecsReferringTo(setOf(spec), visitedSpecs)
            }
        }
        return (visitedSpecs - schemaFiles)
    }

    internal fun allSpecFiles(): List<File> {
        return File(repoDir).walk().toList().filterNot {
            ".git" in it.path
        }.filter { it.isFile }
    }

    private fun logFilesToBeCheckedForBackwardCompatibility(
        changedFiles: Set<String>,
        filesReferringToChangedFiles: Set<String>,
        specificationsOfChangedExternalisedExamples: Set<String>
    ) {
        logger.log("Checking backward compatibility of the following specs:$newLine")
        changedFiles.printSummaryOfChangedSpecs("Specs that have changed")
        filesReferringToChangedFiles.printSummaryOfChangedSpecs("Specs referring to the changed specs")
        specificationsOfChangedExternalisedExamples
            .printSummaryOfChangedSpecs("Specs whose externalised examples were changed")
        logger.log("-".repeat(20))
        logger.log(newLine)
    }

    private fun Set<String>.printSummaryOfChangedSpecs(message: String) {
        if(this.isNotEmpty()) {
            logger.log("${ONE_INDENT}- $message: ")
            this.forEachIndexed { index, it ->
                logger.log(it.prependIndent("$TWO_INDENTS${index.inc()}. "))
            }
            logger.log(newLine)
        }
    }

    private fun getCurrentBranch(): String {
        val branchWithChanges = gitCommand.currentBranch()
        return if (branchWithChanges == HEAD) gitCommand.detachedHEAD() else branchWithChanges
    }

    private fun runBackwardCompatibilityCheckFor(files: Set<String>, baseBranch: String): CompatibilityReport {
        val treeishWithChanges = getCurrentBranch()

        try {
            val results = files.mapIndexed { index, specFilePath ->
                try {
                    logger.log("${index.inc()}. Running the check for $specFilePath:")

                    // newer => the file with changes on the branch
                    val newer = getFeatureFromSpecPath(specFilePath)
                    val unusedExamples = getUnusedExamples(newer)

                    val olderFile = gitCommand.getFileInBranch(
                        specFilePath,
                        treeishWithChanges,
                        baseBranch
                    )
                    if (olderFile == null) {
                        logger.log("$ONE_INDENT$specFilePath is a new file.$newLine")
                        return@mapIndexed CompatibilityResult.PASSED
                    }

                    areLocalChangesStashed = gitCommand.stash()
                    gitCommand.checkout(baseBranch)
                    // older => the same file on the default (e.g. main) branch
                    val older = getFeatureFromSpecPath(olderFile.path)

                    val backwardCompatibilityResult = checkBackwardCompatibility(older, newer)

                    return@mapIndexed getCompatibilityResult(
                        backwardCompatibilityResult,
                        specFilePath,
                        newer,
                        unusedExamples
                    )
                } finally {
                    gitCommand.checkout(treeishWithChanges)
                    if (areLocalChangesStashed) {
                        gitCommand.stashPop()
                        areLocalChangesStashed = false
                    }
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
        if(backwardCompatibilityResult.success().not()) {
            logger.log("_".repeat(40).prependIndent(ONE_INDENT))
            logger.log("The Incompatibility Report:$newLine".prependIndent(ONE_INDENT))
            logger.log(backwardCompatibilityResult.report().prependIndent(TWO_INDENTS))
            logVerdictFor(
                specFilePath,
                "(INCOMPATIBLE) The changes to the spec are NOT backward compatible with the corresponding spec from ${baseBranch()}".prependIndent(ONE_INDENT)
            )
            return CompatibilityResult.FAILED
        }

        val errorsFound = printExampleValiditySummaryAndReturnResult(newer, unusedExamples, specFilePath)

        val message = if(errorsFound) {
            "(INCOMPATIBLE) The spec is backward compatible but the examples are NOT backward compatible or are INVALID."
        } else {
            "(COMPATIBLE) The spec is backward compatible with the corresponding spec from ${baseBranch()}"
        }
        logVerdictFor(specFilePath, message.prependIndent(ONE_INDENT), startWithNewLine = errorsFound)

        return if (errorsFound) CompatibilityResult.FAILED
        else CompatibilityResult.PASSED
    }

    private fun logVerdictFor(specFilePath: String, message: String, startWithNewLine: Boolean = true) {
        if (startWithNewLine) logger.log(newLine)
        logger.log("-".repeat(20).prependIndent(ONE_INDENT))
        logger.log("Verdict for spec $specFilePath:".prependIndent(ONE_INDENT))
        logger.log("$ONE_INDENT$message")
        logger.log("-".repeat(20).prependIndent(ONE_INDENT))
        logger.log(newLine)
    }

    private fun printExampleValiditySummaryAndReturnResult(
        newer: IFeature,
        unusedExamples: Set<String>,
        specFilePath: String
    ): Boolean {
        var errorsFound = false
        val areExamplesInvalid = areExamplesValid(newer, "newer").not()

        if(areExamplesInvalid || unusedExamples.isNotEmpty()) {
            logger.log("_".repeat(40).prependIndent(ONE_INDENT))
            logger.log("The Examples Validity Summary:$newLine".prependIndent(ONE_INDENT))
        }
        if (areExamplesInvalid) {
            logger.log("Examples in $specFilePath are not valid.$newLine".prependIndent(TWO_INDENTS))
            errorsFound = true
        }

        if (unusedExamples.isNotEmpty()) {
            logger.log("Some examples for $specFilePath could not be loaded.$newLine".prependIndent(TWO_INDENTS))
            errorsFound = true
        }
        return errorsFound
    }

    private fun addShutdownHook() {
        Runtime.getRuntime().addShutdownHook(object: Thread() {
            override fun run() {
                runCatching {
                    gitCommand.checkout(getCurrentBranch())
                    if(areLocalChangesStashed) gitCommand.stashPop()
                }
            }
        })
    }

    companion object {
        private const val HEAD = "HEAD"
        private const val MARGIN_SPACE = "  "
        private const val ONE_INDENT = "  "
        private const val TWO_INDENTS = "${ONE_INDENT}${ONE_INDENT}"
    }
}
