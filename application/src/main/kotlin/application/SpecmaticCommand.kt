package application

import org.springframework.stereotype.Component
import picocli.AutoComplete.GenerateCompletion
import picocli.CommandLine.Command
import java.util.concurrent.Callable

@Component
@Command(
        name = "specmatic",
        description = ["Specmatic CLI tool"],
        mixinStandardHelpOptions = true,
        versionProvider = VersionProvider::class,
        subcommands = [BackwardCompatibilityCheckCommand::class, StubCommand::class, TestCommand::class, Separator::class, BundleCommand::class, GenerateCompletion::class, GraphCommand::class, MergeCommand::class, ToOpenAPICommand::class, ImportCommand::class, InstallCommand::class, ProxyCommand::class, ReDeclaredAPICommand::class, ExamplesCommand::class, SamplesCommand::class,  SubscribeCommand::class, ValidateViaLogs::class, CentralContractRepoReportCommand::class],
        synopsisHeading = "%nUsage: ",
        commandListHeading = "%nCommands:%n",
        optionListHeading = "%nOptions:%n",
        footer = ["%nRun 'specmatic COMMAND --help' for more information on a command."],
)
class SpecmaticCommand : Callable<Int> {
    override fun call(): Int {
        return 0
    }
}
