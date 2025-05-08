package io.specmatic.core.utilities

import io.mockk.*
import io.specmatic.core.git.GitCommand
import io.specmatic.core.git.SystemGit
import io.specmatic.core.git.checkout
import io.specmatic.core.git.clone
import org.junit.jupiter.api.AfterAll
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.io.File

class GitRepoTest {

    companion object {
        @JvmStatic
        @BeforeAll
        fun setup() {
            mockkStatic("io.specmatic.core.git.GitOperations")
            mockkStatic("io.specmatic.core.utilities.Utilities")
        }

        @JvmStatic
        @AfterAll
        fun teardown() {
            unmockkAll()
        }
    }

    @AfterEach
    fun clearMocks() {
        clearAllMocks()
    }

    @Test
    fun `should reset the directory and checkout the correct branch if directory on the wrong branch`(@TempDir tempDir: File) {
        tempDir.resolve("repos").resolve("specmatic").mkdirs()
        val fakeGit = mockk<GitCommand>()
        every { fakeGit.currentBranch() } returns "feature"

        every { getSystemGit(any()) } returns fakeGit
        every { clone(any(), any()) } returns tempDir
        every { checkout(any(), any()) } returns Unit

        val gitRepo = GitRepo(
            gitRepositoryURL = "https://github.com/specmatic/specmatic.git",
            branchName = "main",
            testContracts = emptyList(),
            stubContracts = emptyList(),
            type = null
        )
        gitRepo.loadContracts(workingDirectory = tempDir.canonicalPath, configFilePath = "", selector = { it.stubContracts })

        verify(exactly = 1) { clone(tempDir.resolve("repos"), gitRepo) }
        verify(exactly = 1) { checkout(tempDir,"main") }
    }

    @Test
    fun `should reset when branch does not match origin default while config branch is not set`(@TempDir tempDir: File) {
        tempDir.resolve("repos").resolve("specmatic").mkdirs()
        val fakeGit = mockk<GitCommand>()
        every { fakeGit.currentBranch() } returns "feature"
        every { fakeGit.getOriginDefaultBranchName() } returns "main"

        every { getSystemGit(any()) } returns fakeGit
        every { clone(any(), any()) } returns tempDir
        every { checkout(any(), any()) } returns Unit

        val gitRepo = GitRepo(
            gitRepositoryURL = "https://github.com/specmatic/specmatic.git",
            branchName = null,
            testContracts = emptyList(),
            stubContracts = emptyList(),
            type = null
        )
        gitRepo.loadContracts(workingDirectory = tempDir.canonicalPath, configFilePath = "", selector = { it.stubContracts })

        verify(exactly = 1) { clone(tempDir.resolve("repos"), gitRepo) }
        verify(exactly = 0) { checkout(tempDir,"main") }
    }
}