package `in`.specmatic.test

import org.junit.jupiter.api.DynamicTest
import org.junit.jupiter.api.TestFactory
import java.util.stream.Stream

interface SpecmaticContractTest {

    @TestFactory
    fun contractTest(): Stream<DynamicTest> {
        return SpecmaticJUnitSupport().contractTest()
    }
}