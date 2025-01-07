const mainElement = document.querySelector("main");
const table = document.querySelector("table");
const backBtn = document.querySelector("button#back");
const pathSummaryUl = document.querySelector("ul#path-summary");
const examplesOl = document.querySelector("ol#examples");
const alerts = document.getElementById("alert-container");
const bulkValidateBtn = document.querySelector("button#bulk-validate");
const bulkGenerateBtn = document.querySelector("button#bulk-generate");
const bulkTestBtn = document.querySelector("button#bulk-test");
const chevronDownIcon = document.querySelector("svg.chevron");
let setDecorationsEffect;
let decorationsField;
let savedEditorResponse = null;
let originalEditorText = null;
let scrollYPosition = 0;
let selectedTableRow = null;
let blockGenValidate = false;
const defaultAttrs = {
    "data-generate": "not-generated",
    "data-valid": "not-validated",
    "data-test": "not-tested",
    "data-main": "false"
}
const dataValidationSuccessValues = ["success", "partial"]
let isSaved = true;
let errorMetadata = [];

examplesOl.addEventListener("click", (event) => {
    const target = event.target;
    if (target.matches(".dropdown, .dropdown *")) {
        target.closest("div.details")?.classList.toggle("expanded");
        event.stopPropagation();
    }
})

backBtn.addEventListener("click", () => {
    if (!isSaved) {
        const modalContainer = createModal({
            onDiscard: () => {
                examplesOl.replaceChildren();
                mainElement.setAttribute("data-panel", "table");
                bulkValidateBtn.setAttribute("data-panel", "table");
                bulkGenerateBtn.setAttribute("data-panel", "table");
                bulkTestBtn.setAttribute("data-panel", "table");
                window.scrollTo(0, scrollYPosition);
                console.log("Changes discarded!");
                isSaved = true;
            },

        });
        document.body.appendChild(modalContainer);
        return;
    }

    examplesOl.replaceChildren();
    mainElement.setAttribute("data-panel", "table");
    bulkValidateBtn.setAttribute("data-panel", "table");
    bulkGenerateBtn.setAttribute("data-panel", "table");
    bulkTestBtn.setAttribute("data-panel", "table");
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
                switch (target.getAttribute("aria-label")) {
                    case "Generate":
                    case "Generate More": {
                        return await generateRowExamples(nearestTableRow, rowValues);
                    }
                    case "Validate": {
                        return await validateRowExamples(nearestTableRow);
                    }
                    default: {
                        return await testRowExample(nearestTableRow);
                    }
                }
            }

            case "INPUT": {
                if (target.matches("th > label > input")) {
                    const {validatedCount, generatedCount, discAndMainCount, totalCount} = getRowsCount();
                    toggleAllSelects(target.checked);

                    if (table.hasAttribute("data-generated")) {
                        bulkValidateBtn.setAttribute("data-selected", target.checked ? generatedCount : 0);
                        bulkTestBtn.setAttribute("data-selected", target.checked ? validatedCount : 0);
                    }

                    return bulkGenerateBtn.setAttribute("data-selected", target.checked ? totalCount - generatedCount + discAndMainCount : 0);
                }

                return handleSingleInput(nearestTableRow, target.checked);
            }

            default: {
                if (!target.matches("tr > *:first-child, tr > *:first-child *") && nearestTableRow.getAttribute("data-generate") === "success") {
                    return await goToDetails(nearestTableRow, rowValues);
                }
            }
        }
    }
});

function handleSingleInput(nearestTableRow, checked) {
    const getPreSelectCount = (btn, attr) => Number.parseInt(btn.getAttribute(attr) || 0);

    const valPreSelectCount = getPreSelectCount(bulkValidateBtn, "data-selected");
    const genPreSelectCount = getPreSelectCount(bulkGenerateBtn, "data-selected");
    const testPreSelectCount = getPreSelectCount(bulkTestBtn, "data-selected");

    const hasBeenGenerated = nearestTableRow.getAttribute("data-generate") === "success";
    const hasBeenValidated = dataValidationSuccessValues.includes(nearestTableRow.getAttribute("data-valid"));
    const isDiscriminatorRow = nearestTableRow.getAttribute("data-disc") === "true";
    const isMainRow = nearestTableRow.getAttribute("data-main") === "true";

    const countAdjustment = checked ? 1 : -1;

    if (hasBeenGenerated) {
        bulkValidateBtn.setAttribute("data-selected", valPreSelectCount + countAdjustment);
        if (isDiscriminatorRow && isMainRow) {
            bulkGenerateBtn.setAttribute("data-selected", genPreSelectCount + countAdjustment);
        }
        if (hasBeenValidated) {
            bulkTestBtn.setAttribute("data-selected", testPreSelectCount + countAdjustment);
        }
    } else if (isMainRow) {
        bulkGenerateBtn.setAttribute("data-selected", genPreSelectCount + countAdjustment);
    }
}

