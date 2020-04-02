package run.qontract.core.pattern

import run.qontract.core.Resolver
import run.qontract.core.value.NumberValue
import org.junit.jupiter.api.Test
import run.qontract.core.Result
import run.qontract.core.value.StringValue
import org.assertj.core.api.Assertions.assertThat
import run.qontract.core.shouldNotMatch
import run.qontract.core.value.NullValue
import java.util.*

internal class NumericStringPatternTest {
    @Test
    fun `should match Number Value`() {
        NumericStringPattern().matches(NumberValue(123), Resolver()).let {
            assertThat(it is Result.Success).isTrue()
        }
    }

    @Test
    fun `should match String Value that represents a number`() {
        NumericStringPattern().matches(StringValue("123"), Resolver()).let {
            assertThat(it is Result.Success).isTrue()
        }
    }

    @Test
    fun `should not match String Value that is not a number`() {
        NumericStringPattern().matches(StringValue("text"), Resolver()).let {
            assertThat(it is Result.Success).isFalse()
            assertThat((it as Result.Failure).stackTrace()).isEqualTo(Stack<String>().also { stack ->
                stack.push(""""text" is not a Number""")
            })
        }
    }

    @Test
    fun `should fail to match nulls gracefully`() {
        NullValue shouldNotMatch NumberTypePattern()
    }
}
