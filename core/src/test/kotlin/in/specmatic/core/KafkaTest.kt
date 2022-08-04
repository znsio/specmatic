package `in`.specmatic.core

import com.intuit.karate.junit5.Karate
import org.assertj.core.api.Assertions.assertThat
import org.junit.Ignore
import org.junit.jupiter.api.Test
import `in`.specmatic.core.value.KafkaMessage
import `in`.specmatic.core.value.StringValue

class KafkaTest {
    @Karate.Test
    fun karateTest(): Karate {
        return Karate.run("classpath:stub.feature").relativeTo(javaClass)
    }
}