bulkValidateBtn.addEventListener("click", async () => {
    blockGenValidate = true;
    bulkValidateBtn.setAttribute("data-valid", "processing");

    switch (bulkValidateBtn.getAttribute("data-panel")) {
        case "table": {
            await validateAllSelected();
            break;
        }

        case "details": {
            await validateRowExamples(selectedTableRow);
            const originalYScroll = scrollYPosition;
            await goToDetails(selectedTableRow, extractRowValues(selectedTableRow));
            scrollYPosition = originalYScroll;
            break;
        }
    }

    bulkValidateBtn.removeAttribute("data-valid");
    return cleanUpSelections();
});

bulkGenerateBtn.addEventListener("click", async () => {
    blockGenValidate = true;
    bulkGenerateBtn.setAttribute("data-generate", "processing");

    let failedCount = 0;
    createdCount = 0;
    existedCount = 0;
    const selectedRows = Array.from(table.querySelectorAll("td > input[type=checkbox]:checked")).map((checkbox) => checkbox.closest("tr"));
    const rowsWithNoExamplesOrDiscriminator = selectedRows.filter(row =>
        row.getAttribute("data-main") === "true" && (row.getAttribute("data-generate") === defaultAttrs["data-generate"] || row.getAttribute("data-disc") === "true")
    );

    for (const row of rowsWithNoExamplesOrDiscriminator) {
        const rowValues = extractRowValues(row);
        const {success, created, existed, count} = await generateRowExamples(row, rowValues, true);

        if (!success) failedCount += count;
        createdCount += created;
        existedCount += existed;
    }

    bulkGenerateBtn.removeAttribute("data-generate");
    const message = `${createdCount} Example(s) Created\n${existedCount} Example(s) Existed\n${failedCount} Example(s) Failed`
    const title = `Generation Complete (${createdCount + existedCount + failedCount}) Example(s)`;
    createAlert(title, message, failedCount !== 0);
    return cleanUpSelections();
});

async function validateAllSelected() {
    bulkValidateBtn.setAttribute("data-valid", "processing");

    let errorsCount = 0;
    const selectedRows = Array.from(table.querySelectorAll("td > input[type=checkbox]:checked")).map((checkbox) => checkbox.closest("tr"));
    const rowsWithExamples = selectedRows.filter(row => row.getAttribute("data-generate") === "success");

    for (const row of rowsWithExamples) {
        const success = await validateRowExamples(row, true);
        if (!success) {
            errorsCount++;
        }
    }

    bulkValidateBtn.removeAttribute("data-generate");
    createAlert("Validations Complete", `${errorsCount} out of ${rowsWithExamples.length} are invalid`, errorsCount !== 0);
    return cleanUpSelections();
}

bulkTestBtn.addEventListener("click", async () => {
    blockGenValidate = true;
    bulkTestBtn.setAttribute("data-test", "processing");

    switch (bulkValidateBtn.getAttribute("data-panel")) {
        case "table": {
            await testAllSelected();
            break;
        }

        case "details": {
            await testRowExample(selectedTableRow);
            const originalYScroll = scrollYPosition
            await goToDetails(selectedTableRow, extractRowValues(selectedTableRow));
            scrollYPosition = originalYScroll;
            break;
        }
    }

    bulkTestBtn.removeAttribute("data-test");
    return cleanUpSelections();
});

async function testAllSelected() {
    const selectedRows = Array.from(table.querySelectorAll("td > input[type=checkbox]:checked")).map((checkbox) => checkbox.closest("tr"));
    const rowsWithValidations = selectedRows.filter(row => dataValidationSuccessValues.includes(row.getAttribute("data-valid")));

    for (const row of rowsWithValidations) {
        row.setAttribute("data-test", "processing");
    }

    let failureCount = 0;
    for (const row of rowsWithValidations) {
        const result = await testRowExample(row, true);

        if (!result) {
            failureCount++;
        }
    }

    blockGenValidate = false;
    createAlert("Tests Complete", `${failureCount} out of ${rowsWithValidations.length} have failed`, failureCount !== 0);
}

function extractRowValues(tableRow) {
    const [method, responseAndContentType] = [...tableRow.children].slice(3, 5).map((child) => child.textContent.trim());
    const [responseStatusCode, contentType] = responseAndContentType.split("\n").map((str) => str.trim());
    const isSchemaBased = tableRow.getAttribute("data-schema-based") === "true"

    return {path: tableRow.getAttribute("data-raw-path"), method, responseStatusCode, contentType, isSchemaBased};
}

