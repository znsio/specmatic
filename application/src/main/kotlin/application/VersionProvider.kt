package application

import io.specmatic.application.VersionInfo
import picocli.CommandLine
import java.util.*

class VersionProvider : CommandLine.IVersionProvider {
    override fun getVersion(): Array<String> {
        return arrayOf(VersionInfo.describe())
    }
}
