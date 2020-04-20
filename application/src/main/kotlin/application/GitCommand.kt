package application

import org.eclipse.jgit.api.Git
import picocli.CommandLine
import picocli.CommandLine.*
import java.io.File
import java.util.concurrent.Callable

@Command(name = "git", mixinStandardHelpOptions = true)
class GitCommand: Callable<Unit> {
    @CommandLine.Command
    fun add(@Parameters(index = "0", descriptionKey = "repoName") repoName: String = "", @Parameters(index = "1", descriptionKey = "repoURL") repoURI: String = "") {
        val gitRepo = GitRepo(repoName, repoURI)
        checkoutGitRepo(gitRepo)
        createRepoDescriptor(gitRepo)
        linkContracts(gitRepo)
    }

    override fun call() {
        CommandLine(GitCommand()).usage(System.out)
    }
}

class GitRepo(val name: String, val uri: String) {
    val repoDir = ExistingDirectory(repoDirPath(name))
}

fun repoDirPath(name: String): String = "$qontractRepoDirPath/${name}/repo"

fun createRepoDescriptor(gitRepo: GitRepo) {
    val descriptorPath = "$qontractRepoDirPath/${gitRepo.name}/conf.json"
    val conf = """{"type": "git", "uri": "${gitRepo.uri}"}"""

    ExistingFile(descriptorPath).file.writeText(conf)
}

fun checkoutGitRepo(repo: GitRepo) {
    println("Cloning from ${repo.uri} to ${repo.repoDir.file.absolutePath}")
    Git.cloneRepository().setURI(repo.uri).setDirectory(repo.repoDir.file).call()
}

fun linkContracts(gitRepo: GitRepo) {
    val contractFiles = listOfFiles(gitRepo.repoDir.file, "contract")

    val destFiles = contractFiles.map {
        it.absolutePath
                .removePrefix(gitRepo.repoDir.file.absolutePath)
                .removePrefix("/")
                .removeSuffix(".contract")
                .plus(".pointer")
    }.map { ExistingFile("$qontractCacheDirPath/$it") }

    contractFiles.map { it.absolutePath }.zip(destFiles).forEach { (contractPath, pointer) ->
        pointer.file.writeText("""{"repoName": "${gitRepo.name}", "contractPath": "$contractPath"}""")
    }
}

fun listOfFiles(file: File, extension: String = ""): List<File> {
    return file.listFiles()?.flatMap {
        when {
            it.isDirectory -> when {
                it.name != ".git" -> listOfFiles(it, extension)
                else -> emptyList()
            }
            extension.isNotBlank() && it.name.endsWith(".$extension") -> listOf(it)
            else -> listOf(it)
        }
    } ?: emptyList()
}

data class ExistingDirectory(val directory: String) {
    val file = File(directory)

    init {
        file.mkdirs()
    }
}

val qontractDirPath: String = "${System.getProperty("user.home")}/.qontract"
val qontractCacheDirPath = "$qontractDirPath/cache"
val qontractRepoDirPath = "$qontractDirPath/repos"
