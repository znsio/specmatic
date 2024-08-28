/* DOM ELEMENTS */

/** @type {HTMLButtonElement} */
// @ts-ignore Element Exists
const backBtn = document.querySelector("button#back");
/** @type {HTMLButtonElement} */
// @ts-ignore Element Exists
const printBtn = document.querySelector("button#print");
/** @type {HTMLElement} */
// @ts-ignore Element Exists
const mainElement = document.querySelector("main");
/** @type {HTMLTableElement} */
// @ts-ignore Element Exists
const reportTable = document.querySelector("table#reports");
/** @type {HTMLOListElement} */
// @ts-ignore Element Exists
const groupSummaryList = document.querySelector("ul#group-summary");
/** @type {HTMLOListElement} */
// @ts-ignore Element Exists
const scenariosList = document.querySelector("ol#scenarios");

/* VARIABLES */

/** @type {import("./types").ScenarioGroup} */
// @ts-ignore Func in utils
const SCENARIOS = readJsonDataFromScript(document.querySelector("script#json-data"));

/** @type {import("./types").SelectedRow} */
let selectedResponse = {
    coverage: 0,
    groupValues: [],
    exercisedCount: 0,
    result: "",
    color: "",
    type: "",
};
// @ts-ignore Used in utils
let scrollYPosition = 0;

/* EVENT LISTENERS */

backBtn.addEventListener("click", () => {
    // @ts-ignore Func in summaryUpdater
    resetSummaryHeader();
    // @ts-ignore Func in utils
    goBackToTable(500, true);
});

printBtn.addEventListener("click", () => {
    window.print();
});

reportTable.addEventListener("click", (event) => {
    /** @type {HTMLTableRowElement | null} */
    // @ts-ignore Is an HTML Element or Null
    const nearestRow = event.target.closest("tr");

    if (nearestRow && nearestRow.parentElement?.nodeName !== "THEAD") {
        // @ts-ignore Func in utils
        selectedResponse = extractRowValues(nearestRow);
        const summaryFragment = createResponseSummaryDetails(selectedResponse);
        groupSummaryList.replaceChildren(summaryFragment);
        // @ts-ignore Func in utils
        const scenarios = getScenarios(selectedResponse.groupValues);
        // @ts-ignore Func in utils
        addScenariosToDetails(scenarios);
        // @ts-ignore Func in summaryUpdater
        updateSummaryHeader(selectedResponse);
        scrollYPosition = window.scrollY;
        event.stopPropagation();
    }
});

scenariosList.addEventListener("click", (event) => {
    const target = event.target;
    /** @type {HTMLLIElement | null} */
    // @ts-ignore Its a HTMLElement or Null
    const nearestListItem = target.closest("li");

    if (nearestListItem) {
        const dataExpand = nearestListItem.getAttribute("data-expand");
        nearestListItem.setAttribute("data-expand", dataExpand === "true" ? "false" : "true");
        event.stopPropagation();
    }
});

/* FUNCTIONS */

/**
 * Create a div that displays a request or response or detail with a title
 * @param {string} title - The title of the detail
 * @param {string} content - The content of the detail
 * @returns {HTMLDivElement} - The created div
 */
function createReqResDetailDiv(title, content) {
    const elementDiv = document.createElement("div");
    elementDiv.classList.add("req-res-detail");
    elementDiv.appendChild(document.createElement("p")).textContent = `${title}: `;

    const elementPre = document.createElement("pre");
    elementDiv.appendChild(elementPre).textContent = content;

    return elementDiv;
}

/**
 * Create scenario list item
 * @param {import("./types").ScenarioData} scenario - Scenario data to create li from
 * @returns {HTMLLIElement}
 */
function createScenarioLi(scenario) {
    const scenarioLi = document.createElement("li");
    scenarioLi.classList.add("scenario");
    scenarioLi.setAttribute("data-type", scenario.htmlResult);
    scenarioLi.setAttribute("data-expand", "false");

    const scenarioSummary = createScenarioInformation(scenario);
    const scenarioReqRes = createScenarioReqRes(scenario);

    scenarioLi.appendChild(scenarioSummary);
    scenarioLi.appendChild(scenarioReqRes);
    return scenarioLi;
}

