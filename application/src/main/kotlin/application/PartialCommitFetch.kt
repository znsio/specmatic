package application

import run.qontract.core.git.GitCommand
import run.qontract.core.utilities.exceptionCauseMessage

val getFileContentAtSpecifiedCommit = {
    gitRoot: GitCommand -> { relativeContractPath: String -> { contractPath: String -> { commit: String ->
    try {
        Outcome(gitRoot.show(commit, relativeContractPath).trim())
    } catch (e: Throwable) {
        Outcome(null, "Could not load ${commit}:${contractPath} because of error:\n${exceptionCauseMessage(e)}")
    }
} } } }
