/* Variables */
const SCENARIOS = readJsonData();
let selectedResponse = {
    coverage: 0,
    path: "",
    method: "",
    response: 0,
    exercised: 0,
    result: "",
    color: ""
};
let scrollYPosition = 0;

/* DOM Elements */
const backBtn = document.querySelector("button#go-back");
const downloadBtn = document.querySelector("#downloadButton");
const mainElement = document.querySelector("main");
const responseSummary = document.querySelector("ul#response-summary");
const scenariosList = document.querySelector("ul#scenarios");
const reportTable = document.querySelector("table#reports");
const summary = document.querySelector("div#summary");

/* Functions */
function readJsonData() {
    const jsonData = document.querySelector("script#json-data");
    return JSON.parse(jsonData.textContent);
}

/**
 * Adds scenarios to the DOM
 * @param {Object[]} scenarios
 * @returns {void}
 */
function addScenarios(scenarios) {
    try {
        const docFragment = document.createDocumentFragment();
        for (const scenario of scenarios) {
            const scenarioItem = createScenarioItem(scenario);
            docFragment.appendChild(scenarioItem);
        }
        scenariosList.replaceChildren(docFragment);
    } catch {
        scenariosList.replaceChildren(noScenarioFoundMessage());
    } finally {
        scrollYPosition = window.scrollY;
        mainElement.setAttribute("data-item", "details");
        scrollTo(0, 0);
        event.stopPropagation();
    }
}

/* Event Listeners */

backBtn.addEventListener("click", () => {
    setTimeout(() => {
        scenariosList.replaceChildren();
    }, 300)
    mainElement.setAttribute("data-item", "table");
    scrollTo(0, scrollYPosition);
})

downloadBtn.addEventListener("click", () => {
    window.print();
})

reportTable.addEventListener("click", (event) => {
    const nearestRow = event.target.closest("tr");

    if (nearestRow && nearestRow.parentElement?.nodeName !== "THEAD") {
        selectedResponse = extractValuesFromTableRow(nearestRow);
        const responseElement = createResponseSummaryDetails(selectedResponse);
        responseSummary.replaceChildren(responseElement);
        const borderColor = `border-${selectedResponse.color}-300`;
        responseSummary.className = responseSummary.className.replace(/border-\w{3,}-300/, borderColor);
        responseSummary.classList.add("px-10");
        addScenarios(SCENARIOS[selectedResponse.path][selectedResponse.method][selectedResponse.response]);
    }
})

scenariosList.addEventListener("click", (event) => {
	const target = event.target;
	const nearestListItem = target.closest("li");
	if (nearestListItem && !target.matches("#req-res *")) {
		const dataShow = nearestListItem.getAttribute("data-show");
		nearestListItem.setAttribute("data-show", dataShow === "true" ? "false" : "true");
		event.stopPropagation();
	}
});

summary.addEventListener("click", (event) => {
    const target = event.target;
    const nearestListItem = target.closest("li");
    if (nearestListItem?.getAttribute("data-type")) {
        const resultType = nearestListItem.getAttribute("data-type");
        const scenarios = filterScenariosByResult(SCENARIOS, resultType);
        const summary = createSummaryForFilteredScenariosByResult(resultType, scenarios);
        responseSummary.className = responseSummary.className.replace(/border-\w{3,}-300/, `border-${getColor(resultType)}-300`);
        responseSummary.classList.remove("px-10");
        responseSummary.replaceChildren(summary);
        addScenarios(scenarios);
        event.stopPropagation();
    }
})


/* Utils */

const REQ_RES_DETAIL_CSS = ["p-4", "bg-slate-50", "border-2"];
const REQ_RES_DETAILS_CONTAINER_CSS = [
	"flex",
	"group-data-[show=true]:py-2",
	"flex-col",
	"group-data-[show=false]:h-0",
	"gap-3",
	"overflow-hidden",
	"transition-all",
	"group-data-[show=true]:h-auto",
];
const PILL_CSS = ["py-2", "text-center", "rounded-xl", "font-medium"];

/**
 * Extract values from table row
 * @param {HTMLTableRowElement} tableRow
 * @returns {string[]}
 */
function extractValuesFromTableRow(tableRow) {
  const values = [...tableRow.children].map((child) => child.textContent);
  return {
    coverage: Number(values[0].trim().slice(0, -1)),
    path: values[1].trim(),
    method: values[2].trim(),
    response: Number(values[3]),
    exercised: Number(values[4]),
    result: values[5].trim(),
    color: tableRow.lastElementChild.getAttribute("data-color")
  };
}

/**
 * Create response summary details
 * @param {Object} response
 * @returns {DocumentFragment}
 */
function createResponseSummaryDetails(response) {
    const documentFragment = document.createDocumentFragment();

    for (const [key, value] of Object.entries(response)) {
        const li = document.createElement("li");
        li.classList.add("flex-shrink-0");

        const keySpan = document.createElement("span");
        keySpan.textContent = `${key}: `;
        keySpan.classList.add("font-light", "capitalize");

        const valueSpan = document.createElement("span");
        valueSpan.textContent = value;

        switch (key) {
            case "path":
                li.classList.replace("flex-shrink-0", "break-all");
                break;
            case "coverage":
                valueSpan.textContent = `${value}%`;
                break;
            case "color":
                continue
        }

        li.appendChild(keySpan);
        li.appendChild(valueSpan);
        documentFragment.appendChild(li);
    }

    return documentFragment;
}

