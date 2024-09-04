const extractValuesFromTableRow = (tableRow) => {
  const values = [...tableRow.children].map((child) => child.textContent);
  return {
    coverage: Number(values[0].trim().slice(0, -1)),
    firstGroup: values[1].trim(),
    secondGroup: values[2].trim(),
    thirdGroup: values[3],
    exercised: Number(values[4]),
    result: values[5]?.trim() || tableRow.getAttribute("data-result") || "Unknown",
    color: tableRow.getAttribute("data-color"),
    type: tableRow.getAttribute("data-type"),
  };
};

const goBackToTable = (scrollBack = true) => {
  setTimeout(() => {
    scenariosList.replaceChildren();
  }, 300);
  mainElement.setAttribute("data-item", "table");
  if(headerSummary.getAttribute("data-panel") === "details") {
    resetSummaryHeader();
    headerSummary.setAttribute("data-panel", "table");
  }
  if (scrollBack) {
    scrollTo(0, scrollYPosition);
  }
};

const calculateTestGroups = (scenarios) => {
  const groups = scenarios.reduce(
    (acc, scenario) => {
      acc[scenario.htmlResult] += 1;
      acc.Total += 1
      return acc;
    },
    { Success: 0, Failed: 0, Error: 0, Skipped: 0, Total: 0 }
  );
  return groups;
};