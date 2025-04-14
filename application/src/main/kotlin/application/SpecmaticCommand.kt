package application

import application.backwardCompatibility.BackwardCompatibilityCheckCommandV2
import org.springframework.stereotype.Component
import picocli.AutoComplete.GenerateCompletion
import picocli.CommandLine.Command
import java.util.concurrent.Callable

@Component
@Command(
        name = "specmatic",
        mixinStandardHelpOptions = true,
        versionProvider = VersionProvider::class,
        subcommands = [
            BackwardCompatibilityCheckCommandV2::class,
            BackwardCompatibilityCheckCommand::class,
            BundleCommand::class,
            CompareCommand::class,
            CompatibleCommand::class,
            DifferenceCommand::class,
            GenerateCompletion::class,
            GraphCommand::class,
            MergeCommand::class,
            ToOpenAPICommand::class,
            ImportCommand::class,
            ProxyCommand::class,
            ExamplesCommand::class,
            StubCommand::class,
            VirtualServiceCommand::class,
            TestCommand::class,
            CentralContractRepoReportCommand::class,
            ConfigCommand::class
        ]
)
class SpecmaticCommand : Callable<Int> {
    override fun call(): Int {
        return 0
    }
}
