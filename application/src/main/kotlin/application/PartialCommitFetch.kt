package application

import run.qontract.core.git.GitCommand

data class PartialCommitFetch(val gitRoot: GitCommand, val relativeContractPath: String, val contractPath: String) {
    fun apply(commit: String): Outcome<String> {
        return try {
            Outcome(gitRoot.show(commit, relativeContractPath).trim())
        } catch (e: Throwable) {
            Outcome(null, "Could not load ${commit}:${contractPath}")
        }
    }
}