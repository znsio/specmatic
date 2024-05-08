package application

import `in`.specmatic.core.CONTRACT_EXTENSIONS
import `in`.specmatic.core.git.GitCommand
import `in`.specmatic.core.git.SystemGit
import org.junit.platform.launcher.Launcher
import org.springframework.beans.factory.annotation.Autowired
import picocli.CommandLine.*
import java.io.File
import java.util.concurrent.Callable

@Command(name = "backwardCompatibilityCheck",
        mixinStandardHelpOptions = true,
        description = ["Checks backward compatibility of a directory across the current HEAD and the main branch"])
class BackwardCompatibilityCheckCommand : Callable<Int> {
//    @Autowired
//    lateinit var gitCommand: GitCommand

    val gitCommand: GitCommand = SystemGit()

    @Autowired
    lateinit var fileOperations: FileOperations

    @Autowired
    lateinit var junitLauncher: Launcher

    override fun call(): Int {
        val filesChangedInCurrentBranch: Set<String> = gitCommand.getFilesChangeInCurrentBranch().filter { File(it).extension in CONTRACT_EXTENSIONS }.toSet()

        val changedSchemaFiles: Set<String> = filesChangedInCurrentBranch.filterNot { File(it).readText().lines().any { it.matches(Regex("paths:")) } }.toSet()
        val schemaFileBaseNames = changedSchemaFiles.map { File(it).name }

        val allFiles = File(".").walk().toList().filterNot { ".git" in it.path }

        val filesReferringToChangedSchemaFiles = allFiles.filter {
            it.readText().let { specContent ->
                schemaFileBaseNames.any { schemaFileBaseName -> schemaFileBaseName in specContent }
            }
        }.map { it.path }.toSet()

        val filesToCheck: Set<String> = (filesChangedInCurrentBranch - changedSchemaFiles) + filesReferringToChangedSchemaFiles

        println("Checking backward compatibility of the following files: ")

        filesToCheck.forEach {
            print(it)
        }

        // Pull logic from CompatibleCommand to check each file in filesToCheck

        return 0
    }
}
