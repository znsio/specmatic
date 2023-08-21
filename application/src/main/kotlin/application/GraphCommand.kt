package application

import `in`.specmatic.core.azure.AzureAPI
import `in`.specmatic.core.azure.PersonalAccessToken
import `in`.specmatic.core.git.getPersonalAccessToken
import `in`.specmatic.core.loadSpecmaticJsonConfig
import `in`.specmatic.core.log.CompositePrinter
import `in`.specmatic.core.log.Verbose
import `in`.specmatic.core.log.logger
import `in`.specmatic.core.pattern.ContractException
import `in`.specmatic.core.utilities.exitWithMessage
import picocli.CommandLine
import picocli.CommandLine.Option
import java.util.concurrent.Callable

@CommandLine.Command(name = "graph",
    mixinStandardHelpOptions = true,
    description = ["Dependency graph"])
class GraphCommand: Callable<Unit> {
    @CommandLine.Command(
        name = "consumers",
        description = ["Display a list of services depending on contracts in this repo"]
    )
    fun consumers(
        @Option(names = ["--verbose"], description = ["Print verbose logs"]) verbose: Boolean = false,
        @Option(
            names = ["--azureBaseURL"],
            description = ["Azure base URL"],
            required = true
        ) azureBaseURL: String
    ) {
        if (verbose)
            logger = Verbose(CompositePrinter())

        val configJson = loadSpecmaticJsonConfig(null)

        val azureAuthToken = PersonalAccessToken(
            getPersonalAccessToken() ?: throw ContractException(
                "Access token not found, put it in ${
                    System.getProperty(
                        "user.home"
                    )
                }/specmatic.json"
            )
        )

        val repository = configJson.repository
            ?: exitWithMessage(
                """specmatic.json needs to contain a the repository information, as below:
                    |{
                    |  "repository": {
                    |    "provider": "azure"
                    |    "collectionName": "NameOfTheCollectionContainingThisProject"
                    |  }
                    |}
                """.trimMargin()
            )

        val collection = repository.collectionName
        val azure = AzureAPI(azureAuthToken, azureBaseURL, collection)

        logger.log("Dependency projects")
        logger.log("-------------------")

        configJson.sources.forEach { source ->
            logger.log("In central repo ${source.repository}")

            source.test?.forEach { relativeContractPath ->
                logger.log("  Consumers of $relativeContractPath")
                val consumers = azure.referencesToContract(relativeContractPath)

                if (consumers.isEmpty()) {
                    logger.log("    ** no consumers found **")
                } else {
                    consumers.forEach {
                        logger.log("  - ${it.description}")
                    }
                }

                logger.newLine()
            }
        }
    }

    override fun call() {
    }

}