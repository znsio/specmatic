package application

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
        val filesChangedInCurrentBranch = gitCommand.getFilesChangeInCurrentBranch().toSet()

        val changedSchemaFiles = filesChangedInCurrentBranch.filterNot { File(it).readText().contains(Regex("^path:")) }.toSet()
        val schemaFileBaseNames = changedSchemaFiles.map { File(it).name }

        val changedReferringFiles = filesChangedInCurrentBranch.filter {
            File(it).readText().let { specContent ->
                schemaFileBaseNames.any { schemaFileBaseName -> schemaFileBaseName in specContent }
            }
        }.toSet()

        val filesToCheck: Set<String> = (filesChangedInCurrentBranch - changedSchemaFiles) + changedReferringFiles

        println("Checking backward compatibility of the following files: ")

        filesToCheck.forEach {
            print(it)
        }

        return 0
    }
}
