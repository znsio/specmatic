package run.qontract.core.utilities

import run.qontract.core.ComponentManifest
import java.io.BufferedReader
import java.io.IOException
import java.io.InputStreamReader
import java.net.URL
import java.util.*

object BrokerClient {
    @Throws(IOException::class)
    fun readFromURL(url: String?): MutableMap<String, Any?> {
        return readFromURL(URL(url))
    }

    @Throws(IOException::class)
    fun readFromURL(url: URL): MutableMap<String, Any?> {
        val bufferedReader = BufferedReader(InputStreamReader(url.openStream()))
        val buffer = StringBuilder()
        try {
            var tmp: String?
            while (bufferedReader.readLine().also { tmp = it } != null) {
                buffer.append(tmp).append(System.lineSeparator())
            }
        } finally {
            bufferedReader.close()
        }

        return jsonStringToMap(buffer.toString())
    }

    @Throws(IOException::class)
    fun getContractTestsForProviderMajorVersion(environment: String, provider: String, majorVersion: Int): MutableMap<String, Any?> {
        val url = brokerURL + "/environment/" + environment + "/contract-tests?provider-service=" + provider + "&version=" + majorVersion
        return readFromURL(url)
    }

    @Throws(IOException::class)
    fun getContractVersions(componentName: String): ArrayList<ArrayList<Int>> {
        val url = brokerURL + "/contract-versions?provider=" + componentName
        val jsonObject = readFromURL(url)
        val versionsJSONArray = jsonObject["versions"] as List<Any?>
        val versions = ArrayList<ArrayList<Int>>()
        for (i in versionsJSONArray.indices) {
            val versionArray = versionsJSONArray[i] as List<Int>
            val version = ArrayList<Int>()

            for (value in versionArray) {
                version.add(value)
            }

            versions.add(version)
        }
        return versions
    }

    @Throws(IOException::class)
    fun getLatestContractForMajorVersion(componentManifest: ComponentManifest, dependency: String): MutableMap<String, Any?> {
        val contractURL = brokerURL + "/contracts?provider="
        val url = contractURL +
                dependency +
                "&majorVersion=" +
                componentManifest.getDependencyMajorVersion(dependency)
        return readFromURL(url)
    }

    @Throws(IOException::class)
    fun getSupportedContract(componentManifest: ComponentManifest, environment: String, dependency: String): MutableMap<String, Any?> {
        val majorVersion = componentManifest.getDependencyMajorVersion(dependency)
        val contractVersionURL = brokerURL + "/environment/" + environment + "?contract-name=" + dependency + "&majorVersion=" + majorVersion
        val jsonObject = readFromURL(contractVersionURL)
        var minorVersion = 0
        if (jsonObject.containsKey("minorVersion")) {
            minorVersion = jsonObject["minorVersion"] as Int
        }
        return getContract(dependency, majorVersion, minorVersion)
    }

    @Throws(IOException::class)
    fun getContract(contractName: String, majorVersion: Int, minorVersion: Int): MutableMap<String, Any?> {
        val contractURL = brokerURL + "/contracts" + "?provider=" + contractName + "&majorVersion=" + majorVersion + "&minorVersion=" + minorVersion
        return readFromURL(contractURL)
    }
}