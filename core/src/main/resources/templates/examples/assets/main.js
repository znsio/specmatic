const mainElement = document.querySelector("main");
const table = document.querySelector("table");
const backBtn = document.querySelector("button#back");
const pathSummaryUl = document.querySelector("ul#path-summary");
const examplesOl = document.querySelector("ol#examples");
const alerts = document.getElementById("alert-container");
const bulkValidateBtn = document.querySelector("button#bulk-validate");
const bulkGenerateBtn = document.querySelector("button#bulk-generate");

let scrollYPosition = 0;
let selectedTableRow = null;
const totalRows = table.querySelectorAll("tbody > tr").length;
let blockGenValidate = false;
const validationDetails = {}

backBtn.addEventListener("click", () => {
    mainElement.setAttribute("data-panel", "table");
    bulkValidateBtn.setAttribute("data-panel", "table");
    bulkGenerateBtn.setAttribute("data-panel", "table");
    window.scrollTo(0, scrollYPosition);
});

table.addEventListener("click", async (event) => {
    const target = event.target;
    const nearestTableRow = target.closest("tr");

    if (nearestTableRow) {
        selectedTableRow = nearestTableRow;
        const rowValues = extractRowValues(nearestTableRow);
        event.stopPropagation();

        switch (target.tagName) {
            case "BUTTON": {
                if (blockGenValidate) break;
                if (target.classList.contains("generate")) {
                    return await generateRowExamples(nearestTableRow, rowValues);
                }
                return await validateRowExamples(nearestTableRow);
            }

            case "INPUT": {
                const prevSelectCount = Number.parseInt(bulkValidateBtn.getAttribute("data-selected") || 0);

                if (target.matches("th > label > input")) {
                    toggleAllSelects(target.checked);
                    bulkGenerateBtn.setAttribute("data-selected", target.checked ? totalRows : 0);
                    return bulkValidateBtn.setAttribute("data-selected", target.checked ? totalRows : 0);
                }

                const nextSelectCount = prevSelectCount + (target.checked ? 1 : -1);
                bulkGenerateBtn.setAttribute("data-selected", nextSelectCount);
                return bulkValidateBtn.setAttribute("data-selected", nextSelectCount);
            }

            default: {
                if (!target.matches("tr > *:first-child, tr > *:first-child *")) {
                    return await goToDetails(nearestTableRow, rowValues);
                }
            }
        }
    }
});

examplesOl.addEventListener("click", (event) => {
    const target = event.target;
    const nearestLi = target.closest("li");

    if (nearestLi && target.matches(".example, .example *")) {
        const currentExpandState = nearestLi.getAttribute("data-expand");
        const newExpandState = currentExpandState === "true" ? "false" : "true";
        nearestLi.setAttribute("data-expand", newExpandState);
    }
});

bulkValidateBtn.addEventListener("click", async () => {
    bulkValidateBtn.setAttribute("data-valid", "processing");

    switch (bulkValidateBtn.getAttribute("data-panel")) {
        case "table": {
            await validateAllSelected();
            break;
        }

        case "details": {
            const result = await validateRowExamples(selectedTableRow);
            const {example, error} = await getExampleContent(result[0].absPath);
            const docFragment = createExampleRows([{
                absPath: result[0].absPath,
                exampleJson: example,
                error: validationDetails[Number.parseInt(selectedTableRow.firstElementChild.textContent)],
                hasBeenValidated: selectedTableRow.getAttribute("data-valid")
            }]);
            examplesOl.replaceChildren(docFragment);
            break;
        }
    }

    bulkValidateBtn.removeAttribute("data-valid");
});

bulkGenerateBtn.addEventListener("click", async () => {
    blockGenValidate = true;
    bulkGenerateBtn.setAttribute("data-generate", "processing");

    const tableRows = Array.from(table.querySelectorAll("td > input[type=checkbox]:checked")).map((checkbox) => checkbox.closest("tr"));
    const rowValues = tableRows.map((row) => extractRowValues(row));

    for (const row of tableRows) {
        row.setAttribute("data-generate", "processing");
    }

    const {examples, generatedCount, error} = await generateExamples(rowValues);

    if (error) {
        bulkGenerateBtn.removeAttribute("data-generate");
        return createAlert("Something went wrong", error, true);
    }

    createAlert("Examples Generated", `${generatedCount} were examples generated`, generatedCount === 0);

    for (let i = 0; i < tableRows.length; i++) {
        const tableRow = tableRows[i];
        const example = examples[i];
        const exampleColumn = tableRow.querySelector("td:nth-last-child(2)");

        if (example) storeExampleData(tableRow, [example]);
        enableValidateBtn(exampleColumn);
        tableRow.setAttribute("data-generate", example ? "success" : "failed");
        tableRow.removeAttribute("data-valid");
        exampleColumn.textContent = parseFileName(example);
    }

    bulkGenerateBtn.removeAttribute("data-generate");
    blockGenValidate = false;
});

