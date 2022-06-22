package `in`.specmatic.stub

import `in`.specmatic.core.Feature
import org.apache.activemq.ActiveMQConnectionFactory
import org.apache.activemq.broker.BrokerService
import org.apache.commons.lang3.RandomUtils
import javax.jms.*

class JMSStub(
    private val features: List<Feature>,
    private val host: String = "127.0.0.1",
    private val port: Int = 61616,
) {
    fun brokerUrl(): String = "tcp://${host}:${port}"

    constructor(feature: Feature) : this(features = listOf(feature))

    var broker = BrokerService().also {
        it.addConnector(brokerUrl())
        println("starting JMS Broker Service")
        it.start()
        it.waitUntilStarted()
        println("JMS Broker Service Started")
    }

    var factory = ActiveMQConnectionFactory().also { it.brokerURL = brokerUrl() }

    var connection: TopicConnection = factory.createTopicConnection().also {
        it.clientID = "ClientId ${RandomUtils.nextInt()}"
        it.start()
    }

    var session = connection.createSession(
        true,
        Session.AUTO_ACKNOWLEDGE
    )

//    var destination: Topic = session.createTopic("mailbox")

    var destinations: List<Topic> = features.map { feature ->
        feature.scenarios.filter { it.async }.map {
            session.createTopic(it.channel)
        }
    }.flatten()

//    var consumer: MessageConsumer = session.createConsumer(destination)

    var consumers: List<MessageConsumer> = destinations.map { destination ->
        session.createConsumer(destination).also {
            it.setMessageListener {
                println("${it.jmsDestination} ${it}")
            }
        }
    }
}