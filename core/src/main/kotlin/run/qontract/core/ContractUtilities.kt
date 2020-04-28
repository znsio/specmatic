@file:JvmName("ContractUtilities")
package run.qontract.core

import java.io.File

fun getContractFileName(directory: String, majorVersion: Int, minorVersion: Int? = null): String? {
    return when(val result = testBackwardCompatibilityInDirectory(File(directory), majorVersion, minorVersion)) {
        is JustOne -> File(result.file).absolutePath
        is NoContractsFound -> return null
        is TestResults -> {
            File(if(!result.list.first().results.success())
                result.list.first().older
            else
                result.list.zipWithNext().firstOrNull { (_, thatOne) ->
                    !thatOne.results.success()
                }?.first?.newer ?: result.list.last().newer).absolutePath
        }
    }
}
