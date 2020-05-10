package application.versioning.commands

import org.eclipse.jgit.api.Git
import org.eclipse.jgit.lib.ObjectId
import org.eclipse.jgit.lib.ObjectLoader
import org.eclipse.jgit.revwalk.RevCommit
import org.eclipse.jgit.treewalk.TreeWalk
import picocli.CommandLine.Command
import picocli.CommandLine.Parameters
import run.qontract.core.ContractBehaviour
import run.qontract.core.testBackwardCompatibility
import java.io.File
import java.util.concurrent.Callable
import kotlin.system.exitProcess

@Command(name = "checkGitFile", mixinStandardHelpOptions = true, description = ["Check backward compatibility of the current file with the version in the last commit in git"])
class CheckGitFileCommand: Callable<Unit> {
    @Parameters(index = "0", description = ["Git file to check"])
    var filePath: String = ""

    override fun call() {
        val gitFile = File(filePath)
        val older = getOlder(gitFile)
        val newer = ContractBehaviour(gitFile.readText())

        val results = testBackwardCompatibility(older, newer)

        if(results.success()) {
            println("The new version is backward compatible with the old.")
        }
        else {
            println("The new version is not backward compatible with the old.")
            println()
            println(results.report())
            exitProcess(1)
        }
    }
}

private fun getOlder(newer: File): ContractBehaviour {
    return Git.open(findParentGitDir(newer)).use { git ->
        val relativeFile = newer.absoluteFile.relativeTo(findParentGitDir(newer).absoluteFile)
        val commit = git.log().call().first()

        ContractBehaviour(getContent(git, commit, relativeFile.path) ?: throw Exception("Couldn't find the file in git history."))
    }
}

private fun getContent(git: Git, commit: RevCommit, path: String): String? {
    return git.repository.newObjectReader().use { repo ->
        TreeWalk.forPath(git.repository, path, commit.tree).use { treeWalk ->
            val blobId: ObjectId = treeWalk.getObjectId(0)

            repo.use { objectReader ->
                val objectLoader: ObjectLoader = objectReader.open(blobId)
                val bytes = objectLoader.bytes
                String(bytes)
            }
        }
    }
}

private fun findParentGitDir(filePath: File): File {
    val gitDir = filePath.listFiles()?.firstOrNull { it.name == ".git" }

    if(gitDir != null) {
        return filePath
    } else {
        if(filePath.absoluteFile.parentFile == null)
            throw Exception("Couln't find git parent")

        return findParentGitDir(filePath.absoluteFile.parentFile)
    }
}
