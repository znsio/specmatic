package application

import io.specmatic.core.utilities.UncaughtExceptionHandler
import picocli.CommandLine
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.util.*
import java.util.logging.LogManager
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
                else ->  {
                    val exitCode = CommandLine(SpecmaticCommand()).execute(*args)
                    exitProcess(exitCode)
                }
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