async function validateAllSelected() {
    blockGenValidate = true;
    const selectedRows = Array.from(table.querySelectorAll("td > input[type=checkbox]:checked")).map((checkbox) => checkbox.closest("tr"));
    const rowsWithExamples = selectedRows.filter(row => row.getAttribute("data-generate") === "success");

    for (const row of rowsWithExamples) {
        row.setAttribute("data-valid", "processing");
    }

    const examples = rowsWithExamples.flatMap((row) => getExampleData(row))
    const { results, errorsCount, error } = await validateExamples(examples);

    if (error) {
        createAlert("Something went wrong", error, true);
    }

    createAlert("Validations Complete", `${errorsCount} out of ${results.length} are invalid`, errorsCount > 0);

    for (let i = 0; i < results.length; i++) {
        const result = results[i];
        const isValid = result.error === null;
        const rowId = Number.parseInt(rowsWithExamples[i].firstElementChild.textContent);

        rowsWithExamples[i].setAttribute("data-valid", isValid);
        validationDetails[rowId] = result.error;
    }

    blockGenValidate = false;
}

function extractRowValues(tableRow) {
    const [method, responseAndContentType] = [...tableRow.children].slice(2, 4).map((child) => child.textContent.trim());
    const [responseStatusCode, contentType] = responseAndContentType.split("\n").map((str) => str.trim());

    return { path: tableRow.getAttribute("data-raw-path"), method, responseStatusCode, contentType };
}

function toggleAllSelects(isSelected = true) {
    const checkboxes = table.querySelectorAll("input[type=checkbox]");
    for (const checkbox of checkboxes) {
        checkbox.checked = isSelected;
    }
}

function storeExampleData(tableRow, newExample) {
    tableRow.setAttribute("data-examples", newExample);
    return newExample;
}

function getExampleData(tableRow) {
    const existingData = tableRow.getAttribute("data-examples");
    return existingData;
}

async function generateRowExamples(tableRow, rowValues) {
    tableRow.setAttribute("data-generate", "processing");
    const { examples, error } = await generateExamples([rowValues]);

    if (error) {
        createAlert("Example Generation Failed", error, true);
        return tableRow.setAttribute("data-generate", "failed");
    }

    storeExampleData(tableRow, examples[0]);
    tableRow.setAttribute("data-generate", "success");
    createAlert("Example Generated", `Example name: ${parseFileName(examples[0])}`, false);

    const generateColumn = tableRow.querySelector("td:nth-last-child(2)")
    enableValidateBtn(generateColumn);
    generateColumn.textContent = parseFileName(examples[0]);
}

async function validateRowExamples(tableRow) {
    tableRow.setAttribute("data-valid", "processing");
    const allExamples = getExampleData(tableRow);

    if (allExamples.length === 0) {
        return [];
    }

    const { results, errorsCount, error } = await validateExamples([allExamples]);

    tableRow.setAttribute("data-valid", errorsCount === 0 && !error);
    validationDetails[Number.parseInt(tableRow.firstElementChild.textContent)] = results[0].error;

    if (error) {
        createAlert("Example Validation Failed", error, true);
    }

    return results;
}

async function goToDetails(tableRow, rowValues) {
    const exampleAbsPath = getExampleData(tableRow);
    let docFragment = [];

    if (exampleAbsPath) {
        const {example, error} = await getExampleContent(exampleAbsPath);
        docFragment = createExampleRows([{
            absPath: exampleAbsPath,
            exampleJson: example,
            error: validationDetails[Number.parseInt(tableRow.firstElementChild.textContent)],
            hasBeenValidated: tableRow.getAttribute("data-valid")
        }]);
    }

    pathSummaryUl.replaceChildren(createPathSummary(rowValues));
    examplesOl.replaceChildren(docFragment);
    mainElement.setAttribute("data-panel", "details");
    bulkValidateBtn.setAttribute("data-panel", "details");
    bulkGenerateBtn.setAttribute("data-panel", "details");
    scrollYPosition = window.scrollY;
    window.scrollTo(0, 0);
}

function createExampleRows(examples) {
    const docFragment = document.createDocumentFragment();
    for (const example of examples) {
        const exampleLi = document.createElement("li");
        exampleLi.setAttribute("data-expand", "false");
        exampleLi.appendChild(createExampleSummary(example));
        exampleLi.appendChild(createExampleDropDown(example));
        docFragment.appendChild(exampleLi);
    }

    return docFragment;
}

function createPathSummary(rowValues) {
    const docFragment = document.createDocumentFragment();

    for (const [key, value] of Object.entries(rowValues)) {
        if (!value) continue;

        const li = document.createElement("li");
        const keySpan = document.createElement("span");
        const valueSpan = document.createElement("span");

        keySpan.textContent = key;
        valueSpan.textContent = value;
        li.appendChild(keySpan);
        li.appendChild(valueSpan);

        docFragment.appendChild(li);
    }

    return docFragment;
}

