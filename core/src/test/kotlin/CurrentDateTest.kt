import `in`.specmatic.core.log.CurrentDate
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import java.util.*

internal class CurrentDateTest {
    private val date = CurrentDate(Calendar.Builder().let {
        it.setDate(2000, 10, 20)
        it.setTimeOfDay(10, 20, 30, 40)
    }.build())

    @Test
    fun `file name string`() {
        assertThat(date.toFileNameString()).isEqualTo("2000-11-20-10-20-30")
    }

    @Test
    fun `log string`() {
        assertThat(date.toLogString()).isEqualTo("2000-11-20 10:20:30.40")
    }
}