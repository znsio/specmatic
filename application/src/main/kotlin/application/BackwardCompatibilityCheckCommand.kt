package application

import `in`.specmatic.core.CONTRACT_EXTENSIONS
import `in`.specmatic.core.git.GitCommand
import org.junit.platform.launcher.Launcher
import org.springframework.beans.factory.annotation.Autowired
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
    private val gitCompatibleCommand: GitCompatibleCommand,
    private val fileOperations: FileOperations,
    private val junitLauncher: Launcher,
) : Callable<Int> {

    override fun call(): Int {
        val filesChangedInCurrentBranch: Set<String> = gitCommand.getFilesChangeInCurrentBranch().filter {
            File(it).extension in CONTRACT_EXTENSIONS
        }.toSet()

        val changedSchemaFiles: Set<String> = filesChangedInCurrentBranch.filter {
            isSchemaFile(File(it))
        }.toSet()

        val filesToCheck: Set<String> =
            filesChangedInCurrentBranch - changedSchemaFiles + filesReferringToChangedSchemaFiles(changedSchemaFiles)

        logFilesToBeCheckedForBackwardCompatibility(filesToCheck)
        runBackwardCompatibilityCheckFor(filesToCheck)

        return 0
    }

    private fun runBackwardCompatibilityCheckFor(files: Set<String>) {
        files.forEachIndexed { index, it ->
            println("[${index.inc()}] Running the check for $it:")
            gitCompatibleCommand.file(it, "", false)
            println("-".repeat(20))
            println()
        }
    }

    private fun logFilesToBeCheckedForBackwardCompatibility(files : Set<String>) {
        println("Checking backward compatibility of the following files: ")
        files.forEach { println(it) }
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
