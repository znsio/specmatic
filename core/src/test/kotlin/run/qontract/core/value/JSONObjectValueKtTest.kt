package run.qontract.core.value

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test

internal class JSONObjectValueKtTest {
    @Test
    fun `when name exists it should generate a new name`() {
        val newName = UseExampleDeclarations().getNewName("name", listOf("name"))
        assertThat(newName).isEqualTo("name_")
    }

    @Test
    fun `when name does not exist it should return the same name`() {
        val newName = UseExampleDeclarations().getNewName("name", emptyList())
        assertThat(newName).isEqualTo("name")
    }
}