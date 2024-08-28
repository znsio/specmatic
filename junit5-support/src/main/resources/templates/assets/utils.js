/**
 * Reads JSON data from script
 * @param {HTMLScriptElement} scriptElement
 * @returns {import("./types").ScenarioGroup}
 */
function readJsonDataFromScript(scriptElement) {
    const jsonData = scriptElement.textContent;
    return JSON.parse(jsonData || "{}");
}

/**
 * Go back to table, empty scenario list
 * @param {number} timeoutToEmpty - Timeout to empty scenario list
 * @param {boolean} scrollBack - Whether to scroll back to saved position
 */
function goBackToTable(timeoutToEmpty = 500, scrollBack = true) {
    setTimeout(() => {
        // @ts-ignore DOM Element in index
        scenariosList.replaceChildren();
    }, timeoutToEmpty);

    // @ts-ignore DOM Element in index
    mainElement.setAttribute("data-panel", "table");

    if (scrollBack) {
        // @ts-ignore scrollY in index
        window.scrollTo(0, scrollYPosition);
    }
}

/**
 * Extract values from selected HTMLTableRow
 * @param {HTMLTableRowElement} row - HTMLTableRowElement to extract data from
 * @returns {import("./types").SelectedRow}
 */
function extractRowValues(row) {
    /** @type {Array<string>} */
    // @ts-ignore Will be array of strings
    const extractedValues = [...row.children].map((child) => child.textContent);

    /** @type {Array<import("./types").GroupColumn>} */
    // @ts-ignore Will be array of objects
    const groups = [...row.children].slice(1, -2).map((group) => {
        return { name: group.getAttribute("data-colName"), value: group.textContent?.trim() };
    });

    return {
        coverage: Number.parseInt(extractedValues[0].trim().slice(0, -1)),
        groupValues: groups,
        // @ts-ignore
        exercisedCount: Number.parseInt(extractedValues.at(-2)),
        // @ts-ignore
        result: extractedValues.at(-1).trim(),
        // @ts-ignore
        color: row.getAttribute("data-color"),
        // @ts-ignore
        type: row.getAttribute("data-type"),
    };
}



/**
 * Create a span with key and value, to be used in a details element
 * @param {string} key - key to be displayed
 * @param {string} value - value to be displayed
 * @returns {Array<HTMLSpanElement>} - Array with two elements: keySpan and valueSpan
 */
function createKeyValueSpan(key, value) {
    const keySpan = document.createElement("span");
    const valueSpan = document.createElement("span");

    keySpan.textContent = key;
    valueSpan.textContent = value;

    return [keySpan, valueSpan];
}

/**
 * Get all scenarios based on group values
 * @param {Array<import("./types").GroupColumn>} groupValues - Array of group values
 * @returns {Array<import("./types").ScenarioData>}
 */
function getScenarios(groupValues) {
    /** @type {import("./types").ScenarioGroup} */
    // @ts-ignore SCENARIOS in index
    let scenarios = SCENARIOS;

    for (const group of groupValues) {
        // @ts-ignore value will be in scenarios.subGroup
        scenarios = scenarios.subGroup[group.value];
    }

    return scenarios.data;
}

/**
 * Create a paragraph element with a span containing the given text
 * @param {string} title - Title of the span
 * @param {string} value - Text to be contained in the span
 * @returns {HTMLParagraphElement} - Created paragraph element
 */
function createParagraphWithSpan(title, value) {
    const p = document.createElement("p");
    const span = document.createElement("span");
    span.textContent = value;
    p.textContent = `${title}: `;
    p.appendChild(span);
    return p;
}

/**
 * Convert an epoch time to a ISO String
 * @param {number} epoch - The time in epoch
 * @returns {string} ISO String representation of the time
 */
function epochToDateTime(epoch) {
    if (epoch === 0) return "N/A";
    return new Date(epoch).toISOString();
}

/**
 * Returns a color string based on the result of a scenario
 * @param {string} result - Result of a scenario
 * @returns {string} Color string, one of "red", "yellow", "green"
 */
function getColor(result) {
    switch (result) {
        case "Success":
            return "green";
        case "Skipped":
        case "Error":
            return "yellow";
        default:
            return "red";
    }
}

/**
 * Calculate the number of tests in each result group
 * @param {Array<import("./types").ScenarioData>} scenarios - List of ScenarioData objects
 * @returns {Object} - Object with the number of tests in each group: Success, Failed, Error, Skipped and Total
 */
const calculateTestGroups = (scenarios) => {
    const groups = scenarios.reduce(
      (acc, scenario) => {
        // @ts-ignore
        acc[scenario.htmlResult] += 1;
        acc.All += 1
        return acc;
      },
      { Success: 0, Failed: 0, Error: 0, Skipped: 0, All: 0 }
    );
    return groups;
  };

/* CUSTOM EXCEPTIONS */
class NoScenariosFound extends Error {
    constructor() {
        super("No scenarios found");
    }
}
