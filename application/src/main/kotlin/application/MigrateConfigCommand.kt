package application

import io.specmatic.core.config.toSpecmaticConfig
import io.specmatic.core.log.logger
import picocli.CommandLine.Command
import picocli.CommandLine.Option
import java.io.File
import java.util.concurrent.Callable

@Command(
	name = "migrate-config",
	description = ["Migrate Specmatic config to different version"],
	mixinStandardHelpOptions = true
)
class MigrateConfigCommand : Callable<Int> {
	@Option(names = ["--input"], description = ["Path to config file that needs to migrated"], required = true)
	var inputFile: File = File("specmatic.yaml")

	@Option(names = ["--output"], description = ["Output path"])
	var outputFile: File = inputFile

	@Option(names = ["--to-version"], required = true)
	var version: Int = 1

	override fun call(): Int {
		val specmaticConfig = inputFile.toSpecmaticConfig()
		val newConfigYaml = specmaticConfig.transformTo(version)

		logger.log("Writing merged contract file to ${outputFile.path}")
		outputFile.writeText(newConfigYaml)
		return 0
	}
}