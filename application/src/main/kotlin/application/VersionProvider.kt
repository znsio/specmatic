package application

import io.specmatic.application.VersionInfo
import picocli.CommandLine

class VersionProvider : CommandLine.IVersionProvider {
    override fun getVersion(): Array<String> {
        return arrayOf(VersionInfo.describe())
    }
}
