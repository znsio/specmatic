package application.example

import picocli.CommandLine.Option
import java.io.File

class ValidateCommandOptions {
    @Option(
        names= ["--filter"],
        description = [
            """
Filter tests matching the specified filtering criteria

You can filter tests based on the following keys:
- `METHOD`: HTTP methods (e.g., GET, POST)
- `PATH`: Request paths (e.g., /users, /product)
- `STATUS`: HTTP response status codes (e.g., 200, 400)
- `HEADERS`: Request headers (e.g., Accept, X-Request-ID)
- `QUERY-PARAM`: Query parameters (e.g., status, productId)
- `EXAMPLE-NAME`: Example name (e.g., create-product, active-status)

To specify multiple values for the same filter, separate them with commas. 
For example, to filter by HTTP methods: 
--filter="METHOD=GET,POST"
           """
        ],
        required = false
    )
    var filter: String = ""

    @Option(names = ["--contract-file", "--spec-file"], description = ["Contract file path"], required = false)
    var contractFile: File? = null

    @Option(names = ["--example-file"], description = ["Example file path"], required = false)
    val exampleFile: File? = null

    @Option(names = ["--examples-dir"], description = ["External examples directory path for a single API specification (If you are not following the default naming convention for external examples directory)"], required = false)
    val examplesDir: File? = null

    @Option(names = ["--specs-dir"], description = ["Directory with the API specification files"], required = false)
    val specsDir: File? = null

    @Option(
        names = ["--examples-base-dir"],
        description = ["Base directory which contains multiple external examples directories each named as per the Specmatic naming convention to associate them with the corresponding API specification"],
        required = false
    )
    val examplesBaseDir: File? = null

    @Option(names = ["--debug"], description = ["Debug logs"])
    var verbose = false

    @Option(
        names = ["--filter-name"],
        description = ["Validate examples of only APIs with this value in their name"],
        defaultValue = "\${env:SPECMATIC_FILTER_NAME}",
        hidden = true
    )
    var filterName: String = ""

    @Option(
        names = ["--filter-not-name"],
        description = ["Validate examples of only APIs which do not have this value in their name"],
        defaultValue = "\${env:SPECMATIC_FILTER_NOT_NAME}",
        hidden = true
    )
    var filterNotName: String = ""

    @Option(
        names = ["--examples-to-validate"],
        description = ["Whether to validate inline, external, or both examples. Options: INLINE, EXTERNAL, BOTH"],
        converter = [ExamplesToValidateConverter::class],
        defaultValue = "BOTH"
    )
    var examplesToValidate: ExamplesToValidate = ExamplesToValidate.BOTH

    fun withUpdated(specFile: File): ValidateCommandOptions {
        this.contractFile = specFile
        return this
    }

    fun withUpdated(examplesToValidate: ExamplesToValidate): ValidateCommandOptions {
        this.examplesToValidate = examplesToValidate
        return this
    }
}