function getRowsCount() {
    const tableRows = table.querySelectorAll("tbody > tr");
    return Array.from(tableRows).reduce((acc, row) => {
        const isRowGenerated = row.getAttribute("data-generate") === "success";
        const isRowValidated = dataValidationSuccessValues.includes(row.getAttribute("data-valid"));
        const isRowDiscAndMain = row.getAttribute("data-main") === "true" && row.getAttribute("data-disc") === "true";

        acc.validatedCount += isRowValidated ? 1 : 0;
        acc.generatedCount += isRowGenerated ? 1 : 0;
        acc.discAndMainCount += isRowGenerated && isRowDiscAndMain ? 1 : 0;
        acc.totalCount += 1;
        return acc;

    }, {validatedCount: 0, generatedCount: 0, discAndMainCount: 0, totalCount: 0});
}

function toggleAllSelects(isSelected = true) {
    const checkboxes = table.querySelectorAll("input[type=checkbox]");
    for (const checkbox of checkboxes) {
        checkbox.checked = isSelected;
    }
}

async function generateRowExamples(tableRow, rowValues, bulkMode = false) {
    const originalState = tableRow.getAttribute("data-generate");

    tableRow.setAttribute("data-generate", "processing");
    const {examples, error} = await generateExample(rowValues, bulkMode);
    tableRow.setAttribute("data-generate", originalState);

    if (error) {
        if (!bulkMode) createAlert("Example Generation Failed", error, true);
        tableRow.setAttribute("data-generate", originalState === defaultAttrs["data-generate"] ? "failed" : originalState);
        return {success: false, created: 0, existed: 0, count: 0};
    }

    const {createdCount, existedCount, totalCount} = getExamplesCount(examples);
    const newExamples = getOnlyNewExamples(tableRow, examples);
    const thisRowIsGenerated = tableRow.getAttribute("data-generate") === "success";
    const newRows = newExamples.map((ex, idx) => updateRowWithExample(tableRow, thisRowIsGenerated || idx > 0, ex));

    const rowsToBeAdded = newRows.filter((row, idx) => idx > 0 || thisRowIsGenerated);
    const exampleFragment = document.createDocumentFragment();
    rowsToBeAdded.forEach(row => exampleFragment.appendChild(row))

    requestAnimationFrame(() => {
        rowsToBeAdded.forEach(row => tableRow.parentElement.insertBefore(row, tableRow.nextSibling));
        updateSpans(tableRow, rowValues, rowsToBeAdded.length);
    })

    if (!bulkMode) {
        const alertTitle = `Example(s) Generated, ${existedCount} already existed`;
        const alertMessage = `Example name(s): ${newExamples.map(example => parseFileName(example.exampleFilePath)).join("\n")}`;
        createAlert(alertTitle, alertMessage, false)
    }
    ;

    return {success: true, created: createdCount, existed: existedCount, count: totalCount};
}

async function validateRowExamples(tableRow, bulkMode = false) {
    tableRow.setAttribute("data-valid", "processing");
    const exampleData = getExampleData(tableRow);

    if (!isSaved) {
        const exampleSaved = await saveExample(exampleData);
        if (!exampleSaved) {
            return false;
        }
    }

    const {exampleAbsPath, errorMessage, errorList, isPartialFailure} = await validateExample(exampleData);

    if (errorMessage && !exampleAbsPath) {
        if (!bulkMode) createAlert("Validation Failed", `Error: ${error ?? "Unknown Error"}`, true);
        tableRow.setAttribute("data-valid", "failed");
        return false;
    }

    tableRow.setAttribute("data-valid", errorMessage ? isPartialFailure ? "partial" : "failed" : "success");
    storeExampleValidationData(tableRow, {errorMessage, errorList});
    storeExampleTestData(tableRow, null);
    tableRow.setAttribute("data-test", defaultAttrs["data-test"]);

    if (errorMessage && !isPartialFailure) {
        if (!bulkMode) createAlert("Invalid Example", `Example name: ${parseFileName(exampleAbsPath)}`, true);
        return false;
    }

    if (!bulkMode) createAlert("Valid Example", `Example name: ${parseFileName(exampleAbsPath)}`, false);
    return true;
}

