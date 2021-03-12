package run.qontract.core

import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.junit.jupiter.api.Test
import run.qontract.core.pattern.ContractException
import java.io.File

internal class QontractFilePathTest {
    @Test
    fun `reads a qontract file and instantiates a feature file from it`() {
        val actualFeature = QontractFilePath(pathTo("random.qontract")).readFeatureForValue("")
        val expectedFeature = parseGherkinStringToFeature(readTextResource("random.qontract"))
        assertThat(actualFeature).isEqualTo(expectedFeature)
    }

    @Test
    fun `throws an exception when the given path does not exist`() {
        assertThatThrownBy {
            QontractFilePath("doesnotexist.qontract").readFeatureForValue("")
        }.isInstanceOf(ContractException::class.java)
    }

    fun pathTo(path: String): String {
        return javaClass.classLoader.getResource(path)?.file ?: throw Exception("Could not find path $path")
    }

    fun readTextResource(path: String) =
        File(
            javaClass.classLoader.getResource(path)?.file
                ?: throw ContractException("Could not find resource file $path")
        ).readText()
}