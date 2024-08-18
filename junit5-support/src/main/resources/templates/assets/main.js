/* Variables */
const SCENARIOS = readJsonData();
let selectedResponse = {
  coverage: 0,
  path: "",
  method: "",
  response: 0,
  exercised: 0,
  result: "",
  color: "",
};
let scrollYPosition = 0;

/* DOM Elements */
const backBtn = document.querySelector("button#go-back");
const downloadBtn = document.querySelector("#downloadButton");
const mainElement = document.querySelector("main");
const responseSummary = document.querySelector("ul#response-summary");
const scenariosList = document.querySelector("ul#scenarios");
const reportTable = document.querySelector("table#reports");

/* Functions */
function readJsonData() {
  const jsonData = document.querySelector("script#json-data");
  return JSON.parse(jsonData.textContent);
}

function addScenarios(scenarios) {
  try {
    if (scenarios.length === 0) throw "No scenarios found";
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

backBtn.addEventListener("click", goBackToTable);

downloadBtn.addEventListener("click", () => {
  window.print();
});

reportTable.addEventListener("click", (event) => {
  const nearestRow = event.target.closest("tr");

  if (nearestRow && nearestRow.parentElement?.nodeName !== "THEAD") {
    selectedResponse = extractValuesFromTableRow(nearestRow);
    const responseElement = createResponseSummaryDetails(selectedResponse);
    responseSummary.replaceChildren(responseElement);
    const borderColor = `border-${selectedResponse.color}-300`;
    responseSummary.className = responseSummary.className.replace(/border-\w{3,}-300/, borderColor);
    const scenarios = SCENARIOS[selectedResponse.path]?.[selectedResponse.method]?.[selectedResponse.response] ?? [];
    addScenarios(scenarios);
  }
});

scenariosList.addEventListener("click", (event) => {
  const target = event.target;
  const nearestListItem = target.closest("li");
  if (nearestListItem && !target.matches("#req-res *")) {
    const dataShow = nearestListItem.getAttribute("data-show");
    nearestListItem.setAttribute("data-show", dataShow === "true" ? "false" : "true");
    event.stopPropagation();
  }
});

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
      case "type":
        continue;
    }

    li.appendChild(keySpan);
    li.appendChild(valueSpan);
    documentFragment.appendChild(li);
  }

  return documentFragment;
}

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

function createScenarioInformation(scenario) {
  const scenarioInfoDiv = document.createElement("div");
  scenarioInfoDiv.classList.add("flex", "items-center", "justify-between");

  const scenarioName = document.createElement("p");
  scenarioName.textContent = `${scenario.name}`;

  const scenarioDuration = document.createElement("span");
  scenarioDuration.classList.add(...PILL_CSS, "w-28", "bg-blue-200");
  scenarioDuration.textContent = `${scenario.duration}ms`;

  const scenarioResult = document.createElement("span");
  const pillColor = getColor(scenario.result);
  const pillText = scenario.wip ? "WIP" :
    scenario.valid || scenario.remark === "MissingInSpec" ? scenario.remark : "Invalid";
  scenarioResult.classList.add(...PILL_CSS, `bg-${pillColor}-300`, "w-36");
  scenarioResult.textContent = pillText;

  const badgeDiv = document.createElement("div");
  badgeDiv.classList.add("flex", "items-center", "gap-2");
  badgeDiv.appendChild(scenarioResult);
  badgeDiv.appendChild(scenarioDuration);

  scenarioInfoDiv.appendChild(scenarioName);
  scenarioInfoDiv.appendChild(badgeDiv);
  return scenarioInfoDiv;
}

function createReqResDetailsContainer(scenario) {
  const reqResDetailsDiv = document.createElement("div");
  reqResDetailsDiv.id = "req-res";
  reqResDetailsDiv.classList.add(...REQ_RES_DETAILS_CONTAINER_CSS);

  const additionalInfoDiv = document.createElement("div");
  additionalInfoDiv.classList.add("text-sm");
  additionalInfoDiv.appendChild(document.createElement("p")).textContent = `Request URL: ${scenario.url}`;
  additionalInfoDiv.appendChild(document.createElement("p")).textContent = `Request Time: ${epochToDateTime(
    scenario.requestTime
  )}`;
  additionalInfoDiv.appendChild(document.createElement("p")).textContent = `Response Time: ${epochToDateTime(
    scenario.responseTime
  )}`;
  additionalInfoDiv.appendChild(
    document.createElement("p")
  ).textContent = `Specifications File: ${scenario.specFileName}`;
  reqResDetailsDiv.appendChild(additionalInfoDiv);

  reqResDetailsDiv.appendChild(createReqResDetailDiv("Details", scenario.details));
  reqResDetailsDiv.appendChild(createReqResDetailDiv("Request", scenario.request));
  reqResDetailsDiv.appendChild(createReqResDetailDiv("Response", scenario.response));

  return reqResDetailsDiv;
}

function createReqResDetailDiv(title, content) {
  const elementDiv = document.createElement("div");
  elementDiv.classList.add(...REQ_RES_DETAIL_CSS);
  elementDiv.appendChild(document.createElement("p")).textContent = `${title}: `;
  const elementPre = document.createElement("pre");
  elementPre.classList.add("whitespace-pre-wrap");
  elementDiv.appendChild(elementPre).textContent = content;
  return elementDiv;
}

function noScenarioFoundMessage() {
  const messageElement = document.createElement("li");
  messageElement.textContent = "No scenarios found for this filter.";
  return messageElement;
}

function epochToDateTime(epoch) {
  if (epoch === 0) return "N/A";
  return new Date(epoch).toISOString();
}

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
