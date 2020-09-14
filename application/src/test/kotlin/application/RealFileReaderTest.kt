package application

import org.junit.jupiter.api.Test

import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.io.TempDir
import run.qontract.core.QONTRACT_EXTENSION
import java.io.File
import java.nio.file.Path

internal class RealFileReaderTest {

    @Test
    fun `given an existing file, it is found to be a valid file`(@TempDir tempDir: Path) {
        val existentFile = tempDir.resolve("contract.qontract")
        val qontractFilePath = existentFile.toAbsolutePath().toString()
        File(qontractFilePath).writeText("some content")

        val reader = RealFileReader()

        assertTrue(reader.isFile(qontractFilePath))
    }

    @Test
    fun `given an invalid file, it is not found to be a valid file`(@TempDir tempDir: Path) {
        val nonExistentFile = tempDir.resolve("contract.qontract")
        val qontractFilePath = nonExistentFile.toAbsolutePath().toString()

        val reader = RealFileReader()

        assertFalse(reader.isFile(qontractFilePath))
    }

    @Test
    fun `given a contract with a matching extension, it is found to be valid`(@TempDir tempDir: Path) {
        val validExtensionContract = tempDir.resolve("contract.qontract")
        val qontractFilePath = validExtensionContract.toAbsolutePath().toString()

        val reader = RealFileReader()

        assertFalse(reader.extensionIsNot(qontractFilePath, QONTRACT_EXTENSION))
    }

    @Test
    fun `given a contract with a mismatched extension, it is found to be invalid`(@TempDir tempDir: Path) {
        val validExtensionContract = tempDir.resolve("contract.txt")
        val qontractFilePath = validExtensionContract.toAbsolutePath().toString()

        val reader = RealFileReader()

        assertTrue(reader.extensionIsNot(qontractFilePath, QONTRACT_EXTENSION))
    }
}