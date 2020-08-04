package run.qontract.core.git

import java.io.File

interface GitCommand {
    fun add(): SystemGit
    fun add(relativePath: String): SystemGit
    fun commit(): SystemGit
    fun push(): SystemGit
    fun pull(): SystemGit
    fun resetHard(): SystemGit
    fun resetMixed(): SystemGit
    fun mergeAbort(): SystemGit
    fun checkout(branchName: String): SystemGit
    fun merge(branchName: String): SystemGit
    fun clone(gitRepositoryURI: String, cloneDirectory: File): SystemGit
    fun gitRoot(): String
    fun show(treeish: String, relativePath: String): String
    fun workingDirectoryIsGitRepo(): Boolean
    fun getChangedFiles(): List<String>
    fun relativeGitPath(newerContractPath: String): Pair<GitCommand, String>
    fun fileIsInGitDir(newerContractPath: String): Boolean
}