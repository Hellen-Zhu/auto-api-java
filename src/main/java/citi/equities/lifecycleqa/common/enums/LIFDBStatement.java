package citi.equities.lifecycleqa.common.enums;

public enum LIFDBStatement {
    InsertAutoProgressFromAudit(false, -1, -1),
    InsertAutoCaseAudit(false, -1, -1),
    UpdateAutoProgress(false, -1, -1),
    UpdateAutoCaseAudit(false, -1, -1),
    DeleteAutoCasesByIdList(false, -1, -1),
    InsertAutoCase(false, -1, -1),
    UpdateAutoCasePassrate(false, -1, -1),
    FetchSingleAutoCaseAuditDetail(true, -1, -1),
    FetchAutoCaseAuditBaseOnComponentForAPI(true, -1, -1),
    FetchFinalFeature(true, -1, -1),
    FetchAutoCaseAuditBaseOnComponentForUI(true, -1, -1),
    FetchAutoConfigurationBasedOnSingleComponent(true, 1, -1),
    FetchComponentTemplateVariables(true, -1, -1),
    FetchIdByComponentAndScenario(true, 1, 1),
    UpdateAutoCase(false, -1, -1),
    FetchAutoCaseUIMethod(true, -1, -1),
    FetchEhService(true, -1, -1),
    DynamicSQL(false, -1, -1),
    FetchAutoCaseAuditForAutoCaseUI(true, -1, -1),
    BuildFeaturesByRunId(true, -1, -1),
    BuildConfigByRunId(true, -1, -1),
    updateBaseUrlIntoAutoBaseURL(false, -1, -1),
    updateElementAndRunStatusToAutoCaseAudit(false, -1, -1),
    checkSvcFromAutoBaseURL(true, -1, -1),
    insertBaseUrlIntoAutoBaseURL(false, -1, -1),
    FetchFinalTestResultFromAudit(true, -1, -1),
    FetchValueFromAutoSystemVariables(true, -1, -1),
    FetchSvcRegionsFromConfiguration(true, -1, -1),
    checkIgnoreAPIFromAutoEndpointAllIgnore(true, -1, -1),
    selectIgnoreAPIMethodFromAutoEndpointAllIgnore(true, -1, -1),
    checkExistFromAutoEndpointAllIgnore(true, -1, -1),
    selectIgnoreAPIFromAutoEndpointAllIgnore(true, -1, -1),
    checkAPIFromAutoEndpointAll(true, -1, -1),
    selectFormerServiceNameFromAutoEndpointAllServiceMap(true, -1, -1),
    checkFormerServiceNameFromAutoEndpointAllServiceMap(true, -1, -1),
    selectServiceNameFromAutoEndpointAll(true, -1, -1),
    selectServicesFromEhService(true, -1, -1),
    selectServicesFromAutoConfiguration(true, -1, -1),
    selectComponentFromAutoConfiguration(true, -1, -1),
    insertGeneratedAutoCaseIntoAutoCaseScenario(false, -1, -1),
    deleteFromAutoEndpointAllByServiceName(false, -1, -1),
    deleteFromAutoBaseUrlByServiceName(false, -1, -1),
    deleteFromAutoConfigurationByServiceName(false, -1, -1),
    selectDistinctFromAutoEndpoint(true, -1, -1),
    selectEndpointInfoFromAutoEndpointAllByServiceName(true, -1, -1),
    selectEndpointInfoFromAutoEndpointAll(true, -1, -1),
    FetchEndpointCount(true, -1, -1),
    checkEndpointCount(true, -1, -1),
    FetchAutoSysVariableValueByKeyName(true, -1, -1),
    FetchRioMessageByTemplateName(true, -1, -1),
    selectAllJiraAndDescription(true, -1, -1),
    selectJiraAndDescriptionByIssueKey(true, -1, -1),
    updateUpdateAtBySpecificAttributionAndValue(false, -1, -1),
    selectStringListBySQL(true, -1, -1),
    FetchAutoCaseAudit(true, -1, -1),
    deleteAutoCaseScenarioByIdList(false, -1, -1),
    saveAutoCaseUIElement(false, -1, -1),
    updateAutoCaseUIElement(false, -1, -1),
    deleteAutoCaseUIElementByIdList(false, -1, -1),
    FetchAutoCaseUIElementsByUIElement(true, -1, -1),
    FetchALlAutoCaseUIElements(true, -1, -1),
    FetchALlAutoCaseUI(true, -1, -1),
    FetchAutoCaseUIByID(true, -1, -1),
    insertAutoCaseUI(false, -1, -1),
    saveAutoCaseUIBehavior(false, -1, -1),
    FetchIdFromAutoCaseUIByComponentAndScenario(true, -1, -1),
    FetchAutoCaseUIBehaviorsBySearch(true, -1, -1),
    FetchALlAutoCaseUIBehaviors(true, -1, -1),
    FetchAutoCaseUIBehaviorById(true, -1, -1),
    deleteAutoCaseUIBehyIdList(false, -1, -1),
    deleteAutoCaseUIBehaviorByIdList(false, -1, -1),
    updateAutoCaseUIBehavior(false, -1, -1),
    FetchIdFromAutoCaseUIPageByPageNameAndBehaviorName(true, -1, -1),
    FetchPageElementList(true, -1, -1),
    FetchPageBehaviorList(true, -1, -1),
    FetchAutoCaseBySearch(true, -1, -1),
    FetchAutoCaseById(true, -1, -1),
    FetchALlAutoCase(true, -1, -1),
    UNKNOWN(false, -1, -1);

    private final boolean isSelect;
    private final int rowNumber;
    private final int columnNumber;

    LIFDBStatement(boolean isSelect, int rowNumber, int columnNumber) {
        this.isSelect = isSelect;
        this.rowNumber = rowNumber;
        this.columnNumber = columnNumber;
    }

    public boolean isSelect() {
        return isSelect;
    }

    public int getRowNumber() {
        return rowNumber;
    }

    public int getColumnNumber() {
        return columnNumber;
    }

    public static boolean isValid(String value) {
        if (value == null) return false;
        for (LIFDBStatement r : LIFDBStatement.values()) {
            if (r.name().equalsIgnoreCase(value)) {
                return true;
            }
        }
        return false;
    }

    public static LIFDBStatement fromString(String value) {
        for (LIFDBStatement r : LIFDBStatement.values()) {
            if (r.name().equalsIgnoreCase(value)) {
                return r;
            }
        }
        return UNKNOWN;
    }
}
