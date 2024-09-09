/* DOM Elements */
const resultsList = document.querySelector("ol#results");
const resultsLi = document.querySelectorAll("ol#results > li");
const allTests = document.querySelector("#all");

/* VARIABLES */
const tableRows = document.querySelectorAll("table#reports tbody tr");
const groupedRows = groupRowsByFirstAndSecondGroups(tableRows);

/* Event Listeners */
resultsList.addEventListener("click", (event) => {
  const target = event.target;
  const nearestListItem = target.closest("li");
  let nearestLiType = nearestListItem?.getAttribute("data-type");

  if (nearestLiType) {

    if (reportTable.getAttribute("data-filter") === nearestLiType) {
      nearestLiType = "All";
    }

    if(headerSummary.getAttribute("data-panel") !== "details") {
      updateTable(groupedRows[nearestLiType]);
      goBackToTable(false);
    } else {
      updateScenarios(nearestLiType);
    }

    updateLiStyle(nearestLiType);
    reportTable.setAttribute("data-filter", nearestLiType);
    event.stopPropagation();
  }
});

/* Utils */
function groupRowsByFirstAndSecondGroups(tableRows) {
  const categories = { Success: {}, Error: {}, Failed: {}, Skipped: {}, All: {} };

  for (const row of tableRows) {
    const rowValues = extractValuesFromTableRow(row);
    const { firstGroup, secondGroup, thirdGroup, type } = rowValues;

    if (!(type in categories)) {
      throw new Error(`Unknown type: ${row.type}`);
    }

    if (!categories[type][firstGroup]) categories[type][firstGroup] = {};
    if (!categories[type][firstGroup][secondGroup]) categories[type][firstGroup][secondGroup] = {};
    if (!categories[type][firstGroup][secondGroup][thirdGroup]) categories[type][firstGroup][secondGroup][thirdGroup] = [];
    categories[type][firstGroup][secondGroup][thirdGroup].push(rowValues);

    if (!categories.All[firstGroup]) categories.All[firstGroup] = {};
    if (!categories.All[firstGroup][secondGroup]) categories.All[firstGroup][secondGroup] = {};
    if (!categories.All[firstGroup][secondGroup][thirdGroup]) categories.All[firstGroup][secondGroup][thirdGroup] = [];
    categories.All[firstGroup][secondGroup][thirdGroup].push(rowValues);
  }

  return categories;
}

function updateTable(groupedRows) {
  for (const row of tableRows) {
    const rowChildren = Array.from(row.children);
    let firstGroupTd;
    let secondGroupTd;
    let rest;

    if (rowChildren.length === 6) {
      firstGroupTd = rowChildren[1];
      secondGroupTd = rowChildren[2];
      rest = rowChildren.slice(3);
    } else {
      firstGroupTd = rowChildren[0];
      secondGroupTd = rowChildren[1];
      rest = rowChildren.slice(2);
    }

    const { firstGroup, secondGroup, thirdGroup } = extractValuesFromTableRow(row);

    const firstGroupExists = firstGroup in groupedRows;
    const secondGroupExists = firstGroupExists && secondGroup in groupedRows[firstGroup];

    row.classList.toggle("hidden", !firstGroupExists);

    if (secondGroupTd.getAttribute("data-main") === "true") {
      secondGroupTd.classList.toggle("hidden", !secondGroupExists);
    }

    for (const row of rest) {
      const thirdGroupExists = firstGroupExists && secondGroupExists && thirdGroup in groupedRows[firstGroup][secondGroup];
      row.classList.toggle("hidden", !thirdGroupExists);
    }
  }
}

function updateScenarios(resultType) {
  const scenarios = document.querySelectorAll("ul#scenarios > li");
  for (const scenario of scenarios) {
    const matchesResultType = scenario.getAttribute("data-type") === resultType;
    scenario.classList.toggle("hidden", !matchesResultType && resultType !== "All");
  }
}

function updateLiStyle(nearestLiType) {
  for (const li of resultsLi) {
    li.classList.toggle("button-pressed", li.getAttribute("data-type") === nearestLiType);
  }
}