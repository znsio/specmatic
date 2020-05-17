package run.qontract.core.kafka

import kafka.metrics.KafkaMetricsReporter
import kafka.server.KafkaConfig
import kafka.server.KafkaServer
import org.apache.kafka.common.utils.Time
import org.apache.zookeeper.server.ServerCnxnFactory
import org.apache.zookeeper.server.ZooKeeperServer
import scala.Option
import java.io.File

class KafkaNode {
    init {
        val temp = File("temp")
        temp.mkdir()

        val tickTime = 2000
        val numOfConnections = 10

        val zooKeeperServer = ZooKeeperServer(temp, temp, tickTime)
        val standaloneServerFactory = ServerCnxnFactory.createFactory(0, numOfConnections)
        val zkPort = standaloneServerFactory.localPort

        standaloneServerFactory.startup(zooKeeperServer)

        val kafkaConfig = mapOf("zookeeper.connect" to "localhost:$zkPort")

        val emptyScalaSeq = scala.jdk.javaapi.CollectionConverters.asScala(emptyList<KafkaMetricsReporter>()).toSeq()

        val kafkaServer = KafkaServer(KafkaConfig(kafkaConfig), Time.SYSTEM, Option.apply(null), emptyScalaSeq)

        kafkaServer.startup()
    }
}
