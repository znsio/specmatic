package application

import org.springframework.stereotype.Component
import picocli.AutoComplete.GenerateCompletion
import picocli.CommandLine.Command
import java.util.concurrent.Callable

@Component
@Command(
        name = "specmatic",
        mixinStandardHelpOptions = true,
        versionProvider = VersionProvider::class,
        subcommands = [BundleCommand::class, CompareCommand::class, CompatibleCommand::class, TestCountCommand::class, DifferenceCommand::class, GenerateCompletion::class, GraphCommand::class, MergeCommand::class, ToOpenAPICommand::class, ImportCommand::class, InstallCommand::class, ProxyCommand::class, PushCommand::class, ReDeclaredAPICommand::class, SamplesCommand::class, StubCommand::class, SubscribeCommand::class, TestCommand::class, ValidateViaLogs::class, CentralContractRepoReportCommand::class]
)
class SpecmaticCommand : Callable<Int> {
    override fun call(): Int {
        return 0
    }
}
