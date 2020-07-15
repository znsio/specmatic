package application.versioning.commands

import application.AddCommand
import picocli.CommandLine
import picocli.CommandLine.Command
import java.util.concurrent.Callable

@Command(name = "version", mixinStandardHelpOptions = true, description = ["Suggest the version of a contract"], subcommands = [CheckAncestorsCommand::class, CheckGitFileCommand::class, CheckoutCommand::class, IncrementCommand::class, ListCommand::class, RepoCommand::class, ShowCommand::class, SuggestCommand::class, AddCommand::class])
class VersionCommand: Callable<Unit> {
    override fun call() {
        CommandLine(VersionCommand()).usage(System.out)
    }
}