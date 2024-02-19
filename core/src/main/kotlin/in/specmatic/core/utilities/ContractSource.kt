package `in`.specmatic.core.utilities

import `in`.specmatic.core.git.NonZeroExitError
import `in`.specmatic.core.git.SystemGit
import `in`.specmatic.core.git.exitErrorMessageContains
import java.io.File

sealed interface ContractSource {
    val type:String?
    val testContracts: List<String>
    val stubContracts: List<String>
    fun pathDescriptor(path: String): String
    fun install(workingDirectory: File)
    fun directoryRelativeTo(workingDirectory: File): File
    fun getLatest(sourceGit: SystemGit)
    fun pushUpdates(sourceGit: SystemGit)
    fun loadContracts(selector: ContractsSelectorPredicate, workingDirectory: String, configFilePath: String): List<ContractPathData>
}

fun commitAndPush(sourceGit: SystemGit) {
    val pushRequired = try {
        sourceGit.commit()
        true
    } catch (e: NonZeroExitError) {
        if (!exitErrorMessageContains(e, listOf("nothing to commit")))
            throw e

        exitErrorMessageContains(e, listOf("branch is ahead of"))
    }

    when {
        pushRequired -> {
            println("Pushing changes")
            sourceGit.push()
        }
        else -> println("No changes were made to the repo, so nothing was pushed.")
    }
}
