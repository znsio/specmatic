package run.qontract.core

import run.qontract.core.utilities.serviceManifestPath
import org.yaml.snakeyaml.Yaml
import java.io.BufferedReader
import java.io.FileInputStream
import java.io.IOException
import java.io.InputStreamReader
import java.util.*

class ComponentManifest {
    val componentName: String?
        get() = componentManifest[NAME] as String?

    fun hasDependencies(): Boolean {
        return componentManifest.containsKey(DEPENDENCIES)
    }

    val dependencies: HashMap<String?, Any?>
        get() {
            var dependencies = componentManifest[DEPENDENCIES] as HashMap<String?, Any?>?
            if (dependencies == null) {
                dependencies = HashMap()
            }
            return dependencies
        }

    fun getDependencyMajorVersion(dependencyName: String?): Int {
        val dependencies = componentManifest[DEPENDENCIES] as HashMap<String, Any>?
                ?: throw Exception("Dependency not declared")

        val dependencyInfo = dependencies[dependencyName] as HashMap<String, Int>
        return dependencyInfo["major"] ?: throw Exception("Major version not declared")
    }

    fun getDependencyMinorVersion(dependencyName: String?): Int {
        val dependencies = componentManifest[DEPENDENCIES] as HashMap<String, Any>?
        return (dependencies!![dependencyName] as HashMap<String?, Int>?)!!.computeIfAbsent("minor") { x: String? -> 0 }
    }

    val componentContractMajorVersion: Int
        get() {
            val version = componentManifest["contract-version"] as Map<String, Any>?
            return version!!["major"] as Int
        }

    val componentContractMinorVersion: Int
        get() {
            val version = componentManifest["contract-version"] as Map<String, Any>?
            return version!!["minor"] as Int
        }

    companion object {
        const val NAME = "name"
        const val DEPENDENCIES = "dependencies"
        private var componentManifest: HashMap<String, Any?> = getManifest()

        @Throws(IOException::class)
        private fun readFile(contractPath: String): String {
            val fileInputStream = FileInputStream(contractPath)
            val contractBuffer = StringBuilder()
            val bufferedReader = BufferedReader(InputStreamReader(fileInputStream))
            var line: String?
            val lineSeparator = System.getProperty("line.separator")
            while (bufferedReader.readLine().also { line = it } != null) {
                contractBuffer.append(line)
                contractBuffer.append(lineSeparator)
            }
            return contractBuffer.toString().trim { it <= ' ' }
        }

        private fun getManifest(): HashMap<String, Any?> {
            val manifestData = readFile(serviceManifestPath)
            return Yaml().load(manifestData)
        }
    }

}