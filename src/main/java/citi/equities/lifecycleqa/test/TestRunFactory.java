package citi.equities.lifecycleqa.test;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import citi.equities.lifecycleqa.common.entities.AutoCaseAudit;
import citi.equities.lifecycleqa.common.enums.AutomationRunMode;
import citi.equities.lifecycleqa.common.enums.AutomationRunStatus;
import citi.equities.lifecycleqa.common.utils.DataTypeUtil;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.testng.annotations.Factory;
import org.testng.annotations.Parameters;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

public class TestRunFactory {
    private static final Logger log = LoggerFactory.getLogger(TestRunFactory.class);
    private static final ObjectMapper objectMapper = new ObjectMapper();

    @Factory
    @Parameters({"runId", "scenario", "audits"})
    public Object[] createInstances(String runId, String scenario, String audits) {
        log.info("Start Run runId={}, Scenario={}", runId, scenario);

        JsonNode auditsJson = DataTypeUtil.convertToJsonNode(audits);
        ArrayNode auditArray = auditsJson.isArray() ? (ArrayNode) auditsJson : objectMapper.createArrayNode();

        List<AutoCaseAudit> auditList = buildBasicAutoCaseAudit(auditArray);
        auditList.sort(Comparator.comparing(a -> a.getIssueKey() + "_" + a.getSummary()));

        Object[] instances = new Object[auditList.size()];
        for (int i = 0; i < auditList.size(); i++) {
            instances[i] = APITestClass.getInstance(i, auditList.get(i));
        }

        return instances;
    }

    private List<AutoCaseAudit> buildBasicAutoCaseAudit(ArrayNode list) {
        List<AutoCaseAudit> autoCaseAudits = new ArrayList<>();

        for (JsonNode item : list) {
            if (!item.isObject()) {
                continue;
            }

            ObjectNode map = (ObjectNode) item;

            AutoCaseAudit audit = new AutoCaseAudit(
                    getTextValue(map, "runId"),
                    getTextValue(map, "suite"),
                    getTextValue(map, "component"),
                    parseRunMode(getTextValue(map, "runMode")),
                    parseRunStatus(getTextValue(map, "runStatus")),
                    getObjectValue(map, "element"),
                    getTextValue(map, "caseId"),
                    getTextValue(map, "mainComponent"),
                    getTextValue(map, "componentLikeFirst"),
                    getTextValue(map, "scenario"),
                    getTextValue(map, "serviceName"),
                    getTextValue(map, "region"),
                    getTextValue(map, "env"),
                    getTextValue(map, "label"),
                    getTextValue(map, "issueKey"),
                    getTextValue(map, "summary"),
                    getArrayValue(map, "caseScript"),
                    getArrayValue(map, "templateScript"),
                    getObjectValue(map, "testData"),
                    getObjectValue(map, "caseVariables"),
                    getObjectValue(map, "templateVariables"),
                    getBooleanValue(map, "isTemplate")
            );

            autoCaseAudits.add(audit);
        }

        return autoCaseAudits;
    }

    private String getTextValue(ObjectNode map, String key) {
        return map.has(key) ? map.get(key).asText("") : "";
    }

    private ObjectNode getObjectValue(ObjectNode map, String key) {
        if (map.has(key) && map.get(key).isObject()) {
            return (ObjectNode) map.get(key);
        }
        return objectMapper.createObjectNode();
    }

    private ArrayNode getArrayValue(ObjectNode map, String key) {
        if (map.has(key) && map.get(key).isArray()) {
            return (ArrayNode) map.get(key);
        }
        return objectMapper.createArrayNode();
    }

    private boolean getBooleanValue(ObjectNode map, String key) {
        return map.has(key) && map.get(key).asBoolean(false);
    }

    private AutomationRunMode parseRunMode(String value) {
        try {
            return AutomationRunMode.valueOf(value);
        } catch (Exception e) {
            return AutomationRunMode.TEST;
        }
    }

    private AutomationRunStatus parseRunStatus(String value) {
        try {
            return AutomationRunStatus.valueOf(value);
        } catch (Exception e) {
            return AutomationRunStatus.PENDING;
        }
    }
}
