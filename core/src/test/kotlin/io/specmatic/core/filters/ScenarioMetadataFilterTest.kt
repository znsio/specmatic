import io.specmatic.core.filters.*
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test
import java.util.regex.Pattern

class ScenarioMetadataFilterTests {

    private fun createScenarioMetadata(
        method: String = "GET",
        path: String = "/default",
        statusCode: Int = 200
    ): ScenarioMetadata {
        return ScenarioMetadata(
            method = method,
            path = path,
            statusCode = statusCode,
            header = emptySet(),
            query = emptySet(),
            exampleName = "example"
        )
    }

    @Test
    fun `filter by PATH and METHOD`() {
        val filter = ScenarioMetadataFilter.from("PATH=/products && METHOD=GET,POST")

        val metadata1 = createScenarioMetadata(method = "GET", path = "/products")
        val metadata2 = createScenarioMetadata(method = "POST", path = "/products")
        val metadata3 = createScenarioMetadata(method = "PUT", path = "/products")
        val metadata4 = createScenarioMetadata(method = "GET", path = "/users")

        assertTrue(filter.isSatisfiedBy(metadata1))
        assertTrue(filter.isSatisfiedBy(metadata2))
        assertFalse(filter.isSatisfiedBy(metadata3))
        assertFalse(filter.isSatisfiedBy(metadata4))
    }

    @Test
    fun `filter by STATUS 200 or 400`() {
        val filter = ScenarioMetadataFilter.from("STATUS=200,400")

        val metadata1 = createScenarioMetadata(statusCode = 200)
        val metadata2 = createScenarioMetadata(statusCode = 400)
        val metadata3 = createScenarioMetadata(statusCode = 500)

        assertTrue(filter.isSatisfiedBy(metadata1))
        assertTrue(filter.isSatisfiedBy(metadata2))
        assertFalse(filter.isSatisfiedBy(metadata3))
    }

    @Test
    fun `filter by STATUS 2xx`() {
        val filter = ScenarioMetadataFilter.from("STATUS=2xx")

        val metadata1 = createScenarioMetadata(statusCode = 200)
        val metadata2 = createScenarioMetadata(statusCode = 201)
        val metadata3 = createScenarioMetadata(statusCode = 500)

        assertTrue(filter.isSatisfiedBy(metadata1))
        assertTrue(filter.isSatisfiedBy(metadata2))
        assertFalse(filter.isSatisfiedBy(metadata3))
    }

    @Test
    fun `filter by METHOD not GET and PATH not users`() {
        val filter = ScenarioMetadataFilter.from("METHOD!=GET && PATH!=/users")
        val metadata1 = createScenarioMetadata(method = "POST", path = "/products")
        val metadata2 = createScenarioMetadata(method = "GET", path = "/products")
        val metadata3 = createScenarioMetadata(method = "POST", path = "/users")

        assertTrue(filter.isSatisfiedBy(metadata1))
        assertFalse(filter.isSatisfiedBy(metadata2))
        assertFalse(filter.isSatisfiedBy(metadata3))
    }

    @Test
    fun `filter by STATUS not 200 or 400`() {
        val filter = ScenarioMetadataFilter.from("STATUS!=200,400")
        val metadata1 = createScenarioMetadata(statusCode = 500)
        val metadata2 = createScenarioMetadata(statusCode = 200)
        val metadata3 = createScenarioMetadata(statusCode = 400)

        assertTrue(filter.isSatisfiedBy(metadata1))
        assertFalse(filter.isSatisfiedBy(metadata2))
        assertFalse(filter.isSatisfiedBy(metadata3))
    }

    @Test
    fun `complex filter with OR`() {
        val filter = ScenarioMetadataFilter.from("PATH=/products || METHOD=POST")
        val metadata1 = createScenarioMetadata(method = "GET", path = "/products")
        val metadata2 = createScenarioMetadata(method = "POST", path = "/users")
        val metadata3 = createScenarioMetadata(method = "PUT", path = "/users")

        assertTrue(filter.isSatisfiedBy(metadata1))
        assertTrue(filter.isSatisfiedBy(metadata2))
        assertFalse(filter.isSatisfiedBy(metadata3))
    }

    @Test
    fun `exclude scenarios with STATUS 202`() {
        val filter = ScenarioMetadataFilter.from("STATUS!=202")
        val metadata1 = createScenarioMetadata(statusCode = 200)
        val metadata2 = createScenarioMetadata(statusCode = 202)
        val metadata3 = createScenarioMetadata(statusCode = 400)

        assertTrue(filter.isSatisfiedBy(metadata1))
        assertFalse(filter.isSatisfiedBy(metadata2))
        assertTrue(filter.isSatisfiedBy(metadata3))
    }

    @Test
    fun `exclude scenarios by example name with exact match`() {
        val filter = ScenarioMetadataFilter.from("PATH!=/hub,/hub/(id:string)")
        val metadata1 = createScenarioMetadata(path = "/hub")
        val metadata2 = createScenarioMetadata(path = "/hub/(id:string)")
        val metadata3 = createScenarioMetadata(path = "/users")

        assertFalse(filter.isSatisfiedBy(metadata1))
        assertFalse(filter.isSatisfiedBy(metadata2))
        assertTrue(filter.isSatisfiedBy(metadata3))
    }

    @Test
    fun `exclude scenarios by list of status codes`() {
        val filter = ScenarioMetadataFilter.from("STATUS!=202,50,401,403,405")
        val metadata1 = createScenarioMetadata(statusCode = 202)
        val metadata2 = createScenarioMetadata(statusCode = 50)
        val metadata3 = createScenarioMetadata(statusCode = 201)

        assertFalse(filter.isSatisfiedBy(metadata1))
        assertFalse(filter.isSatisfiedBy(metadata2))
        assertTrue(filter.isSatisfiedBy(metadata3))
    }

    @Test
    fun `exclude scenarios with STATUS not in a list`() {
        val filter = ScenarioMetadataFilter.from("STATUS!=202,401,403")
        val metadata1 = createScenarioMetadata(statusCode = 200)
        val metadata2 = createScenarioMetadata(statusCode = 401)
        val metadata3 = createScenarioMetadata(statusCode = 202)

        assertTrue(filter.isSatisfiedBy(metadata1))
        assertFalse(filter.isSatisfiedBy(metadata2))
        assertFalse(filter.isSatisfiedBy(metadata3))
    }

    @Test
    fun `exclude scenarios with combined STATUS and path conditions`() {
        val filter = ScenarioMetadataFilter.from("STATUS!=202 && PATH!=/hub,/hub/(id:string)")
        val metadata1 = createScenarioMetadata(statusCode = 200, path = "/users")
        val metadata2 = createScenarioMetadata(statusCode = 202, path = "/users")
        val metadata3 = createScenarioMetadata(statusCode = 200, path = "/hub")
        val metadata4 = createScenarioMetadata(statusCode = 202, path = "/hub")

        assertTrue(filter.isSatisfiedBy(metadata1))
        assertFalse(filter.isSatisfiedBy(metadata2))
        assertFalse(filter.isSatisfiedBy(metadata3))
        assertFalse(filter.isSatisfiedBy(metadata4))
    }

}
