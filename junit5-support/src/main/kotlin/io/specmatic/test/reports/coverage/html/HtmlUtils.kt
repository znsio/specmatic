package io.specmatic.test.reports.coverage.html

import java.io.InputStream
import java.nio.file.Files
import java.nio.file.Paths
import java.nio.file.StandardCopyOption

fun loadFileFromClasspath(fileName: String): InputStream {
    val classLoader = Thread.currentThread().contextClassLoader
    return classLoader.getResourceAsStream(fileName)
        ?: throw IllegalArgumentException("File not found in Classpath: $fileName")
}

fun loadFileFromClasspathAndSaveIt(fileName: String, outputPath: String = ".") {
    val fileStream = loadFileFromClasspath(fileName)
    fileStream.use { stream ->
        val targetPath = Paths.get(outputPath, fileName)
        targetPath.parent.toFile().mkdirs()
        Files.copy(stream, targetPath, StandardCopyOption.REPLACE_EXISTING)
    }
}

fun createAssetsDir(reportsDir: String) {
    val fileNames = listOf(
        "badge.svg",
        "blocked.svg",
        "check-badge.svg",
        "clipboard-document-list.svg",
        "clock.svg",
        "download.svg",
        "exclamation-triangle.svg",
        "favicon.svg",
        "index.js",
        "mark-approved.svg",
        "mark-rejected.svg",
        "specmatic-logo.svg",
        "styles.css",
        "trend-up.svg",
        "x-circle.svg"
    )

    fileNames.forEach { fileName ->
        loadFileFromClasspathAndSaveIt(
            "assets/${fileName}",
            reportsDir
        )
    }
}

fun writeToFileToAssets(outputDir: String, fileName: String, data: String) {
    val targetPath = Paths.get("$outputDir/assets", fileName)
    targetPath.parent.toFile().mkdirs()
    Files.write(targetPath, data.toByteArray())
}