async function testRowExample(tableRow, bulkMode = false) {
    tableRow.setAttribute("data-test", "processing");

    const isExampleValid = await validateRowExamples(tableRow, bulkMode);
    if (!isExampleValid) {
        createAlert("Invalid Example or Network Failure", `Example name: ${parseFileName(getExampleData(tableRow))}`, true);
        return false;
    }

    const exampleData = getExampleData(tableRow);
    const {data, error} = await testExample({
        exampleFile: exampleData
    });

    if (error && !data) {
        if (!bulkMode) createAlert("Testing Failed", `Error: ${error ?? "Unknown Error"}`, true);
        tableRow.setAttribute("data-test", "failed");
        return false;
    }

    tableRow.setAttribute("data-test", (error || data?.result !== "Success") ? "failed" : "success");
    storeExampleTestData(tableRow, data);

    if (error || data?.result !== "Success") {
        if (!bulkMode) createAlert(`Test ${data ? data.result : "Failed"}`, `Example name: ${parseFileName(exampleData)}`, true);
        return false;
    }

    if (!bulkMode) createAlert("Test Passed", `Example name: ${parseFileName(exampleData)}`, false);
    return true;
}

// ACTION HELPERS
function getExamplesCount(examples) {
    return examples.reduce((counts, example) => {
        if (example.created) {
            counts.createdCount++;
        } else if (example.existed) {
            counts.existedCount++;
        }
        return counts;
    }, {createdCount: 0, existedCount: 0, totalCount: examples.length});
}

function getOnlyNewExamples(tableRow, examples) {
    const existingExamples = new Set(getAllSameGroupExamples(tableRow));
    return examples.filter(example => !existingExamples.has(example.exampleFilePath));
}

function updateRowWithExample(tableRow, shouldClone, example) {
    const newRow = shouldClone ? tableRow.cloneNode(true) : tableRow;
    storeExampleData(newRow, example);
    insertExampleIntoRow(example, newRow);

    if (shouldClone) {
        Object.entries(defaultAttrs).forEach(([attr, value]) => newRow.setAttribute(attr, value));
        Array.from(newRow.children).slice(2, -2).forEach(cell => cell.classList.add("hidden"));
    }

    newRow.setAttribute("data-generate", "success");
    enableValidateBtn(newRow);
    return newRow;
}

function insertExampleIntoRow(example, tableRow) {
    const exampleSpan = document.createElement("span");
    exampleSpan.textContent = parseFileName(example.exampleFilePath);
    const generateColumn = tableRow.querySelector("td:nth-last-child(2)")
    generateColumn.replaceChildren(exampleSpan);
}

function updateSpans(tableRow, rowValues, increment) {
    const origMainValues = Object.values(rowValues)
    let curRow = tableRow;

    while (curRow) {
        const curRowValues = Object.values(extractRowValues(curRow));
        if (curRowValues[0] !== origMainValues[0]) break;

        const cells = Array.from(curRow.children).slice(2, -2);
        let isMatchingCell = true;

        for (let i = 0; i < cells.length; i++) {
            isMatchingCell = isMatchingCell && curRowValues[i] === origMainValues[i];
            if (i == cells.length - 1) {
                isMatchingCell = isMatchingCell && curRowValues[i + 1] == origMainValues[i + 1];
            }
            if (isMatchingCell) {
                cells[i].rowSpan += increment;
            }
        }

        curRow = curRow.previousElementSibling;
    }
}

async function goToDetails(tableRow, rowValues) {
    const exampleAbsPath = getExampleData(tableRow);
    let docFragment = [];

    if (exampleAbsPath) {
        const {example, error} = await getExampleContent(exampleAbsPath);
        const {errorList, errorMessage} = getExampleValidationData(tableRow) || {errorList: null, errorMessage: null};
        docFragment = createExampleRows([{
            absPath: exampleAbsPath,
            exampleJson: example,
            errorList: errorList,
            errorMessage: error || errorMessage,
            hasBeenValidated: tableRow.getAttribute("data-valid") !== defaultAttrs["data-valid"],
            isPartialFailure: tableRow.getAttribute("data-valid") === "partial",
            test: getExampleTestData(tableRow)
        }]);
        originalEditorText = example;
    }

    bulkTestBtn.classList.toggle("bulk-disabled", tableRow.getAttribute("data-valid") !== "success")
    pathSummaryUl.replaceChildren(createPathSummary(rowValues));
    examplesOl.replaceChildren(docFragment);
    mainElement.setAttribute("data-panel", "details");
    bulkValidateBtn.setAttribute("data-panel", "details");
    bulkGenerateBtn.setAttribute("data-panel", "details");
    bulkTestBtn.setAttribute("data-panel", "details");
    scrollYPosition = window.scrollY;
    window.scrollTo(0, 0);
}

function createExampleRows(examples) {
    const docFragment = document.createDocumentFragment();
    for (const example of examples) {
        const exampleLi = document.createElement("li");
        exampleLi.appendChild(createExampleSummary(example));
        exampleLi.appendChild(createExampleDropDown(example));
        docFragment.appendChild(exampleLi);
    }

    return docFragment;
}

