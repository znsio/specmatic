/* DOM Elements */

/** @type {HTMLOListElement} */
// @ts-ignore
const testCountsLi = document.querySelector("ol#test-counts");
/** @type {HTMLLIElement} */
// @ts-ignore
const allTests = document.querySelector("#All");
/** @type {Array<HTMLTableRowElement>} */
// @ts-ignore
const tableRows = document.querySelectorAll("table#reports tbody tr");
/** @type {HTMLDivElement} */
// @ts-ignore
const headerSummary = document.querySelector("header > #summary");

/* VARIABLES */
const groupedRows = groupRows(tableRows);

/* Event Listeners */
testCountsLi.addEventListener("click", (event) => {
    /** @type {HTMLElement} */
    // @ts-ignore will be a HTML Element
    const target = event.target;

    const nearestListItem = target.closest("li");

    if (nearestListItem) {
        /** @type {string} */
        // @ts-ignore will always have an id
        let liType = nearestListItem.getAttribute("id");

        // @ts-ignore
        if (reportTable.getAttribute("data-filter") === liType) {
            liType = "All";
        }

        if (headerSummary.getAttribute("data-panel") !== "details") {
            // @ts-ignore Func in utils
            updateTable(groupedRows[liType]);
        } else {
            updateScenarios(liType);
        }

        updateLiStyle(liType);
        // @ts-ignore DOM Element in index
        reportTable.setAttribute("data-filter", liType);
        event.stopPropagation();
    }
});

/**
 * Group all table rows by type and group values
 * @param {HTMLTableRowElement[]} tableRows - All table rows
 * @returns {import("./types").Categories} - Object with categories as keys and arrays of grouped rows as values
 */
function groupRows(tableRows) {
    const categories = { Success: {}, Error: {}, Failed: {}, Skipped: {}, All: {} };

    for (const row of tableRows) {
        /** @type {import("./types").SelectedRow} */
        // @ts-ignore Func in utils
        const rowValues = extractRowValues(row);
        const { groupValues, type } = rowValues;
        const maxGroupIndex = groupValues.length - 1;

        if (!(type in categories)) {
            throw new Error(`Unknown type: ${type}`);
        }

        // @ts-ignore
        let workingGroup = categories[type];
        let allGroup = categories.All;

        for (const [index, group] of groupValues.entries()) {
            if (!workingGroup[group.value]) workingGroup[group.value] = index === maxGroupIndex ? [] : {};
            // @ts-ignore
            if (!allGroup[group.value]) allGroup[group.value] = index === maxGroupIndex ? [] : {};

            workingGroup = workingGroup[group.value];
            // @ts-ignore
            allGroup = allGroup[group.value];
        }

        workingGroup.push(rowValues);
        // @ts-ignore
        allGroup.push(rowValues);
    }

    return categories;
}

/**
 * Hide or show table rows based on group values
 * @param {import("./types").Categories} groupedRows - Object with categories as keys and arrays of grouped rows as values
 */
function updateTable(groupedRows) {
    for (const row of tableRows) {
        const { restGroupTds, restTds } = getRowTds(row);
        const { firstGroup, restGroups } = getRowGroups(row);

        let currentGroup = groupedRows;
        const showRow = firstGroup.value in currentGroup;
        row.classList.toggle("hidden", !showRow);

        if (!showRow) continue;

        // @ts-ignore
        currentGroup = currentGroup[firstGroup.value];
        let showRest = true;

        for (const [index, group] of restGroups.entries()) {
            const rowTd = restGroupTds[index];
            const showRowTd = group.value in currentGroup;

            if (rowTd.getAttribute("data-main") === "true") {
                rowTd.classList.toggle("hidden", !showRowTd);
            }

            showRest = showRest && showRowTd;
            // @ts-ignore
            currentGroup = showRowTd ? currentGroup[group.value] : currentGroup;
        }

        for (const td of restTds) {
            td.classList.toggle("hidden", !showRest);
        }
    }
}

/**
 * Given a table row, return an object with three properties: childrenTds, restGroupTds, and restTds.
 * childrenTds is an array of all the <td> elements in the row, restGroupTds is an array of all <td> elements
 * except the first and the last two, and restTds is an array of the last two <td> elements.
 * @param {HTMLTableRowElement} row - Table row
 * @returns {import("./types").GroupedRowTds} Object with three properties: childrenTds, restGroupTds, and restTds
 */
function getRowTds(row) {
    /** @type {Array<HTMLTableCellElement>} */
    // @ts-ignore will be an array of Tds
    const childrenTds = Array.from(row.children);

    return {
        childrenTds,
        restGroupTds: childrenTds.slice(2, -2),
        restTds: childrenTds.slice(-2),
    };
}

/**
 * Given a table row, return an object.
 * firstGroup is the second column of the table row, and restGroups is an array of the rest of
 * the group columns except last two td elements.
 * @param {HTMLTableRowElement} row - Table row
 * @returns {import("./types").GroupedRowColumns} Object with two properties: firstGroup and restGroups
 */
function getRowGroups(row) {
    /** @type {import("./types").SelectedRow} */
    // @ts-ignore Func in utils
    const { groupValues } = extractRowValues(row);
    return {
        firstGroup: groupValues[0],
        restGroups: groupValues.slice(1),
    };
}

/**
 * Updates the visibility of the scenarios list items based on the result type filter.
 * @param {string} resultType - The result type filter, either "Success", "Failed", "Error" or "All"
 */
function updateScenarios(resultType) {
    // @ts-ignore DOM element in index
    for (const scenario of scenariosList.children) {
        const matchesResultType = scenario.getAttribute("data-type") === resultType;
        scenario.classList.toggle("hidden", !matchesResultType && resultType !== "All");
    }
}

/**
 * Updates the active class of the result type list items based on the given test type
 * @param {string} testType - The result type to make active, either "Success", "Failed", "Error" or "All"
 */
function updateLiStyle(testType) {
    for (const li of testCountsLi.children) {
        li.classList.toggle("active", li.getAttribute("id") === testType);
    }
}
