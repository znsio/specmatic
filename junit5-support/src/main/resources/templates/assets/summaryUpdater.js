/** @type {Array<HTMLLIElement>} */
// @ts-ignore Element Exists
const [successLi, failedLi, errorLi, skippedLi, totalLi] = headerSummary.querySelectorAll("ol > li > span:last-of-type");
/** @type {Array<HTMLSpanElement>} */
// @ts-ignore Element Exists
const [coverageTitleEl, coveragePercentageSpan] = headerSummary.querySelectorAll("div:first-of-type > *");
/** @type {HTMLTableRowElement} */
// @ts-ignore Element Exists
const firstGroupTitle = document.querySelector("table > thead > tr > td:nth-child(2)");
/** @type {Array<HTMLImageElement>} */
// @ts-ignore Element Exists
const [badgeMark, badgeImg] = document.querySelectorAll("div#summary > div#badge > img");

const colorToBackground = {
    green: "approved",
    red: "rejected",
    yellow: "warning",
};
const backgroundRegex = /bg-(approved|rejected|warning)/;
const filterRegex = /(approved|rejected|warning)/;

const initialData = {
    Success: successLi.textContent,
    Failed: failedLi.textContent,
    Error: errorLi.textContent,
    Skipped: skippedLi.textContent,
    All: totalLi.textContent,
    // @ts-ignore
    coveragePercentage: Number.parseInt(coveragePercentageSpan.textContent, 10),
    // @ts-ignore
    coverageTitle: coverageTitleEl.textContent.split(" ")[0],
    // @ts-ignore
    summaryBackground: headerSummary.className.match(backgroundRegex)[1] || "approved",
    activeFilter: getActiveFilter(),
};

/**
 * Updates the summary header with the new selected row data
 * @param {import("./types").SelectedRow} selectedResponse - Selected row data
 */
function updateSummaryHeader(selectedResponse) {
    const { groupValues, color, coverage } = selectedResponse;
    // @ts-ignore Func in utils
    const testsCount = calculateTestGroups(getScenarios(groupValues));
    // @ts-ignore
    const backgroundColor = colorToBackground[color];

    initialData.activeFilter = getActiveFilter();

    updateTestsCount(testsCount);
    // @ts-ignore textContent isn't Null
    updateCoverageInfo(coverage, firstGroupTitle.textContent);
    updateSummaryBackground(backgroundColor);
    updateCoverageFilter(backgroundColor);
    updateMarkAndBadge(backgroundColor);
    // @ts-ignore Func in utils
    updateLiStyle("All");

    // @ts-ignore DOM Element in index
    headerSummary.setAttribute("data-panel", "details");
}

/**
 * Resets the summary header to its initial state
 * @function
 */
function resetSummaryHeader() {
    // @ts-ignore All values exist
    updateTestsCount(initialData);
    updateCoverageInfo(initialData.coveragePercentage, initialData.coverageTitle);
    updateSummaryBackground(initialData.summaryBackground);
    updateCoverageFilter(initialData.summaryBackground);
    updateMarkAndBadge(initialData.summaryBackground);
    // @ts-ignore Func in utils
    updateLiStyle(initialData.activeFilter);

    // @ts-ignore DOM Element in index
    headerSummary.setAttribute("data-panel", "table");
}

/**
 * Updates the test counts in the summary header
 * @param {import("./types").TestCount} testsCount - Object with the number of tests in each result group
 */
function updateTestsCount(testsCount) {
    successLi.textContent = testsCount.Success.toString();
    failedLi.textContent = testsCount.Failed.toString();
    errorLi.textContent = testsCount.Error.toString();
    skippedLi.textContent = testsCount.Skipped.toString();
    totalLi.textContent = testsCount.All.toString();
}

/**
 * Updates the coverage title and percentage in the summary header
 * @param {number} coveragePercentage - Percentage of coverage
 * @param {string} coverageTitle - Title of the coverage (e.g. Path, Service, etc.)
 */
function updateCoverageInfo(coveragePercentage, coverageTitle) {
    coverageTitleEl.textContent = `${coverageTitle} Coverage`;
    coveragePercentageSpan.textContent = `${coveragePercentage}%`;
}

/**
 * Updates the background color of the summary header
 * @param {string} backgroundColor - The background color to apply, e.g. 'rejected', 'approved', 'warning', etc.
 */
function updateSummaryBackground(backgroundColor) {
    // @ts-ignore DOM Element in index
    headerSummary.className = headerSummary.className.replace(backgroundRegex, `bg-${backgroundColor}`);
}

/**
 * Updates the background color of the coverage filter in the summary header
 * @param {string} filterColor - The background color to apply, e.g. 'rejected', 'approved', 'warning', etc.
 */
function updateCoverageFilter(filterColor) {
    coveragePercentageSpan.className = coveragePercentageSpan.className.replace(filterRegex, filterColor);
}

/**
 * Updates the mark and badge images in the summary header
 * @param {string} background - The background color to apply, e.g. 'rejected', 'approved', 'warning', etc.
 */
function updateMarkAndBadge(background) {
    badgeImg.src = backgroundToSrc(background);
    badgeMark.className = badgeMark.className.replace(filterRegex, background);
}

/**
 * Returns the src for the mark SVG based on the given background color
 * @param {string} background - The background color to apply, e.g. 'rejected', 'approved', 'warning', etc.
 * @returns {string} The src path for the mark SVG
 */
function backgroundToSrc(background) {
    switch (background) {
        case "approved":
            return "assets/mark-approved.svg";
        default:
            return "assets/mark-rejected.svg";
    }
}

/**
 * Returns the id of the currently selected filter in the summary header
 * @returns {string} The id of the selected filter, or "All" if no filter is selected
 */
function getActiveFilter() {
    // @ts-ignore always one active filter
    const active = headerSummary.querySelector("ol > li.active");
    return active.getAttribute("id");
}
