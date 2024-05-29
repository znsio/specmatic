package application

import `in`.specmatic.conversions.OpenApiSpecification
import `in`.specmatic.core.CONTRACT_EXTENSIONS
import `in`.specmatic.core.git.GitCommand
import `in`.specmatic.core.git.SystemGit
import `in`.specmatic.core.testBackwardCompatibility
import `in`.specmatic.core.utilities.exitWithMessage
import org.springframework.stereotype.Component
import picocli.CommandLine.Command
import java.io.File
import java.util.concurrent.Callable

@Component
@Command(
    name = "backwardCompatibilityCheck",
    mixinStandardHelpOptions = true,
    description = ["Checks backward compatibility of a directory across the current HEAD and the main branch"]
)
open class BackwardCompatibilityCheckCommand(
    private val gitCommand: GitCommand = SystemGit(),
) : Callable<Unit> {

    private val newLine = System.lineSeparator()

    companion object {
        private const val SUCCESS = "success"
        private const val FAILED = "failed"
        private const val HEAD = "HEAD"
        private const val MARGIN_SPACE = "  "
    }

    override fun call() {
        val filesChangedInCurrentBranch: Set<String> = getOpenAPISpecFilesChangedInCurrentBranch()
        if (filesChangedInCurrentBranch.isEmpty()) exitWithMessage("${newLine}No OpenAPI spec files were changed, skipping the check.$newLine")

        val filesReferringToChangedSchemaFiles = filesReferringToChangedSchemaFiles(filesChangedInCurrentBranch)

        val filesToCheck: Set<String> = filesChangedInCurrentBranch + filesReferringToChangedSchemaFiles


        logFilesToBeCheckedForBackwardCompatibility(
            filesChangedInCurrentBranch,
            filesReferringToChangedSchemaFiles
        )

        val result = runBackwardCompatibilityCheckFor(filesToCheck)

        if (result == FAILED) {
            exitWithMessage("$newLine Verdict: FAIL, backward incompatible changes were found.")
        }
        println("$newLine Verdict: PASS, all changes were backward compatible")
    }

    private fun runBackwardCompatibilityCheckFor(files: Set<String>): String {
        val branchWithChanges = gitCommand.currentBranch()
        val treeishWithChanges = if (branchWithChanges == HEAD) gitCommand.detachedHEAD() else branchWithChanges

        try {
            val failures = files.mapIndexed { index, specFilePath ->
                try {
                    println("${index.inc()}. Running the check for $specFilePath:")

                    // newer => the file with changes on the branch
                    val newer = OpenApiSpecification.fromFile(specFilePath).toFeature()

                    val olderFile = gitCommand.getFileInTheDefaultBranch(specFilePath, treeishWithChanges)
                    if (olderFile == null) {
                        println("$specFilePath is a new file.$newLine")
                        return@mapIndexed SUCCESS
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
                        SUCCESS
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
            }.filter { it == FAILED }

            return if (failures.isNotEmpty()) FAILED else SUCCESS
        } finally {
            gitCommand.checkout(treeishWithChanges)
        }
    }

    private fun logFilesToBeCheckedForBackwardCompatibility(
        changedFiles: Set<String>,
        filesReferringToChangedFiles: Set<String>
    ) {
        println("Checking backward compatibility of the following files: $newLine")
        println("Files that have changed - ")
        changedFiles.forEach { println(it) }
        println()
        println("Files referring to the changed files - ")
        filesReferringToChangedFiles.forEach { println(it) }
        println()

        println("-".repeat(20))
        println()
    }

    internal fun filesReferringToChangedSchemaFiles(schemaFiles: Set<String>): Set<String> {
        if (schemaFiles.isEmpty()) return emptySet()

        val schemaFileBaseNames = schemaFiles.map { File(it).name }
        val result = allOpenApiSpecFiles().filter {
            it.readText().let { specContent ->
                schemaFileBaseNames.any { schemaFileBaseName -> schemaFileBaseName in specContent }
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
}

