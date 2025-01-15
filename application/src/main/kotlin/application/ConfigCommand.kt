package application

import io.specmatic.core.config.getVersion
import io.specmatic.core.config.toSpecmaticConfig
import io.specmatic.core.config.v2.SPECMATIC_CONFIG_VERSION_2
import io.specmatic.core.log.logger
import io.specmatic.core.utilities.exitWithMessage
import picocli.CommandLine.Command
import picocli.CommandLine.Option
import java.io.File
import java.util.concurrent.Callable

private const val SUCCESS_EXIT_CODE = 0
private const val FAILURE_EXIT_CODE = 1

private const val SPECMATIC_YAML = "specmatic.yaml"
private const val SPECMATIC_YML = "specmatic.yml"
private const val SPECMATIC_JSON = "specmatic.json"

@Command(
    name = "config",
    mixinStandardHelpOptions = true,
    description = ["Manage and configure Specmatic Config."],
    subcommands = [
        ConfigCommand.Upgrade::class
    ]
)
class ConfigCommand : Callable<Int> {
    override fun call(): Int {
        println("Use a subcommand. Use --help for more details.")
        return SUCCESS_EXIT_CODE
    }

    @Command(
        name = "upgrade",
        mixinStandardHelpOptions = true,
        description = ["Upgrade Specmatic Config to the latest version."]
    )
    class Upgrade : Callable<Int> {
        @Option(names = ["--input"], description = ["Path to config file that needs to upgraded."])
        var inputFile: File? = null

        @Option(names = ["--output"], description = ["Output file path for the upgraded config."])
        var outputFile: File? = null

        private val upgradeVersion = SPECMATIC_CONFIG_VERSION_2

        override fun call(): Int {
            inputFile = inputFile ?: getConfigFile() ?: run {
                logger.log("Missing input file. Please provide path to Specmatic Config.")
                return FAILURE_EXIT_CODE
            }

            if (inputFile!!.readText().getVersion() == upgradeVersion) {
                logger.log("Config is already up-to-date")
                return SUCCESS_EXIT_CODE
            }

            val upgradedConfigYaml = getUpgradedConfig(inputFile!!)

            if (outputFile != null) {
                logger.log("Writing upgraded config file to ${outputFile!!.path}")
                outputFile!!.writeText(upgradedConfigYaml)
            } else {
                logger.log(upgradedConfigYaml)
            }

            return SUCCESS_EXIT_CODE
        }

        private fun getConfigFile(): File? {
            val yamlFile = File(SPECMATIC_YAML)
            val ymlFile = File(SPECMATIC_YML)
            val jsonFile = File(SPECMATIC_JSON)

            return when {
                yamlFile.exists() -> yamlFile
                ymlFile.exists() -> ymlFile
                jsonFile.exists() -> jsonFile
                else -> null
            }
        }

        private fun getUpgradedConfig(configFile: File): String {
            return try {
                configFile.toSpecmaticConfig().upgradeTo(upgradeVersion)
            } catch (e: Exception) {
                exitWithMessage(e.message.orEmpty())
            }
        }
    }
}