/**
 * Create scenario item
 * @param {Object} scenario
 * @returns {DocumentFragment}
*/
function createScenarioItem(scenario) {
	const scenarioItem = document.createElement("li");
	scenarioItem.classList.add("p-2", "border-2", "cursor-pointer", "group", "rounded-md");
	scenarioItem.setAttribute("data-show", "false");

	const scenarioInformation = createScenarioInformation(scenario);
	const reqRes = createReqResDetailsContainer(scenario);
	scenarioItem.appendChild(scenarioInformation);
	scenarioItem.appendChild(reqRes);

	return scenarioItem;
}

/**
* Create scenario information
* @param {Object} scenario
* @returns {HTMLDivElement}
*/
function createScenarioInformation(scenario) {
	const scenarioInfoDiv = document.createElement("div");
	scenarioInfoDiv.classList.add("flex", "items-center", "justify-between");

	const scenarioName = document.createElement("p");
	scenarioName.textContent = `${scenario.name}`;

	const scenarioDuration = document.createElement("span");
	scenarioDuration.classList.add(...PILL_CSS, "w-28", "bg-blue-200");
	scenarioDuration.textContent = `${scenario.duration}ms`;

	const scenarioResult = document.createElement("span");
	scenarioResult.classList.add(...PILL_CSS, `bg-${getColor(scenario.result)}-300`, "w-32");
	scenarioResult.textContent = scenario.valid ? scenario.remark : "Invalid";

	const badgeDiv = document.createElement("div");
	badgeDiv.classList.add("flex", "items-center", "gap-2");
	badgeDiv.appendChild(scenarioResult);
	badgeDiv.appendChild(scenarioDuration);

	scenarioInfoDiv.appendChild(scenarioName);
	scenarioInfoDiv.appendChild(badgeDiv);
	return scenarioInfoDiv;
}

/**
 * Create Request, Response and Details container
 * @param {Object} scenario
 * @returns {HTMLDivElement}
*/
function createReqResDetailsContainer(scenario) {
    const reqResDetailsDiv = document.createElement("div");
	reqResDetailsDiv.id = "req-res";
	reqResDetailsDiv.classList.add(...REQ_RES_DETAILS_CONTAINER_CSS);

    const additionalInfoDiv = document.createElement("div");
	additionalInfoDiv.classList.add("text-sm");
	additionalInfoDiv.appendChild(document.createElement("p")).textContent = `Request URL: ${scenario.url}`;
	additionalInfoDiv.appendChild(document.createElement("p")).textContent = `Request Time: ${epochToDateTime(scenario.requestTime)}`;
	additionalInfoDiv.appendChild(document.createElement("p")).textContent = `Response Time: ${epochToDateTime(scenario.responseTime)}`;
	additionalInfoDiv.appendChild(document.createElement("p")).textContent = `Specifications File: ${scenario.specFileName}`;
	reqResDetailsDiv.appendChild(additionalInfoDiv);

    reqResDetailsDiv.appendChild(createReqResDetailDiv("Request", scenario.request))
    reqResDetailsDiv.appendChild(createReqResDetailDiv("Response", scenario.response))
    reqResDetailsDiv.appendChild(createReqResDetailDiv("Details", scenario.details))

    return reqResDetailsDiv;
}

/**
 * Creates Request, Response and Details container
 * @param {string} title
 * @param {string} content
 * @returns {HTMLDivElement}
 */
function createReqResDetailDiv(title, content) {
    const elementDiv = document.createElement("div");
    elementDiv.classList.add(...REQ_RES_DETAIL_CSS);
	elementDiv.appendChild(document.createElement("p")).textContent = `${title}: `;
	const elementPre = document.createElement("pre")
	elementPre.classList.add("whitespace-pre-wrap")
	elementDiv.appendChild(elementPre).textContent = content;
	return elementDiv;
}

/**
 * Li element when np scenarios are found
 * @returns {HTMLLIElement}
 */
function noScenarioFoundMessage() {
    const messageElement = document.createElement("li");
    messageElement.textContent = "No scenarios found for this response";
    return messageElement
}

/**
 * Convert epoch to date time
 * @param {number} epoch
 * @returns string
 */
function epochToDateTime(epoch) {
	if (epoch === 0) return "N/A";
	return new Date(epoch).toISOString();
}

/**
 * Get background color for result
 * @param {string} result
 * @returns string
 */
function getColor(result) {
    switch (result) {
        case "Success":
            return "green";
        case "Skipped":
            return "yellow";
        default:
            return "red";
    }
}


/**
 * Filter scenarios based on result
 * @param {Object} scenarios
 * @param {string} result
 * @returns {Object[]}
 */
function filterScenariosByResult(scenarios, result) {
    const filteredScenarios = []

    for (const methodGroup of Object.values(scenarios)) {
        for (const responseGroup of Object.values(methodGroup)) {
            for (const scenarios of Object.values(responseGroup)) {
                const failed = scenarios.filter(scenario => scenario.result === result);
                filteredScenarios.push(...failed);
            }
        }
    }

    return filteredScenarios;
}

/**
 * Create Summary For Filtered Scenarios by Result
 * @param {string} result
 * @param {Object[]} scenarios
 * @returns {HTMLLIElement}
*/
function createSummaryForFilteredScenariosByResult(result, scenarios) {
    const liElement = document.createElement("li");
    liElement.classList.add("flex", "items-center", "justify-center", "text-white", "tracking-widest", "font-bold",
        "text-center", "w-full", "h-full", `bg-${getColor(result)}-500`, "uppercase")
    liElement.textContent = `${result}: ${scenarios.length}`;
    return liElement
}