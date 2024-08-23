const headerSummary = document.querySelector("div#summary");
const [successLi, failedLi, errorLi, skippedLi, totalLi] = document.querySelectorAll(
  "div#summary > ol > li > p > span:first-of-type"
);
const [coverageTitleEl, coveragePercentageSpan] = document.querySelectorAll("div#summary > div > *");
const firstGroupTitle = document.querySelector("table > thead > tr > td:nth-child(2)");
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
  Total: totalLi.textContent,
  coveragePercentage: Number.parseInt(coveragePercentageSpan.textContent, 10),
  coverageTitle: coverageTitleEl.textContent.split(" ")[0],
  summaryBackground: headerSummary.className.match(backgroundRegex)[1],
  activeFilter: getActiveFilter(),
};

function updateSummaryHeader(scenarios, selectedResponse) {
  const testsCount = calculateTestGroups(scenarios);
  const backgroundColor = colorToBackground[selectedResponse.color];
  const coveragePercentage = selectedResponse.coverage;
  initialData.activeFilter = getActiveFilter();

  updateTestsCount(testsCount);
  updateCoverageInfo(coveragePercentage, firstGroupTitle.textContent);
  updateSummaryBackground(backgroundColor);
  updateCoverageFilter(backgroundColor);
  updateMarkAndBadge(backgroundColor);
  updateLiStyle("All");
  headerSummary.setAttribute("data-panel", "details");
}

function resetSummaryHeader() {
  updateTestsCount(initialData);
  updateCoverageInfo(initialData.coveragePercentage, initialData.coverageTitle);
  updateSummaryBackground(initialData.summaryBackground);
  updateCoverageFilter(initialData.summaryBackground);
  updateMarkAndBadge(initialData.summaryBackground);
  updateLiStyle(initialData.activeFilter);
}

function updateTestsCount(testsCount) {
  successLi.textContent = testsCount.Success;
  failedLi.textContent = testsCount.Failed;
  errorLi.textContent = testsCount.Error;
  skippedLi.textContent = testsCount.Skipped;
  totalLi.textContent = testsCount.Total;
}

function updateCoverageInfo(coveragePercentage, coverageTitle) {
  coverageTitleEl.textContent = `${coverageTitle} Coverage`;
  coveragePercentageSpan.textContent = `${coveragePercentage}%`;
}

function updateSummaryBackground(backgroundColor) {
  headerSummary.className = headerSummary.className.replace(backgroundRegex, `bg-${backgroundColor}`);
}

function updateCoverageFilter(filterColor) {
  coveragePercentageSpan.className = coveragePercentageSpan.className.replace(filterRegex, filterColor);
}

function updateMarkAndBadge(background) {
  badgeImg.src = backgroundToSrc(background);
  badgeMark.className = badgeMark.className.replace(filterRegex, background);
}

function backgroundToSrc(background) {
  switch (background) {
    case "approved":
      return "assets/mark-approved.svg";
    default:
      return "assets/mark-rejected.svg";
  }
}

function getActiveFilter() {
  const active = document.querySelector("div#summary > ol > li.button-pressed");
  return active.getAttribute("data-type");
}
