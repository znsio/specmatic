package `in`.specmatic.core

import com.intuit.karate.junit5.Karate

class KafkaTest {
    @Karate.Test
    fun karateTest(): Karate {
        return Karate.run("classpath:stub.feature").relativeTo(javaClass)
    }
}
