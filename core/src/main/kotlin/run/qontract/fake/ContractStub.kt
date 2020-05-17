package run.qontract.fake

import run.qontract.core.QontractKafka
import java.io.Closeable

interface ContractStub : Closeable {
    fun getKafkaInstance(): QontractKafka?
}
