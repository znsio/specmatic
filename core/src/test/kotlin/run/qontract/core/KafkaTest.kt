package run.qontract.core

import com.intuit.karate.junit5.Karate
import org.assertj.core.api.Assertions.assertThat
import org.junit.Ignore
import org.junit.jupiter.api.Test
import run.qontract.core.value.KafkaMessage
import run.qontract.core.value.StringValue

class KafkaTest {
    @Karate.Test
    fun karateTest(): Karate {
        return Karate().relativeTo(javaClass).feature("classpath:mock.feature")
    }
}