/**
 * Create scenario information div
 * @param {import("./types").ScenarioData} scenario - Scenario data to create div from
 * @returns {HTMLDivElement}
 */
function createScenarioInformation(scenario) {
    const scenarioInfoDiv = document.createElement("div");
    scenarioInfoDiv.classList.add("scenario-summary");

    const scenarioName = document.createElement("p");
    scenarioName.textContent = `${scenario.name}`;

    const scenarioDuration = document.createElement("span");
    scenarioDuration.textContent = `${scenario.duration}ms`;

    const scenarioResult = document.createElement("span");
    // @ts-ignore Func in utils
    const pillColor = getColor(scenario.htmlResult);
    scenarioResult.classList.add("pill", pillColor);
    const pillText = scenario.wip ? "WIP" : scenario.valid || scenario.testResult === "MissingInSpec" ? scenario.testResult : "Invalid";
    scenarioResult.textContent = pillText;

    const badgeDiv = document.createElement("div");
    badgeDiv.appendChild(scenarioDuration);
    badgeDiv.appendChild(scenarioResult);

    scenarioInfoDiv.appendChild(scenarioName);
    scenarioInfoDiv.appendChild(badgeDiv);

    return scenarioInfoDiv;
}

/**
 * Create a div that displays a request and response and details for a scenario
 * @param {import("./types").ScenarioData} scenario - Scenario data to create div from
 * @returns {HTMLDivElement}
 */
function createScenarioReqRes(scenario) {
    const reqResDetailsDiv = document.createElement("div");
    reqResDetailsDiv.classList.add("req-res");

    const additionalInfoDiv = document.createElement("div");
    additionalInfoDiv.classList.add("additional-info");
    // @ts-ignore Func in utils
    additionalInfoDiv.appendChild(createParagraphWithSpan("Request URL", scenario.baseUrl));
    // @ts-ignore Func in utils
    additionalInfoDiv.appendChild(createParagraphWithSpan("Request Time", epochToDateTime(scenario.requestTime)));
    // @ts-ignore Func in utils
    additionalInfoDiv.appendChild(createParagraphWithSpan("Response Time", epochToDateTime(scenario.responseTime)));
    // @ts-ignore Func in utils
    additionalInfoDiv.appendChild(createParagraphWithSpan("Specifications File", scenario.specFileName));
    reqResDetailsDiv.appendChild(additionalInfoDiv);

    reqResDetailsDiv.appendChild(createReqResDetailDiv("Details", scenario.details));
    reqResDetailsDiv.appendChild(createReqResDetailDiv("Request", scenario.request));
    reqResDetailsDiv.appendChild(createReqResDetailDiv("Response", scenario.response));

    return reqResDetailsDiv;
}

/**
 * Create scenario Summary Details
 * @param {import("./types").SelectedRow} selectedRow - TableRow to create details from
 * @returns {DocumentFragment}
 */
function createResponseSummaryDetails(selectedRow) {
    const summaryFragment = document.createDocumentFragment();

    for (const groupItem of selectedRow.groupValues) {
        const groupLi = document.createElement("li");
        // @ts-ignore Func in utils
        groupLi.replaceChildren(...createKeyValueSpan(groupItem.name, groupItem.value));
        summaryFragment.appendChild(groupLi);
    }

    const resultLi = document.createElement("li");
    resultLi.classList.add("capitalize");
    // @ts-ignore Func in utils
    resultLi.replaceChildren(...createKeyValueSpan("Result", selectedRow.result));
    summaryFragment.appendChild(resultLi);

    return summaryFragment;
}

/**
 * Add scenarios to details
 * @param {Array<import("./types").ScenarioData>} scenarios - Array of scenarios
 */
function addScenariosToDetails(scenarios) {
    try {
        // @ts-ignore Exception in utils
        if (scenarios.length === 0) throw new NoScenariosFound("No scenarios found");
        const docFragment = document.createDocumentFragment();
        for (const scenario of scenarios) {
            const scenarioLi = createScenarioLi(scenario);
            docFragment.appendChild(scenarioLi);
        }
        scenariosList.replaceChildren(docFragment);
    } catch (e) {
        scenariosList.replaceChildren("No scenarios found");
    } finally {
        scrollYPosition = window.scrollY;
        window.scrollTo(0, 0);
        mainElement.setAttribute("data-panel", "details");
    }
}
