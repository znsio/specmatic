package application

import `in`.specmatic.consoleLog
import `in`.specmatic.core.CONTRACT_EXTENSIONS
import java.io.File
import java.nio.file.FileSystems
import java.nio.file.Path
import java.nio.file.StandardWatchEventKinds
import java.nio.file.WatchKey

class Watcher(private val contractPaths: List<String>) {
    fun watchForChanges(restartServer: () -> Unit) {
        FileSystems.getDefault().newWatchService().use { watchService ->
            val paths: List<Path> = getPaths(contractPaths).toSet().toList().sorted().map { File(it).toPath() }

            paths.forEach { contractPath ->
                contractPath.register(watchService, StandardWatchEventKinds.ENTRY_MODIFY, StandardWatchEventKinds.ENTRY_CREATE, StandardWatchEventKinds.ENTRY_DELETE)
            }

            var key: WatchKey
            while (watchService.take().also { key = it } != null) {
                key.reset()

                val events = key.pollEvents().map { it.context().toString() }.joinToString(", ")
                consoleLog("""Detected event(s) for $events, restarting stub server.""")
                restartServer()
            }
        }
    }

    private fun getPaths(contractPaths: List<String>): List<String> {
        return contractPaths.map { File(it) }.flatMap {
            when {
                it.isFile && it.extension.toLowerCase() in CONTRACT_EXTENSIONS ->
                    listOf(it.absoluteFile.parentFile.path).plus(getPaths(listOf(dataDirOf(it))))
                it.isFile && it.extension.equals("json", ignoreCase = true) ->
                    listOf(it.absoluteFile.parentFile.path)
                it.isDirectory ->
                    listOf(it.absolutePath).plus(getPaths(it.listFiles()?.toList()?.map { file -> file.absolutePath } ?: emptyList()))
                else -> emptyList()
            }
        }
    }

    private fun dataDirOf(contractFile: File): String {
        return "${contractFile.absoluteFile.parent}/${contractFile.nameWithoutExtension}_data"
    }
}
