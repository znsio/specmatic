package application.versioning.commands

import org.eclipse.jgit.api.Git
import org.eclipse.jgit.lib.ObjectId
import org.eclipse.jgit.lib.ObjectLoader
import org.eclipse.jgit.revwalk.RevCommit
import org.eclipse.jgit.treewalk.TreeWalk
import picocli.CommandLine.Command
import picocli.CommandLine.Parameters
import run.qontract.core.ContractBehaviour
import run.qontract.core.testBackwardCompatibility2
import java.io.File
import java.io.FileNotFoundException
import java.util.concurrent.Callable
import kotlin.system.exitProcess

@Command(name = "checkGitFile", mixinStandardHelpOptions = true, description = ["Check backward compatibility of the current file with the version in the last commit in git"])
class CheckGitFileCommand: Callable<Unit> {
    @Parameters(index = "0", description = ["Git file to check"])
    var filePath: String = ""

    override fun call() {
        try {
            val gitFile = File(filePath)
            val (commitId, older) = getOlder(gitFile)
            val newer = ContractBehaviour(gitFile.readText())

            val results = testBackwardCompatibility2(older, newer)

            if (results.success()) {
                println("This contract is backward compatible with the previous one in commit $commitId.")
            } else {
                println("This contract is not backward compatible with the previous one in commit $commitId.")
                println()
                println(results.report())
                exitProcess(1)
            }
        }
        catch (e: Throwable) {
            println(e.localizedMessage)
        }
    }
}

private fun getOlder(newer: File): Pair<String, ContractBehaviour> {
    if(!newer.exists()) {
        throw FileNotFoundException("${newer.absoluteFile} does not exist.")
    }

    return Git.open(findParentGitDir(newer)).use { git ->
        val relativeFile = newer.absoluteFile.relativeTo(findParentGitDir(newer).absoluteFile)
        val commit = git.log().call().first()

        Pair(commit.name, ContractBehaviour(getContent(git, commit, relativeFile.path) ?: throw Exception("Couldn't find the file in git history.")))
    }
}

private fun getContent(git: Git, commit: RevCommit, path: String): String? {
    return git.repository.newObjectReader().use { repo ->
        TreeWalk.forPath(git.repository, path, commit.tree).use { treeWalk ->
            if(treeWalk == null) {
                throw FileNotFoundException("Couldn't find the path in the last commit (${commit.name})")
            }

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
            throw FileNotFoundException("Couldn't find a parent git directory.")

        return findParentGitDir(filePath.absoluteFile.parentFile)
    }
}