function createPathSummary(rowValues) {
    const docFragment = document.createDocumentFragment();

    if (rowValues.isSchemaBased) {
        rowValues = {"schema": rowValues["path"], ...rowValues};
        delete rowValues["path"];
        delete rowValues["isSchemaBased"];
    }

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
    const testBadge = document.createElement("span");

    exampleDiv.classList.add("example");
    exampleBadge.classList.add("pill", example.hasBeenValidated ? (example.errorMessage ? (example.isPartialFailure ? "yellow" : "red") : "green") : "blue");
    exampleName.textContent = example.absPath;

    if (example.hasBeenValidated) {
        exampleBadge.textContent = example.errorMessage ? example.isPartialFailure ? "Valid" : "Invalid" : "Valid";
        exampleBadge.textContent += " Example";
    } else {
        exampleBadge.textContent = "Example";
    }

    const expandDiv = document.createElement("div");
    expandDiv.classList.add("expand-info");
    expandDiv.appendChild(exampleBadge);

    if (example.test) {
        console.log(example.test);
        testBadge.classList.add("pill", example.test.result === "Success" ? "green" : "red");
        testBadge.textContent = `Test ${example.test.result}`;
        expandDiv.appendChild(testBadge);
    }

    exampleDiv.appendChild(exampleName);
    exampleDiv.appendChild(expandDiv);
    return exampleDiv;
}

function createExampleDropDown(example) {
    const detailsDiv = document.createElement("div");
    detailsDiv.classList.add("details");

    const detailsDropdown = document.createElement("div");
    detailsDropdown.classList.add("dropdown");
    const detailsPara = document.createElement("p");
    detailsDropdown.appendChild(detailsPara);
    detailsDropdown.appendChild(chevronDownIcon.cloneNode(true));

    const detailsPre = document.createElement("pre");
    const examplePara = document.createElement("p");
    examplePara.textContent = "Example: ";

    if (example.errorMessage) {
        const issueCount = example.errorList.length;
        const issueOrIssues = issueCount === 1 ? "issue" : "issues";
        detailsPara.textContent = `Example has ${issueCount || ""} ${issueOrIssues}`;
        if (issueCount > 0) {
            detailsPara.style.color = "red";
        }
    } else {
        detailsPara.textContent = example.hasBeenValidated ? "Example has no errors" : "Example has not yet been validated";
    }

    if (example.hasBeenValidated) {
        detailsPre.textContent = example.errorMessage ? example.errorMessage : `${parseFileName(example.absPath)} IS VALID`;
        if (example.test) {
            detailsPre.textContent = `${detailsPre.textContent}\n${example.test.details}`;
        }
    } else {
        detailsPre.textContent = `${parseFileName(example.absPath)} HAS NOT YET BEEN VALIDATED`;
    }

    const examplePreDiv = document.createElement("div");
    const detailsFragment = document.createDocumentFragment();

    examplePreDiv.setAttribute("id", "example-pre");
    examplePreDiv.classList.add("language-json");
    detailsFragment.appendChild(detailsDropdown);
    detailsFragment.appendChild(detailsPre);

    setDecorationsEffect = window.StateEffect.define();
    decorationsField = window.StateField.define({
        create() {
            return window.Decoration.none;
        },
        update(decorations, transaction) {
            for (let effect of transaction.effects) {
                if (effect.is(setDecorationsEffect)) {
                    return effect.value;
                }
            }
            return decorations;
        },
        provide: field => window.EditorView.decorations.from(field)
    });

    const editorFacet = window.EditorView.theme({
        "&": {
            fontSize: "16px",
            lineHeight: "1.5",
        },
    });

    const editor = new window.EditorView({
        state: window.EditorState.create({
            doc: example.exampleJson,
            extensions: [
                window.basicSetup,
                window.autocompletion,
                window.json,
                window.linter,
                window.lintGutter,
                window.lineNumbers,
                window.oneDark,
                decorationsField,
                editorFacet,
                window.EditorView.updateListener.of((update) => {
                  if (!update.docChanged) return;
                  const docContent = update.state.doc.toString();

                  isSaved = false;
                  const editorElement = editor.dom;
                  updateBorderColorExampleBlock(editorElement, examplePreDiv);
                  savedEditorResponse = docContent;
                  if (!example.errorList?.length > 0) return;
                  highlightErrorLines(editor, example.errorList, docContent);
                })
            ],
        }),
        parent: examplePreDiv
    });


    if (example.errorList && example.errorList.length > 0) {
        highlightErrorLines(editor, example.errorList, example.exampleJson);
    }


    if (example.test) {
        const testPara = document.createElement("p");
        testPara.textContent = "Test Log: ";
        const testPre = document.createElement("pre");
        testPre.textContent = example.test.testLog;
        detailsFragment.appendChild(testPara);
        detailsFragment.appendChild(testPre);
    }

    detailsDiv.appendChild(detailsFragment);
    detailsDiv.appendChild(examplePara);

    const fragment = document.createDocumentFragment();
    if (example.errorMessage) {
        fragment.appendChild(detailsDiv);
    }
    fragment.appendChild(examplePreDiv);

    return fragment;
}

