package application

import application.test.ManifestCommand
import application.versioning.commands.VersionCommand
import picocli.CommandLine
import picocli.CommandLine.Command
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.util.*
import java.util.concurrent.Callable
import java.util.logging.LogManager
import kotlin.system.exitProcess

@Command(name = "qontract", mixinStandardHelpOptions = true, versionProvider = VersionProvider::class, subcommands = [BackwardCompatibleCommand::class, CompareCommand::class, ManifestCommand::class, SamplesCommand::class, StubCommand::class, ImportCommand::class, ProxyCommand::class, TestCommand::class, VersionCommand::class])
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
                else -> exitProcess(CommandLine(QontractApplication()).execute(*args))
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
