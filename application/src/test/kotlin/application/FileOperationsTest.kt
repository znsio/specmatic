package application

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.ValueSource
import `in`.specmatic.core.LEGACY_CONTRACT_EXTENSION
import `in`.specmatic.core.CONTRACT_EXTENSION
import `in`.specmatic.core.CONTRACT_EXTENSIONS
import java.io.File
import java.nio.file.Path

internal class FileOperationsTest {
    @Test
    fun `given an existing file, it is found to be a valid file`(@TempDir tempDir: Path) {
        val existentFile = tempDir.resolve("contract.$CONTRACT_EXTENSION")
        val qontractFilePath = existentFile.toAbsolutePath().toString()
        File(qontractFilePath).writeText("some content")

        val reader = FileOperations()

        assertThat(reader.isFile(qontractFilePath)).isTrue
    }

    @Test
    fun `given an invalid file, it is not found to be a valid file`(@TempDir tempDir: Path) {
        val nonExistentFile = tempDir.resolve("contract.$CONTRACT_EXTENSION")
        val qontractFilePath = nonExistentFile.toAbsolutePath().toString()

        val reader = FileOperations()

        assertThat(reader.isFile(qontractFilePath)).isFalse
    }

    @ParameterizedTest
    @ValueSource(strings = [CONTRACT_EXTENSION, LEGACY_CONTRACT_EXTENSION])
    fun `given a contract with a matching extension, it is found to be valid`(extension: String, @TempDir tempDir: Path) {
        val validExtensionContract = tempDir.resolve("contract.$extension")
        val qontractFilePath = validExtensionContract.toAbsolutePath().toString()

        val reader = FileOperations()

        assertThat(reader.extensionIsNot(qontractFilePath, CONTRACT_EXTENSIONS)).isFalse
    }

    @Test
    fun `given a contract with a mismatched extension, it is found to be invalid`(@TempDir tempDir: Path) {
        val validExtensionContract = tempDir.resolve("contract.txt")
        val qontractFilePath = validExtensionContract.toAbsolutePath().toString()

        val reader = FileOperations()

        assertThat(reader.extensionIsNot(qontractFilePath, CONTRACT_EXTENSIONS)).isTrue
    }
}