async function saveExample(examplePath) {
    const editedText = savedEditorResponse;
    try {
        const parsedContent = JSON.parse(editedText);

        const response = await fetch(`${getHostPort()}/_specmatic/examples/update`, {
            method: "POST",
            headers: {
                "Content-Type": "application/json",
            },
            body: JSON.stringify({
                exampleFile: examplePath,
                exampleContent: editedText,
            }),
        });

        if (response.ok) {
            createAlert("Saved", "Example saved to file", false);
            isSaved = true;
            return true;
        } else {
            const errorMessage = await response.text();
            createAlert("Failed to save example.", `Failed to save example to ${examplePath}: ${errorMessage}`, true);
            console.error("Error saving example:", response.status);
            savedEditorResponse = originalEditorText;
            return false;
        }
    } catch (e) {
        console.error("Error during save request:", e);
        createAlert("Failed to save example.", `An error occurred while saving example to ${examplePath}: ${e.message}`, true);
        savedEditorResponse = originalEditorText;
        return false;
    }
}


function updateBorderColorExampleBlock(editorElement, examplePreDiv) {
    try {
        JSON.parse(editorElement.state.doc.toString());
        editorElement.style.borderWidth = "2px";
        editorElement.style.borderStyle = "solid";
        editorElement.style.borderColor = "green";
    } catch (e) {
        editorElement.style.borderWidth = "2px";
        editorElement.style.borderStyle = "solid";
        editorElement.style.borderColor = "red";
    }
}


function highlightErrorLines(editor, metadata, exampleJson) {
    const {data, pointers} = jsonMapParser(exampleJson);
    let decorations = [];
    const existingMarkers = new Map();
    const errorLines = [];
    errorMetadata = [];

    metadata.forEach(meta => {
        var location = findObjectByPath(pointers, meta.jsonPath);
        if (location == null) {
            meta.jsonPath = meta.jsonPath.substring(0, meta.jsonPath.lastIndexOf('/'));
            location = findObjectByPath(pointers, meta.jsonPath);
        }
        const lineNumber = location.key ? location.key.line : (location.value ? location.value.line : null);

        if (lineNumber !== null) {
            const lineLength = editor.state.doc.line(lineNumber + 1)
            if (!existingMarkers.has(lineNumber)) {
                existingMarkers.set(lineNumber, []);
                errorLines.push(lineNumber);
            }
            existingMarkers.get(lineNumber).push(meta.description);
            const combinedDescriptions = existingMarkers.get(lineNumber).join('\n\n');
            const className = meta.isPartial ? "specmatic-editor-line-warning": "specmatic-editor-line-error";
            const tokenStart = lineLength.from;
            const tokenEnd = lineLength.to;

            const existingError = errorMetadata.find(err => err.line === lineNumber + 1);
            if (existingError) {
                existingError.message = combinedDescriptions;
            } else {
                errorMetadata.push({
                    line: lineNumber + 1,
                    message: combinedDescriptions,
                    isPartial: meta.isPartial
                });
            }
             const existingDecoration = decorations.filter(decoration => decoration.from === tokenStart && decoration.to === tokenEnd);
             if (existingDecoration.length !== 0) return;
               decorations.push(
                  window.Decoration.mark({
                    class: className,
                     attributes: {
                    "data-validation-error-message": combinedDescriptions
                  }
                }).range(tokenStart, tokenEnd)
               );

        }
    });
    decorations.sort((a, b) => a.from - b.from);

    const decorationSet = window.Decoration.set(decorations);
    const transaction = editor.state.update({
        effects: setDecorationsEffect.of(decorationSet)
    });

    editor.dispatch(transaction);
    const errorTooltipExtension = createErrorTooltipExtension(errorMetadata);
    editor.dispatch({
        effects: setDecorationsEffect.of(decorationSet),
    });
    editor.dispatch({
        effects: window.StateEffect.appendConfig.of([errorTooltipExtension]),
    });

}

function findObjectByPath(pointers, patch) {
    for (const path in pointers) {
        if (path === patch) {
            return pointers[path];
        }
    }
    return null;
}

