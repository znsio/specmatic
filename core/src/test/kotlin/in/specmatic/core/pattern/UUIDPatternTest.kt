package `in`.specmatic.core.pattern

import `in`.specmatic.GENERATION
import `in`.specmatic.core.Resolver
import `in`.specmatic.core.Result
import `in`.specmatic.core.pattern.UUIDPattern.newBasedOnR
import `in`.specmatic.core.value.StringValue
import `in`.specmatic.shouldMatch
import `in`.specmatic.shouldNotMatch
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Tag
import org.junit.jupiter.api.Test
import java.util.UUID

internal class UUIDPatternTest {
    @Test
    fun `should parse a valid UUIDvalue`() {
        val uuidString = UUID.randomUUID().toString()
        val uuidValue = UUIDPattern.parse(uuidString, Resolver())

        assertEquals(uuidString, uuidValue.string)
    }

    @Test
    fun `should generate a UUIDvalue which can be parsed`() {
        val valueGenerated = UUIDPattern.generate(Resolver())
        val valueParsed = UUIDPattern.parse(valueGenerated.string, Resolver())

        assertEquals(valueGenerated, valueParsed)
    }

    @Test
    fun `should match a valid UUIDvalue`() {
        val valueGenerated = UUIDPattern.generate(Resolver())
        valueGenerated shouldMatch UUIDPattern
    }

    @Test
    fun `should fail to match an invalid UUIDvalue`() {
        val valueGenerated = StringValue("this is not a UUID value")
        valueGenerated shouldNotMatch UUIDPattern
    }

    @Test
    fun `should return itself when generating a new pattern based on a row`() {
        val uuidPatterns = newBasedOnR(Row(), Resolver()).map { it.value }.toList()
        assertEquals(1, uuidPatterns.size)
        assertEquals(UUIDPattern, uuidPatterns.first())
    }

    @Test
    fun `should match this UUIDtime format`() {
        val uuid1 = StringValue("cfb64879-6e10-47f6-a824-a7606a36d423")
        val uuid2 = StringValue("3b45392f-3e4d-440e-b680-2ab673a197a6")

        assertThat(UUIDPattern.matches(uuid1, Resolver())).isInstanceOf(Result.Success::class.java)
        assertThat(UUIDPattern.matches(uuid2, Resolver())).isInstanceOf(Result.Success::class.java)
    }

    @Test
    @Tag(GENERATION)
    fun `negative values should be generated`() {
        val result = UUIDPattern.negativeBasedOn(Row(), Resolver()).map { it.value }.toList()
        assertThat(result.map { it.typeName }).containsExactlyInAnyOrder(
            "null",
        )
    }
}
