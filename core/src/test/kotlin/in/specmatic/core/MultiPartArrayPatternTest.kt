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

    @Test
    fun `it should generate multiple patterns when multiple examples`() {
        val pattern = MultipartArrayPattern("files", ListPattern(BinaryPattern()))
        val example = Row(columnNames = listOf("files_filename"),values = listOf("[\"test1.txt\", \"test2.txt\"]"))

        val newPattern = pattern.newBasedOn(example, Resolver())

        val generated = newPattern.single()?.generate(Resolver()) as List<MultiPartFileValue>
        assertThat(generated.size).isEqualTo(2)
        assertThat(generated[0].filename).isEqualTo("test1.txt")
        assertThat(generated[1].filename).isEqualTo("test2.txt")
    }

    @Test
    fun `it should only one pattern when a single example is set`() {
        val pattern = MultipartArrayPattern("files", ListPattern(BinaryPattern()))
        val example = Row(columnNames = listOf("files_filename"),values = listOf("test3.txt"))

        val newPattern = pattern.newBasedOn(example, Resolver())

        val generated = newPattern.single()?.generate(Resolver()) as List<MultiPartFileValue>
        assertThat(generated.size).isEqualTo(1)
        assertThat(generated.single().filename).isEqualTo("test3.txt")
    }

}