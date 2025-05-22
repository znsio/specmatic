package io.specmatic.conversions

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test

class PathTreeTest {

    @Test
    fun `should be able to form the required tree-structure from paths of OAS`() {
        val paths = setOf(
            "/test/{testId}",
            "/test/latest",
            "/reports/{testId}/latest",
            "/{testId}/reports/{reportId}"
        )
        val pathTree = PathTree.from(paths.associateWith { null })
        val pathTreeMap = pathTree.asMap()

        assertThat(pathTreeMap).isEqualTo(mapOf(
            "/" to mapOf(
                "reports" to mapOf(
                    "{testId}" to mapOf(
                        "latest" to emptyMap<String, Any>()
                    )
                ),
                "test" to mapOf(
                    "latest" to emptyMap<String, Any>(),
                    "{testId}" to emptyMap<String, Any>()
                ),
                "{testId}" to mapOf(
                    "reports" to mapOf(
                        "{reportId}" to emptyMap<String, Any>()
                    )
                )
            )
        ))
    }

    @Test
    fun `should return the conflicting segments for a given path`() {
        val paths = setOf(
            "/test/{testId}",
            "/test/latest",
            "/reports/{testId}/latest",
            "/{testId}/reports/{reportId}"
        )
        val pathTree = PathTree.from(paths.associateWith { null })
        val conflicts = pathTree.conflictsFor("/test/{testId}")

        assertThat(conflicts).containsExactly("latest")
    }

    @Test
    fun `conflicts should not contain dynamic path segments and duplicates`() {
        val paths = setOf(
            "/test/{testId}",
            "/test/latest",
            "/test/{sameId}/reports",
            "/test/latest/reports",
        )
        val pathTree = PathTree.from(paths.associateWith { null })
        val conflicts = pathTree.conflictsFor("/test/{testId}")

        assertThat(conflicts).containsExactly("latest")
    }
}