const createErrorTooltipExtension = (errorMetadata) => {
    return window.hoverTooltip((view, pos) => {
        const line = view.state.doc.lineAt(pos);
        const error = errorMetadata.find(err => err.line === line.number);
        const lineCoords = view.coordsAtPos(pos);
        const numberOfPixelsAboveDefaultOffset = 120;
        const numberOfPixelsLeftOfDefaultToolTipOrigin = 20;
        if (error) {
            return {
                pos,
                above: true,
                create: () => {
                    const tooltip = document.createElement("div");
                    tooltip.textContent = error.message;
                    tooltip.innerHTML = error.message.replace(/\n/g, "<br>");
                    tooltip.className = "specmatic-editor-tooltip";
                    tooltip.style.borderLeft = error.isPartial ? "4px solid yellow" : "4px solid red";
                    tooltip.style.top = `${lineCoords.top + window.scrollY - tooltip.offsetHeight - numberOfPixelsAboveDefaultOffset}px`;
                    tooltip.style.left = `${lineCoords.left + numberOfPixelsLeftOfDefaultToolTipOrigin}px`;
                    return {dom: tooltip};
                },
            };
        }
        return null;
    });
};

function parseFileName(path) {
    return path.split('/').pop();
}

function storeExampleData(tableRow, example) {
    const key = tableRow.getAttribute("data-key");
    tableRow.setAttribute("data-example", example.exampleFilePath);
    exampleDetails[key][example.exampleFilePath] = null;
    return example;
}

function getExampleData(tableRow) {
    return tableRow.getAttribute("data-example");
}

function getAllSameGroupExamples(tableRow) {
    const rowKey = tableRow.getAttribute("data-key");
    const examplePairs = exampleDetails[rowKey];
    return Object.keys(examplePairs).filter(value => value !== "null");
}

function storeExampleValidationData(tableRow, result) {
    const key = tableRow.getAttribute("data-key");
    exampleDetails[key][tableRow.getAttribute("data-example")] = result;
}

function getExampleValidationData(tableRow) {
    const key = tableRow.getAttribute("data-key");
    return exampleDetails[key][getExampleData(tableRow)];
}

function storeExampleTestData(tableRow, data) {
    const key = tableRow.getAttribute("data-key");
    const exampleData = getExampleData(tableRow);

    if (!testDetails[key]) {
        testDetails[key] = {};
    }

    testDetails[key][exampleData] = data;
}

function getExampleTestData(tableRow) {
    const key = tableRow.getAttribute("data-key");

    if (!testDetails[key]) {
        return null;
    }

    return testDetails[key][getExampleData(tableRow)];
}

async function generateExample(pathInfo, bulkMode) {
    try {
        const resp = await fetch(`${getHostPort()}/_specmatic/examples/generate`, {
            method: "POST",
            body: JSON.stringify({...pathInfo, bulkMode}),
            headers: {
                "Content-Type": "application/json",
            }
        });
        const data = await resp.json();

        if (!resp.ok) {
            throw new Error(data.error);
        }

        return {examples: data.examples, error: data.error};
    } catch (error) {
        return {error: error.message, examples: null};
    }
}

async function validateExample(exampleFile) {
    try {
        const resp = await fetch(`${getHostPort()}/_specmatic/examples/validate`, {
            method: "POST",
            body: JSON.stringify({exampleFile}),
            headers: {
                "Content-Type": "application/json",
            }
        });
        const data = await resp.json();

        if (!resp.ok) {
            throw new Error(data.errorMessage);
        }

        return {
            exampleAbsPath: data.absPath,
            errorList: data.errorList,
            errorMessage: data.errorMessage,
            isPartialFailure: data.isPartialFailure
        };
    } catch (error) {
        return {errorMessage: error.message, exampleAbsPath: null, isPartialFailure: false, errorList: null};
    }
}

async function getExampleContent(example) {
    const exampleFileName = encodeURIComponent(example);
    try {
        const resp = await fetch(`${getHostPort()}/_specmatic/examples/content?fileName=${exampleFileName}`)
        const data = await resp.json();

        if (!resp.ok) {
            throw new Error(data.error);
        }

        return {example: data.content, error: null};
    } catch (e) {
        return {example: null, error: e.message}
    }
}

async function testExample(exampleData) {
    try {
        const resp = await fetch(`${getHostPort()}/_specmatic/examples/test`, {
            method: "POST",
            body: JSON.stringify(exampleData),
            headers: {
                "Content-Type": "application/json",
            }
        });
        const data = await resp.json();

        if (!resp.ok) {
            throw new Error(data.error);
        }

        return {data, error: data.error}
    } catch (error) {
        return {data: null, error: error.message}
    }
}

