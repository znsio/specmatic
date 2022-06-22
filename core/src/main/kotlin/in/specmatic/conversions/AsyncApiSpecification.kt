package `in`.specmatic.conversions

import com.asyncapi.v2.model.AsyncAPI
import com.asyncapi.v2.model.channel.message.Message
import com.asyncapi.v2.model.schema.Schema
import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.dataformat.yaml.YAMLFactory
import `in`.specmatic.core.Feature
import `in`.specmatic.core.Scenario
import `in`.specmatic.core.ScenarioInfo
import `in`.specmatic.core.pattern.Pattern
import `in`.specmatic.core.pattern.StringPattern
import io.cucumber.messages.types.Step
import java.io.File

class AsyncApiSpecification(private val asyncApiFile: String) : IncludedSpecification {
    companion object {
        fun fromFile(asyncApiFilePath: String, relativeTo: String = ""): AsyncApiSpecification {
            val asyncApiFile = File(asyncApiFilePath).let { asyncApiFile ->
                if (asyncApiFile.isAbsolute) {
                    asyncApiFile
                } else {
                    File(relativeTo).canonicalFile.parentFile.resolve(asyncApiFile)
                }
            }

            return fromFile(asyncApiFile.canonicalPath)
        }

        fun fromFile(asyncApiFile: String): AsyncApiSpecification {
            return AsyncApiSpecification(asyncApiFile)
        }
    }

    fun toFeature(): Feature {
        val file = File(asyncApiFile)
        val name = file.name
        val asyncAPI = ObjectMapper(YAMLFactory()).readValue(file.readText(), AsyncAPI::class.java)
        if (asyncAPI.servers!!.any { it.value.protocol != "jms" }) throw UnsupportedOperationException("Only JMS protocol supported")
        val scenarioInfos = toScenarioInfos()
        return Feature(scenarioInfos.map { Scenario(it) }, name = name, path = asyncApiFile)
    }

    val patterns = mutableMapOf<String, Pattern>()

    override fun toScenarioInfos(): List<ScenarioInfo> {
        val file = File(asyncApiFile)
        val name = file.name
        val asyncAPI = ObjectMapper(YAMLFactory()).readValue(file.readText(), AsyncAPI::class.java)
        return asyncAPI.channels.map {
            val topicName = it.key
            val subscription = it.value.subscribe!!
            val subscriptionOperationId = subscription.operationId
            val subscriptionMessage: Message = subscription.message as Message
            val payload: Schema = subscriptionMessage.payload as Schema
            if (payload.type == com.asyncapi.v2.model.schema.Type.STRING) {

            }
            val scenarioInfo = ScenarioInfo(topicName + subscriptionOperationId, async = true, channel = topicName, payload = StringPattern())
            scenarioInfo
        }
    }

    override fun matches(specmaticScenarioInfo: ScenarioInfo, steps: List<Step>): List<ScenarioInfo> {
        TODO("Not yet implemented")
    }
}