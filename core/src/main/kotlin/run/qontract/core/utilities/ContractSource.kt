package run.qontract.core.utilities

import java.io.File

typealias SelectorFunction = (repoDir: File, destinationDir: File) -> Unit

data class ContractSource(val gitRepositoryURL: String, val select: SelectorFunction)
