package `in`.specmatic.core.git

import org.assertj.core.api.AssertionsForClassTypes.assertThat
import org.junit.jupiter.api.Test

class GitOperationsTest {
    @Test
    fun shouldNotChangeGitRepoUrlWhenThereAreNoEnvVariables() {
        val gitRepositoryURI = "https://gitlab.com/group/project.git"
        val evaluatedGitRepositoryURI =
            evaluateEnvVariablesInGitRepoURI(gitRepositoryURI = gitRepositoryURI, emptyMap<String, String>())
        assertThat(evaluatedGitRepositoryURI).isEqualTo(gitRepositoryURI)
    }

    @Test
    fun shouldChangeGitRepoUrlWhenThereIsOneEnvVariable() {
        val gitRepositoryURI = "https://gitlab-ci-token:${'$'}{CI_JOB_TOKEN}@gitlab.com/group/project.git"
        val evaluatedGitRepositoryURI =
            evaluateEnvVariablesInGitRepoURI(gitRepositoryURI = gitRepositoryURI, mapOf("CI_JOB_TOKEN" to "token"))
        assertThat(evaluatedGitRepositoryURI).isEqualTo("https://gitlab-ci-token:token@gitlab.com/group/project.git")
    }

    @Test
    fun shouldChangeGitRepoUrlWhenThereAreMultipleEnvVariable() {
        val gitRepositoryURI = "https://${'$'}{USER_NAME}:${'$'}{PASSWORD}@gitlab.com/group/project.git"
        val evaluatedGitRepositoryURI =
            evaluateEnvVariablesInGitRepoURI(
                gitRepositoryURI = gitRepositoryURI,
                mapOf("USER_NAME" to "john", "PASSWORD" to "password")
            )
        assertThat(evaluatedGitRepositoryURI).isEqualTo("https://john:password@gitlab.com/group/project.git")
    }
}