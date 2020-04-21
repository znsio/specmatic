package application

import picocli.CommandLine
import picocli.CommandLine.HelpCommand
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.util.*
import java.util.concurrent.Callable
import java.util.logging.LogManager

@CommandLine.Command(name = "qontract", subcommands = [CheckoutCommand::class, CompareCommand::class, ComponentCommand::class, GitCommand::class, MockCommand::class, HelpCommand::class, IncrementCommand::class, ListCommand::class, StubCommand::class, SamplesCommand::class, ShowCommand::class, TestCommand::class, UpdateCommand::class])
class QontractApplication : Callable<Int> {
    override fun call(): Int {
        return 0
    }

    companion object {
        @JvmStatic
        fun main(args: Array<String>) {
            setupLogging()
            when {
                args.isEmpty() -> CommandLine(QontractApplication()).usage(System.out)
                else -> System.exit(CommandLine(QontractApplication()).execute(*args))
            }
        }

        private fun setupLogging() {
            val logManager = LogManager.getLogManager()
            val props = Properties()
            props.setProperty("java.util.logging.ConsoleHandler.level", "FINE")
            val out = ByteArrayOutputStream(512)
            props.store(out, "No comment")
            logManager.readConfiguration(ByteArrayInputStream(out.toByteArray()))
        }
    }
}
