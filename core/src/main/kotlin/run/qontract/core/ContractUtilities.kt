@file:JvmName("ContractUtilities")
package run.qontract.core

import java.io.File

fun getLatestCompatibleContractFileName(directory: String, version: Int): String? {
    return when(val result = testBackwardCompatibilityInDirectory(File(directory), version)) {
        is JustOne -> File(result.file).absolutePath
        is Nothing -> return null
        is ResultList -> {
            File(if(!result.list.first().results.success())
                result.list.first().older
            else
                result.list.zipWithNext().firstOrNull { (_, thatOne) ->
                    !thatOne.results.success()
                }?.first?.newer ?: result.list.last().newer).absolutePath
        }
    }
}
