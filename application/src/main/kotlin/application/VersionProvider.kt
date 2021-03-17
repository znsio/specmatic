package application

import picocli.CommandLine
import java.util.*

class VersionProvider : CommandLine.IVersionProvider {
    override fun getVersion(): Array<String> {
        val props = Properties()
        SpecmaticCommand::class.java.classLoader.getResourceAsStream("version.properties").use {
            props.load(it)
        }

        return arrayOf(props.getProperty("version"))
    }
}