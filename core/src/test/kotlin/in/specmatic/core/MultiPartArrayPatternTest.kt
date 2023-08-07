package `in`.specmatic.core

import `in`.specmatic.core.pattern.BinaryPattern
import `in`.specmatic.core.pattern.ListPattern
import `in`.specmatic.core.pattern.Row
import `in`.specmatic.core.value.BinaryValue
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test


internal class MultiPartArrayPatternTest {

    @Test
    fun `match a part with the containing array` () {
        val value = MultiPartFileValue("files", "file1.txt")
        val pattern = MultipartArrayPattern("files", ListPattern(BinaryPattern()))
        assertThat(pattern.matches(value, Resolver())).isInstanceOf(Result.Success::class.java)
    }

    @Test
    fun `it should generate a new pattern`() {
        val pattern = MultipartArrayPattern("files", ListPattern(BinaryPattern()))
        val newPattern = pattern.newBasedOn(Row(), Resolver())
        assertThat(newPattern.single()).isEqualTo(pattern)
    }

//    fun `it should generate a new pattern for test`() {
//
//    }

}