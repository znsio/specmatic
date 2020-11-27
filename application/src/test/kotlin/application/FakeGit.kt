package application

import run.qontract.core.git.GitCommand
import run.qontract.core.git.SystemGit
import java.io.File

abstract class FakeGit: GitCommand {
    override fun add(): SystemGit {
        TODO("Not yet implemented")
    }

    override fun add(relativePath: String): SystemGit {
        TODO("Not yet implemented")
    }

    override fun commit(): SystemGit {
        TODO("Not yet implemented")
    }

    override fun push(): SystemGit {
        TODO("Not yet implemented")
    }

    override fun pull(): SystemGit {
        TODO("Not yet implemented")
    }

    override fun resetHard(): SystemGit {
        TODO("Not yet implemented")
    }

    override fun resetMixed(): SystemGit {
        TODO("Not yet implemented")
    }

    override fun mergeAbort(): SystemGit {
        TODO("Not yet implemented")
    }

    override fun checkout(branchName: String): SystemGit {
        TODO("Not yet implemented")
    }

    override fun merge(branchName: String): SystemGit {
        TODO("Not yet implemented")
    }

    override fun clone(gitRepositoryURI: String, cloneDirectory: File): SystemGit {
        TODO("Not yet implemented")
    }

    override fun gitRoot(): String {
        TODO("Not yet implemented")
    }

    override fun show(treeish: String, relativePath: String): String {
        TODO("Not yet implemented")
    }

    override fun workingDirectoryIsGitRepo(): Boolean {
        TODO("Not yet implemented")
    }

    override fun getChangedFiles(): List<String> {
        TODO("Not yet implemented")
    }

    override fun relativeGitPath(newerContractPath: String): Pair<GitCommand, String> {
        TODO("Not yet implemented")
    }

    override fun fileIsInGitDir(newerContractPath: String): Boolean {
        TODO("Not yet implemented")
    }

    override fun inGitRootOf(contractPath: String): GitCommand {
        TODO("Not yet implemented")
    }

    override fun repoName(): String {
        TODO("Not yet implemented")
    }
}