function parseFileName(absPath) {
    const fileName = absPath.split("\\").pop().split("/").pop();
    const lastIndex = fileName.lastIndexOf(".");
    return fileName.slice(0, lastIndex);
}

function getHostPort() {
    const hostPort = table.getAttribute("data-hostPort");
    return hostPort;
}

function enableValidateBtn(tableRow) {
    table.setAttribute("data-generated", "true");
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
        if (!alerts.matches(":hover")) {
            removeAlertBox(alertBox);
        } else {
            const handleMouseLeave = () => {
                removeAlertBox(alertBox);
                alerts.removeEventListener("mouseleave", handleMouseLeave);
            };
            alerts.addEventListener("mouseleave", handleMouseLeave);
        }
    }, 3000);
}

function removeAlertBox(alertBox) {
    alertBox.classList.add("slide-out");
    setTimeout(() => {
        alertBox.remove();
    }, 250);
}

function cleanUpSelections() {
    requestAnimationFrame(() => {
        for (const checkbox of table.querySelectorAll("input[type=checkbox]")) {
            checkbox.checked = false;
        }
    });
    bulkGenerateBtn.setAttribute("data-selected", "0");
    bulkValidateBtn.setAttribute("data-selected", "0");
    bulkTestBtn.setAttribute("data-selected", "0");
    blockGenValidate = false;
}

function createModal({onSave, onDiscard}) {
    // Data for modal
    const title = "Unsaved Changes";
    const closeText = "No";
    const discardText = "Yes";
    const bodyText = "Your changes are not saved & validated. To save your changes click on the Save & Validate button. Discard changes?";

    // Create modal container dynamically
    const modalContainer = document.createElement("div");
    modalContainer.classList.add("go-back-modal");
    modalContainer.style.position = "fixed";
    modalContainer.style.top = "0";
    modalContainer.style.left = "0";
    modalContainer.style.width = "100%";
    modalContainer.style.height = "100%";
    modalContainer.style.backgroundColor = "rgba(0, 0, 0, 0.5)";
    modalContainer.style.display = "flex";
    modalContainer.style.alignItems = "center";
    modalContainer.style.justifyContent = "center";
    modalContainer.style.zIndex = "9999";

    // Create modal content box
    const modalContent = document.createElement("div");
    modalContent.style.backgroundColor = "#fff";
    modalContent.style.borderRadius = "8px";
    modalContent.style.padding = "20px";
    modalContent.style.width = "400px";
    modalContent.style.boxShadow = "0 4px 8px rgba(0, 0, 0, 0.2)";
    modalContent.style.textAlign = "center";

    // Create modal header
    const modalHeader = document.createElement("div");
    const modalTitle = document.createElement("h5");
    modalTitle.textContent = title;
    modalHeader.style.fontSize = "18px";
    modalHeader.style.fontWeight = "bold";
    modalHeader.style.marginBottom = "15px";
    modalHeader.appendChild(modalTitle);

    // Create modal body
    const modalBody = document.createElement("div");
    modalBody.textContent = bodyText;
    modalBody.style.marginBottom = "15px";

    // Create modal footer
    const modalFooter = document.createElement("div");

    const closeButton = document.createElement("button");
    closeButton.textContent = closeText;
    closeButton.style.backgroundColor = "#007bff";
    closeButton.style.color = "#fff";
    closeButton.style.border = "none";
    closeButton.style.padding = "8px 16px";
    closeButton.style.borderRadius = "4px";
    closeButton.style.cursor = "pointer";
    closeButton.style.marginRight = "10px";

    const discardButton = document.createElement("button");
    discardButton.textContent = discardText;
    discardButton.style.backgroundColor = "#007bff";
    discardButton.style.color = "#fff";
    discardButton.style.border = "none";
    discardButton.style.padding = "8px 16px";
    discardButton.style.borderRadius = "4px";
    discardButton.style.cursor = "pointer";

    // Add buttons to footer
    modalFooter.appendChild(closeButton);
    modalFooter.appendChild(discardButton);

    // Append everything to the modal content
    modalContent.appendChild(modalHeader);
    modalContent.appendChild(modalBody);
    modalContent.appendChild(modalFooter);

    // Append modal content to modal container
    modalContainer.appendChild(modalContent);


    // Button event listeners
    closeButton.addEventListener("click", () => {
        closeModal();
    });

    discardButton.addEventListener("click", () => {
        onDiscard();
        closeModal();
    });


    modalContainer.addEventListener("click", (event) => {
        if (event.target === modalContainer) {
            closeModal();
        }
    });


    function closeModal() {
        modalContainer.remove();
    }

    return modalContainer;
}

(() => cleanUpSelections())();