package application.versioning.commands

import application.PushCommand
import picocli.CommandLine
import picocli.CommandLine.Command
import java.util.concurrent.Callable

@Command(name = "version", mixinStandardHelpOptions = true, description = ["Suggest the version of a contract"], subcommands = [CheckAncestorsCommand::class, CheckGitFileCommand::class, CheckoutCommand::class, IncrementCommand::class, ListCommand::class, RepoCommand::class, ShowCommand::class, SuggestCommand::class, PushCommand::class])
class VersionCommand: Callable<Unit> {
    override fun call() {
        CommandLine(VersionCommand()).usage(System.out)
    }
}