package application

import run.qontract.core.utilities.readFile
import picocli.CommandLine.*
import run.qontract.core.Contract
import run.qontract.core.pattern.ContractException
import run.qontract.core.resultReport
import run.qontract.fake.ContractFake
import java.util.concurrent.Callable

@Command(name = "compare",
        mixinStandardHelpOptions = true,
        description = ["Checks if two contracts are equivalent"])
class CompareCommand : Callable<Void> {
    @Parameters(index = "0", description = ["Contract path"], paramLabel = "<contract path>")
    lateinit var path1: String

    @Parameters(index = "1", description = ["Contract path"], paramLabel = "<contract path>")
    lateinit var path2: String

    @Option(names = ["--host"], description = ["Host"], defaultValue = "localhost")
    var host: String = "127.0.0.1"

    @Option(names = ["--port"], description = ["Port"], defaultValue = "9000")
    var port: Int = 9000


    override fun call(): Void? {
        val successWith1To2 = backwardCompatibleUsingNetwork(path1, path2, host, port)
        val successWith2To1 = backwardCompatibleUsingNetwork(path2, path1, host, port)
        val both = successWith1To2 && successWith2To1

        println()
        println(when {
            both -> "The contracts are mutually backward compatible."
            successWith1To2 -> "$path2 is backward compatible with $path1."
            successWith2To1 -> "$path1 is backward compatible with $path2."
            else -> "The contracts are mutually incompatible."
        })

        return null
    }

    private fun backwardCompatibleUsingNetwork(path1: String, path2: String, host: String, port: Int): Boolean {
        println("### TESTING $path1 => $path2")
        println()

        return try {
            ContractFake(readFile(path2), host, port).use { fake ->
                Contract(readFile(path1)).test(fake)
            }

            true
        }
        catch(e: ContractException) {
            println(resultReport(e.result()))
            false
        }
        catch (contractTestException: Exception) {
            println("""${contractTestException.message?.prependIndent(" ")}
""")
            false
        }
        catch (exception: Throwable) {
            println("Exception (Class=${exception.javaClass.name}, Message=${exception.message ?: exception.localizedMessage})")
            false
        }
    }
}
