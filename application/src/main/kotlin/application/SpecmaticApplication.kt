package application

import io.specmatic.core.utilities.UncaughtExceptionHandler
import io.specmatic.specmatic.executable.JULForwarder
import picocli.CommandLine
import kotlin.system.exitProcess

open class SpecmaticApplication {
    companion object {
        @JvmStatic
        fun main(args: Array<String>) {
            setupLogging()

            Thread.setDefaultUncaughtExceptionHandler(UncaughtExceptionHandler())

            if (args.isEmpty() || args[0] !in listOf("--version", "-V")) {
                println("Specmatic Version: ${VersionProvider().getVersion()[0]}" + System.lineSeparator())
            }

            when {
                args.isEmpty() -> CommandLine(SpecmaticCommand()).usage(System.out)
                else -> {
                    val exitCode = CommandLine(SpecmaticCommand()).execute(*args)
                    exitProcess(exitCode)
                }
            }
        }

        private fun setupLogging() {
            JULForwarder.forward()
        }
    }
}
