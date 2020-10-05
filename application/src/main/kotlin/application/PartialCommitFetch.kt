package application

import run.qontract.core.git.GitCommand

data class PartialCommitFetch(val gitRoot: GitCommand, val relativeContractPath: String, val contractPath: String) {
    fun apply(commit: String): OutCome<String> {
        return try {
            OutCome(gitRoot.show(commit, relativeContractPath).trim())
        } catch (e: Throwable) {
            OutCome(null, "Could not load ${commit}:${contractPath}")
        }
    }
}