function createExampleSummary(example) {
    const exampleDiv = document.createElement("div");
    const exampleName = document.createElement("p");
    const exampleBadge = document.createElement("span");

    exampleDiv.classList.add("example");
    exampleBadge.classList.add("pill", example.hasBeenValidated ? example.error ? "red" : "green" : "blue");

    exampleName.textContent = example.absPath;
    if (example.hasBeenValidated) {
        exampleBadge.textContent = `${example.error ? "Invalid" : "Valid"} Example`;
    } else {
        exampleBadge.textContent = "Generated Example";
    }

    exampleDiv.appendChild(exampleName);
    exampleDiv.appendChild(exampleBadge);
    return exampleDiv;
}

function createExampleDropDown(example) {
    const dropDownDiv = document.createElement("div");
    dropDownDiv.classList.add("dropdown");

    const examplePara = document.createElement("p");
    const detailsPara = document.createElement("p");

    examplePara.textContent = "Example: ";
    detailsPara.textContent = "Details: ";

    const examplePre = document.createElement("pre");
    const detailsPre = document.createElement("pre");

    examplePre.textContent = example.exampleJson;
    if (example.hasBeenValidated) {
        detailsPre.textContent = example.error ? example.error : `${parseFileName(example.absPath)} IS VALID`;
    } else {
        detailsPre.textContent = `${parseFileName(example.absPath)} HAS NOT YET BEEN VALIDATED`;
    }

    const examplePreDiv = document.createElement("div");
    const detailsPreDiv = document.createElement("div");

    detailsPreDiv.appendChild(detailsPara);
    detailsPreDiv.appendChild(detailsPre);

    examplePreDiv.appendChild(examplePara);
    examplePreDiv.appendChild(examplePre);

    dropDownDiv.appendChild(detailsPreDiv);
    dropDownDiv.appendChild(examplePreDiv);

    return dropDownDiv;
}

async function generateExamples(pathList) {
    try {
        const resp = await fetch("http://localhost:9001/_specmatic/examples/generate", {
            method: "POST",
            body: JSON.stringify(pathList),
            headers: {
                "Content-Type": "application/json",
            },
        });
        const data = await resp.json();

        if (data.generatedExamples.length === 0) {
            throw new Error("No examples were generated!");
        }

        if (data.generatedExamples.length !== pathList.length) {
            throw new Error("Not all examples were generated!");
        }

        const generatedCount = data.generatedExamples.filter(example => example !== null).length;
        return { examples: data.generatedExamples, generatedCount: generatedCount, error: data.error };
    } catch (error) {
        return { error: error.message, examples: [], generatedCount: 0 };
    }
}

async function validateExamples(exampleFiles) {
    try {
        const resp = await fetch("http://localhost:9001/_specmatic/examples/validate", {
            method: "POST",
            body: JSON.stringify({ exampleFiles }),
            headers: {
                "Content-Type": "application/json",
            },
        });

        const data = await resp.json();
        const errorsCount = data.filter((result) => result.error).length;

        return { results: data, errorsCount: errorsCount, error: data.error };
    } catch (error) {
        return { error: error.message, results: [], errorsCount: 0 };
    }
}

async function getExampleContent(example) {
    const exampleFileName = encodeURIComponent(example);
    try {
        const resp = await fetch(`http://localhost:9001/_specmatic/examples/content?fileName=${exampleFileName}`)
        const data = await resp.json();

        if (!resp.ok) {
            throw new Error(data.error);
        }

        return {example: data.content, error: null};
    } catch (e) {
        return {example: [], error: e.message}
    }

}

function parseFileName(absPath) {
    return absPath.split("\\").pop().split("/").pop().split(".").at(0);
}

function enableValidateBtn(generateBtn) {
    table.setAttribute("data-generated", "true");

    for (const child of generateBtn.nextElementSibling.children) {
        child.classList.remove("hidden");
    }

}

function createAlert(heading, message, error) {
    const alertBox = document.createElement("div");
    alertBox.classList.add("alert-msg", "slide-in", error ? "red" : "green");

    const alertTitle = document.createElement("p");
    alertTitle.textContent = heading;

    const alertMsg = document.createElement("pre");
    alertMsg.textContent = message;

    alertBox.appendChild(alertTitle);
    alertBox.appendChild(alertMsg);
    alerts.replaceChildren(alertBox);

    setTimeout(() => {
        alertBox.classList.add("slide-out");
        setTimeout(() => {
            alertBox.remove();
        }, 250);
    }, 3000);
}


(() => {
    for (const checkbox of table.querySelectorAll("input[type=checkbox]")) {
        checkbox.checked = false;
    }
})();