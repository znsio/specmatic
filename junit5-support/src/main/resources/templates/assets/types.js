/**
 * ScenarioData Object, contains all data for a single scenario
 * @typedef {Object} ScenarioData
 * @property {string} name - Name of the scenario
 * @property {string} baseUrl - URL of the System Under Test
 * @property {number} duration - Duration of the test
 * @property {string} testResult - Result of the test (Remark)
 * @property {boolean} valid - Whether the Scenario is valid
 * @property {boolean} wip - Whether the Scenario is WIP
 * @property {string} request - Request sent to the System Under Test
 * @property {number} requestTime - Timestamp of the request
 * @property {string} response - Response received from the System Under Test
 * @property {number} responseTime - Timestamp of the response
 * @property {string} specFileName - Name of the specifications file
 * @property {string} details - Details of the test produced by Specmatic
 * @property {string} htmlResult - HTML result of the test one of: "Success", "Failed", "Error", "Skipped"
 */

/** SelectedRow Object, contains information about a selected row
 * @typedef {Object} SelectedRow
 * @property {number} coverage - Coverage of the row
 * @property {Array<GroupColumn>} groupValues - Column Groups
 * @property {number} exercisedCount - Number of exercised tests
 * @property {string} result - Result of the test (REMARK)
 * @property {string} color - Color of the result one of: "red", "yellow", "green"
 * @property {string} type = Html Result one of: "Success", "Failed", "Error", "Skipped"
 */

/** GroupColumn Object, contains information about a group column
 * @typedef {Object} GroupColumn
 * @property {string} name - Name of the column
 * @property {string} value - Values of the column
*/


/**
 * ScenarioGroup Object, contains information about a group of scenarios
 * @typedef {Object} ScenarioGroup
 * @property {Array<ScenarioData>} data - Scenarios in the group
 * @property {Map<string, ScenarioGroup>} subGroup - Subgroups of the group
 */

/**
 * TestCount Object, contains information about the number of tests in each group
 * @typedef {Object} TestCount
 * @property {number} Success - Number of successful tests
 * @property {number} Failed - Number of failed tests
 * @property {number} Error - Number of tests with errors
 * @property {number} Skipped - Number of skipped tests
 * @property {number} All - Total number of tests
 */

/**
 * Represents the structure of grouped categories.
 * @typedef {Object} Categories
 * @property {Object} Success - Grouped items for "Success" type.
 * @property {Object} Error - Grouped items for "Error" type.
 * @property {Object} Failed - Grouped items for "Failed" type.
 * @property {Object} Skipped - Grouped items for "Skipped" type.
 * @property {Object} All - Grouped items for "All" types.
 */

/**
 * Represents the structure of grouped columns.
 * @typedef {Object} GroupedRowColumns
 * @property {GroupColumn} firstGroup
 * @property {Array<GroupColumn>} restGroups
 */


/**
 * Represents the structure of grouped rows.
 * @typedef {Object} GroupedRowTds
 * @property {Array<HTMLTableCellElement>} childrenTds
 * @property {Array<HTMLTableCellElement>} restGroupTds
 * @property {Array<HTMLTableCellElement>} restTds
 */
