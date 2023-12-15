package `in`.specmatic.core

import `in`.specmatic.core.log.logger
import `in`.specmatic.core.utilities.ExternalCommand
import java.io.File

enum class HookName {
    stub_load_contract,
    test_load_contract
}

class CommandHook(private val name: HookName): Hook {
    val command: String? = name.let { Configuration.config?.hooks?.get(it.name) }

    override fun readContract(path: String): String {
        checkExists(File(path))

        return command?.let {
            logger.log("  Invoking hook $name when loading contract $path")
            ExternalCommand(it, ".", mapOf("CONTRACT_FILE" to path)).executeAsSeparateProcess()
        } ?: File(path).readText()
    }
}