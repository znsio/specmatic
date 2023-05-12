package `in`.specmatic.core.pattern

import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.junit.jupiter.api.Test

class CombinationSpecTest {

  @Test
  fun `error when maxCombinations less than 1`() {
    assertThatThrownBy{ CombinationSpec<Int>(mapOf(), 0) }.hasMessageContaining("must be > 0")
    assertThatThrownBy{ CombinationSpec<Int>(mapOf(), -1) }.hasMessageContaining("must be > 0")
  }

  @Test
  fun `empty list when no candidate sets supplied`() {
    assertThat(CombinationSpec<Long>(mapOf(), 50).selectedCombinations).isEmpty()
  }

  @Test
  fun `combination omitted when candidate set empty`() {
    val spec = CombinationSpec(mapOf("k1" to listOf(), "k2" to listOf(21, 22)), 50)
    assertThat(spec.selectedCombinations).containsExactly(mapOf("k2" to 21), mapOf("k2" to 22),)
  }

  @Test
  fun `produces all combos and orders and prioritized first`() {
    val spec = CombinationSpec<Long>(
      mapOf(
        "k1" to listOf(12, 14),
        "k2" to listOf(25, 22, 28, 27),
        "k3" to listOf(39, 33, 31),
      ),
      50,
    )
    assertThat(spec.selectedCombinations).containsExactly(
      // Prioritized first
      mapOf("k1" to 12, "k2" to 25, "k3" to 39),
      mapOf("k1" to 14, "k2" to 22, "k3" to 33),
      mapOf("k1" to 12, "k2" to 28, "k3" to 31),
      mapOf("k1" to 14, "k2" to 27, "k3" to 39),

      // Remaining combos
//      mapOf("k1" to 12, "k2" to 25, "k3" to 39), // Already included in prioritized list
      mapOf("k1" to 12, "k2" to 25, "k3" to 33),
      mapOf("k1" to 12, "k2" to 25, "k3" to 31),
      mapOf("k1" to 12, "k2" to 22, "k3" to 39),
      mapOf("k1" to 12, "k2" to 22, "k3" to 33),
      mapOf("k1" to 12, "k2" to 22, "k3" to 31),
      mapOf("k1" to 12, "k2" to 28, "k3" to 39),
      mapOf("k1" to 12, "k2" to 28, "k3" to 33),
//      mapOf("k1" to 12, "k2" to 28, "k3" to 31), // Already included in prioritized list
      mapOf("k1" to 12, "k2" to 27, "k3" to 39),
      mapOf("k1" to 12, "k2" to 27, "k3" to 33),
      mapOf("k1" to 12, "k2" to 27, "k3" to 31),
      mapOf("k1" to 14, "k2" to 25, "k3" to 39),
      mapOf("k1" to 14, "k2" to 25, "k3" to 33),
      mapOf("k1" to 14, "k2" to 25, "k3" to 31),
      mapOf("k1" to 14, "k2" to 22, "k3" to 39),
//      mapOf("k1" to 14, "k2" to 22, "k3" to 33), // Already included in prioritized list
      mapOf("k1" to 14, "k2" to 22, "k3" to 31),
      mapOf("k1" to 14, "k2" to 28, "k3" to 39),
      mapOf("k1" to 14, "k2" to 28, "k3" to 33),
      mapOf("k1" to 14, "k2" to 28, "k3" to 31),
//      mapOf("k1" to 14, "k2" to 27, "k3" to 39), // Already included in prioritized list
      mapOf("k1" to 14, "k2" to 27, "k3" to 33),
      mapOf("k1" to 14, "k2" to 27, "k3" to 31),
    )
  }

  @Test
  fun `restricts combos when count too high and orders with prioritized first`() {
    val spec = CombinationSpec<Long>(
      mapOf(
        "k1" to listOf(12, 14),
        "k2" to listOf(25, 22, 28, 27),
        "k3" to listOf(39, 33, 31),
      ),
      23,
    )
    assertThat(spec.selectedCombinations).containsExactly(
      // Prioritized first
      mapOf("k1" to 12, "k2" to 25, "k3" to 39),
      mapOf("k1" to 14, "k2" to 22, "k3" to 33),
      mapOf("k1" to 12, "k2" to 28, "k3" to 31),
      mapOf("k1" to 14, "k2" to 27, "k3" to 39),

      // Remaining combos
//      mapOf("k1" to 12, "k2" to 25, "k3" to 39),
      mapOf("k1" to 12, "k2" to 25, "k3" to 33),
      mapOf("k1" to 12, "k2" to 25, "k3" to 31),
      mapOf("k1" to 12, "k2" to 22, "k3" to 39),
      mapOf("k1" to 12, "k2" to 22, "k3" to 33),
      mapOf("k1" to 12, "k2" to 22, "k3" to 31),
      mapOf("k1" to 12, "k2" to 28, "k3" to 39),
      mapOf("k1" to 12, "k2" to 28, "k3" to 33),
//      mapOf("k1" to 12, "k2" to 28, "k3" to 31), // Already included in prioritized list
      mapOf("k1" to 12, "k2" to 27, "k3" to 39),
      mapOf("k1" to 12, "k2" to 27, "k3" to 33),
      mapOf("k1" to 12, "k2" to 27, "k3" to 31),
      mapOf("k1" to 14, "k2" to 25, "k3" to 39),
      mapOf("k1" to 14, "k2" to 25, "k3" to 33),
      mapOf("k1" to 14, "k2" to 25, "k3" to 31),
      mapOf("k1" to 14, "k2" to 22, "k3" to 39),
//      mapOf("k1" to 14, "k2" to 22, "k3" to 33), // Already included in prioritized list
      mapOf("k1" to 14, "k2" to 22, "k3" to 31),
      mapOf("k1" to 14, "k2" to 28, "k3" to 39),
      mapOf("k1" to 14, "k2" to 28, "k3" to 33),
      mapOf("k1" to 14, "k2" to 28, "k3" to 31),
//      mapOf("k1" to 14, "k2" to 27, "k3" to 39), // Already included in prioritized list
      mapOf("k1" to 14, "k2" to 27, "k3" to 33),
//      mapOf("k1" to 14, "k2" to 27, "k3" to 31), // Omitted to not exceed maxCombinations count
    )
  }

  @Test
  fun `restricts combos even when prioritized count is too high`() {
    val spec = CombinationSpec<Long>(
      mapOf(
        "k1" to listOf(12, 14),
        "k2" to listOf(25, 22, 28, 27),
        "k3" to listOf(39, 33, 31),
      ),
      3,
    )
    assertThat(spec.selectedCombinations).containsExactly(
      // Prioritized first
      mapOf("k1" to 12, "k2" to 25, "k3" to 39),
      mapOf("k1" to 14, "k2" to 22, "k3" to 33),
      mapOf("k1" to 12, "k2" to 28, "k3" to 31),
    )
  }

}
