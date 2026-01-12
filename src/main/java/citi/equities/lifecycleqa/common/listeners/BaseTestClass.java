package citi.equities.lifecycleqa.common.listeners;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import citi.equities.lifecycleqa.common.entities.AutoCaseAudit;
import citi.equities.lifecycleqa.common.entities.AutomationStepBasicInfo;
import citi.equities.lifecycleqa.common.enums.AutomationRunMode;
import citi.equities.lifecycleqa.common.enums.LIFDBStatement;
import citi.equities.lifecycleqa.common.utils.DBUtil;
import citi.equities.lifecycleqa.common.utils.DataTypeUtil;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.HashMap;
import java.util.Map;

public class BaseTestClass {
    private static final Logger log = LoggerFactory.getLogger(BaseTestClass.class);
    private static final ObjectMapper objectMapper = new ObjectMapper();

    protected AutomationStepBasicInfo createAutomationStepBasicInfo(int stepId, ObjectNode stepObject, AutoCaseAudit autoCaseAudit) {
        return new AutomationStepBasicInfo(
                false,
                autoCaseAudit.getRunId(),
                Integer.parseInt(autoCaseAudit.getCaseId()),
                autoCaseAudit.getRegion(),
                autoCaseAudit.getEnv(),
                autoCaseAudit.getVariables(),
                stepId,
                stepObject.has("name") ? stepObject.get("name").asText("") : "",
                0,
                stepObject
        );
    }

    protected void updateRunStatusAndElement(int index, AutoCaseAudit autoCaseAudit, long startTime, String stepStatus, ArrayNode steps) {
        long endTime = DataTypeUtil.fetchShanghaiZoneTimeStampNow();
        long duration = (endTime - startTime) * 1000000;
        String name = autoCaseAudit.getIssueKey().isBlank() ?
                autoCaseAudit.getSummary() :
                autoCaseAudit.getIssueKey() + " " + autoCaseAudit.getSummary();

        int pass = 0;
        for (JsonNode step : steps) {
            if (step.isObject()) {
                JsonNode resultNode = step.get("result");
                if (resultNode != null && resultNode.has("status")) {
                    if ("passed".equals(resultNode.get("status").asText(""))) {
                        pass++;
                    }
                }
            }
        }

        ObjectNode elementJson = objectMapper.createObjectNode();
        elementJson.put("endTime", endTime);
        elementJson.put("startTime", startTime);
        elementJson.put("duration", duration);
        elementJson.put("line", index);
        elementJson.put("id", "$startTime");
        elementJson.put("name", name);
        elementJson.put("type", "scenario");
        elementJson.set("steps", steps);
        elementJson.put("passedSteps", pass);

        String runStatus = "passed".equals(stepStatus) ? "PASSED" : "FAILED";
        Map<String, Object> requestParams = new HashMap<>();
        requestParams.put("runId", autoCaseAudit.getRunId());
        requestParams.put("caseId", Integer.parseInt(autoCaseAudit.getCaseId()));
        requestParams.put("element", DataTypeUtil.processJsonElementForSingleQuotation(elementJson).toString());
        requestParams.put("runStatus", runStatus);

        log.info("Update AutoCaseAudit with runId={}, caseId={}, runStatus={}",
                autoCaseAudit.getRunId(), autoCaseAudit.getCaseId(), runStatus);

        StringBuilder errorMessage = new StringBuilder();
        DBUtil.executeLIF(LIFDBStatement.UpdateAutoCaseAudit.name(), false, errorMessage, requestParams);

        if (errorMessage.length() > 0) {
            log.error("Failed to update AutoCaseAudit with error {}", errorMessage);
        }
    }

    protected void updateAutoProgressTestCaseCount(AutoCaseAudit autoCaseAudit, String stepStatus) {
        if (autoCaseAudit.getRunMode() == AutomationRunMode.DEBUG) {
            return;
        }

        String type = "passed".equals(stepStatus) ? "passes" : "failures";
        StringBuilder errorMessage = new StringBuilder();

        log.info("Update AutoProgress with runId={}, type={}", autoCaseAudit.getRunId(), type);

        Map<String, Object> params = new HashMap<>();
        params.put("runId", autoCaseAudit.getRunId());
        params.put("type", type);
        DBUtil.executeLIF(LIFDBStatement.UpdateAutoProgress.name(), false, errorMessage, params);

        if (errorMessage.length() > 0) {
            log.error("Failed to update AutoProgress with error {}", errorMessage);
        }
    }
}
