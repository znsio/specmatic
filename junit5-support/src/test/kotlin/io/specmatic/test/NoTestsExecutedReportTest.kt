package io.specmatic.test

import io.specmatic.core.*
import io.specmatic.test.reports.OpenApiCoverageReportProcessor
import io.specmatic.test.reports.coverage.OpenApiCoverageReportInput
import io.specmatic.test.reports.coverage.console.OpenAPICoverageConsoleReport
import io.specmatic.test.reports.coverage.console.OpenApiCoverageConsoleRow
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Assertions.assertEquals
import java.io.File

class NoTestsExecutedReportTest {

    private val tempReportDir = System.getProperty("java.io.tmpdir") + "/specmatic-test"
    private lateinit var specmaticConfig: SpecmaticConfig

    @BeforeEach
    fun setup() {
        // Clear any previous system property
        System.clearProperty("specmatic.exitCode")
        
        // Create temp report directory
        val reportDir = File(tempReportDir)
        reportDir.mkdirs()
        
        // Create a config with report details
        val reportTypes = ReportTypes(
            apiCoverage = APICoverage(
                openAPI = APICoverageConfiguration(
                    successCriteria = SuccessCriteria(
                        minThresholdPercentage = 0,
                        maxMissedEndpointsInSpec = 0,
                        enforce = true
                    )
                )
            )
        )
        
        val reportConfig = ReportConfigurationDetails(
            formatters = listOf(
                ReportFormatterDetails(
                    type = ReportFormatterType.HTML,
                    outputDirectory = tempReportDir
                )
            ),
            types = reportTypes
        )
        
        // Create specmatic config with report configuration
        specmaticConfig = SpecmaticConfig(
            report = reportConfig
        )
    }
    
    @AfterEach
    fun tearDown() {
        // Clean up test directory
        File(tempReportDir).deleteRecursively()
        System.clearProperty("specmatic.exitCode")
        System.clearProperty("specmatic.exitWithErrorOnNoTests")
    }

    @Test
    fun `should still generate report when no tests are executed`() {
        // Given
        // Create a config without success criteria enforcement
        val noEnforceReportTypes = ReportTypes(
            apiCoverage = APICoverage(
                openAPI = APICoverageConfiguration(
                    successCriteria = SuccessCriteria(
                        minThresholdPercentage = 0,
                        maxMissedEndpointsInSpec = 0,
                        enforce = false
                    )
                )
            )
        )
        
        val noEnforceReportConfig = ReportConfigurationDetails(
            formatters = listOf(
                ReportFormatterDetails(
                    type = ReportFormatterType.HTML,
                    outputDirectory = tempReportDir
                )
            ),
            types = noEnforceReportTypes
        )
        
        val testConfig = SpecmaticConfig(report = noEnforceReportConfig)
        val reportInput = OpenApiCoverageReportInput("/path/to/config")
        val processor = OpenApiCoverageReportProcessor(reportInput)
        
        // When
        processor.process(testConfig)
        
        // Then
        val reportFile = File(tempReportDir, "index.html")
        assertTrue(reportFile.exists(), "Report file should be generated even when no tests run")
    }
    
    @Test
    fun `should set exit code to failure when no tests run`() {
        // Given
        System.clearProperty("specmatic.exitCode")
        val reportInput = OpenApiCoverageReportInput("/path/to/config")
        val processor = OpenApiCoverageReportProcessor(reportInput)
        val report = OpenAPICoverageConsoleReport(
            emptyList(), 
            emptyList(), 
            0, 0, 0, 0, 0
        )
        
        // When
        try {
            processor.assertSuccessCriteria(specmaticConfig.getReport()!!, report)
        } catch (e: AssertionError) {
            // Expected to fail with assertion error
        }
        
        // Then
        assertEquals("1", System.getProperty("specmatic.exitCode"), "Exit code should be set to failure")
    }
    
    @Test
    fun `should not set exit code to failure when configured not to`() {
        // Given
        System.clearProperty("specmatic.exitCode")
        System.setProperty("specmatic.exitWithErrorOnNoTests", "false")
        val reportInput = OpenApiCoverageReportInput("/path/to/config")
        val processor = OpenApiCoverageReportProcessor(reportInput)
        val report = OpenAPICoverageConsoleReport(
            emptyList(), 
            emptyList(), 
            0, 0, 0, 0, 0
        )
        
        // When
        try {
            processor.assertSuccessCriteria(specmaticConfig.getReport()!!, report)
        } catch (e: AssertionError) {
            // Expected to fail with assertion error
        }
        
        // Then
        assertEquals(null, System.getProperty("specmatic.exitCode"), "Exit code should not be set when configured not to")
    }
}