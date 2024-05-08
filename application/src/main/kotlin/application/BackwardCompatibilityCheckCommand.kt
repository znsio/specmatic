package application

import `in`.specmatic.conversions.OpenApiSpecification
import `in`.specmatic.core.CONTRACT_EXTENSIONS
import `in`.specmatic.core.git.GitCommand
import `in`.specmatic.core.testBackwardCompatibility
import `in`.specmatic.core.utilities.exitWithMessage
import org.springframework.stereotype.Component
import picocli.CommandLine.Command
import java.io.File
import java.util.concurrent.Callable

@Component
@Command(name = "backwardCompatibilityCheck",
        mixinStandardHelpOptions = true,
        description = ["Checks backward compatibility of a directory across the current HEAD and the main branch"])
class BackwardCompatibilityCheckCommand(
    private val gitCommand: GitCommand,
) : Callable<Unit> {

    val SUCCESS = "success"
    val FAILED = "failed"

    override fun call(): Unit {
        // TODO - openapi parsing for figuring out the correct files
        val filesChangedInCurrentBranch: Set<String> = gitCommand.getFilesChangeInCurrentBranch().filter {
            File(it).extension in CONTRACT_EXTENSIONS
        }.filter { File(it).exists() && File(it).readText().contains(Regex("openapi")) }.toSet()

        val filesReferringToChangedSchemaFiles = filesReferringToChangedSchemaFiles(filesChangedInCurrentBranch)

        val filesToCheck: Set<String> = filesChangedInCurrentBranch + filesReferringToChangedSchemaFiles

        logFilesToBeCheckedForBackwardCompatibility(filesChangedInCurrentBranch, filesReferringToChangedSchemaFiles)

        val result = runBackwardCompatibilityCheckFor(filesToCheck)

        println()

        if(result == FAILED) {
            exitWithMessage("Verdict: FAIL, backward incompatible changes were found.")
        } else {
            println("Verdict: PASS, all changes were backward compatible")
        }
    }

    private fun runBackwardCompatibilityCheckFor(files: Set<String>): String {
        val currentBranch = gitCommand.currentBranch()

        val currentTreeish = if(currentBranch == "HEAD")
            gitCommand.detachedHEAD()
        else
            currentBranch

        val defaultBranch = gitCommand.defaultBranch()

        try {
            val failures = files.mapIndexed { index, specFilePath ->
                println("${index.inc()}. Running the check for $specFilePath:")

                val newer = OpenApiSpecification.fromFile(specFilePath).toFeature()

                gitCommand.checkout(defaultBranch)

                if (!File(specFilePath).exists()) {
                    println("$specFilePath is a new file.")
                    println()
                    return@mapIndexed SUCCESS
                }

                val older = OpenApiSpecification.fromFile(specFilePath).toFeature()

                gitCommand.checkout(currentTreeish)

                val backwardCompatibilityResult = testBackwardCompatibility(older, newer)

                if (backwardCompatibilityResult.success()) {
                    println("The file $specFilePath is backward compatible.")
                    println()
                    SUCCESS
                } else {
                    println("*** The file $specFilePath is NOT backward compatible. ***")
                    println()
                    FAILED
                }
            }.filter { it == FAILED }

            return if (failures.size > 0) FAILED else SUCCESS
        } finally {
            gitCommand.checkout(currentTreeish)
        }
    }

    private fun logFilesToBeCheckedForBackwardCompatibility(changedFiles : Set<String>, filesReferringToChangedFiles: Set<String>) {
        println("Checking backward compatibility of the following files: \n")
        println("Files that have changed - ")
        changedFiles.forEach { println(it) }
        println()
        println("Files referring to the changed files - ")
        filesReferringToChangedFiles.forEach { println(it) }
        println()

        println("-".repeat(20))
        println()
    }


    private fun filesReferringToChangedSchemaFiles(schemaFiles : Set<String>): Set<String> {
        if(schemaFiles.isEmpty()) return emptySet()

        val schemaFileBaseNames = schemaFiles.map { File(it).name }
        return allFiles().filter {
            it.readText().let { specContent ->
                schemaFileBaseNames.any { schemaFileBaseName -> schemaFileBaseName in specContent }
            }
        }.map { it.path }.toSet()
    }

    private fun isSchemaFile(file: File): Boolean {
        return file.readText().lines().any { it.matches(Regex("paths:")) }.not()
    }

    private fun allFiles(): List<File> {
        return File(".").walk().toList().filterNot {
            ".git" in it.path
        }.filter { it.isFile }
    }
}
