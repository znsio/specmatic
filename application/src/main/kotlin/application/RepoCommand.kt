package application

import org.eclipse.jgit.api.Git
import org.eclipse.jgit.api.TransportConfigCallback
import org.eclipse.jgit.transport.SshTransport
import org.eclipse.jgit.transport.Transport
import org.eclipse.jgit.transport.sshd.SshdSessionFactory
import picocli.CommandLine
import picocli.CommandLine.Command
import picocli.CommandLine.Parameters
import run.qontract.core.qontractCacheDirPath
import run.qontract.core.qontractRepoDirPath
import run.qontract.core.versioning.POINTER_EXTENSION
import run.qontract.core.versioning.getTransportCallingCallback
import java.io.File
import java.util.concurrent.Callable

@Command(name = "repo", description = ["Create a qontract repository that tracks an external repository containing contracts. Currently only external git repositories are supported."], mixinStandardHelpOptions = true)
class RepoCommand: Callable<Unit> {
    @CommandLine.Command
    fun git(@Parameters(index = "0", paramLabel = "<repoName>", description = ["The name of the git repository"]) repoName: String = "", @Parameters(index = "1", paramLabel = "<repoURI>", description = ["The URL of the git repository"]) repoURI: String = "") {
        val gitRepo = GitRepo(repoName, repoURI)
        checkoutGitRepo(gitRepo)
        createRepoDescriptor(gitRepo)
        linkContracts(gitRepo)
    }

    override fun call() {
        CommandLine(RepoCommand()).usage(System.out)
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
    Git.cloneRepository().setURI(repo.uri).setTransportConfigCallback(getTransportCallingCallback()).setDirectory(repo.repoDir.file).call()
}

fun linkContracts(gitRepo: GitRepo) {
    val contractFiles = listOfFiles(gitRepo.repoDir.file, "contract")

    val destFiles = contractFiles.map {
        it.absolutePath
                .removePrefix(gitRepo.repoDir.file.absolutePath)
                .removePrefix("/")
                .removeSuffix(".contract")
                .plus(".$POINTER_EXTENSION")
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
            else -> emptyList()
        }
    } ?: emptyList()
}

data class ExistingDirectory(val directory: String) {
    val file = File(directory)

    init {
        file.mkdirs()
    }
}
