package citi.equities.lifecycleqa.test;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import citi.equities.lifecycleqa.common.entities.AutoCaseAudit;
import citi.equities.lifecycleqa.common.entities.AutomationStepBasicInfo;
import citi.equities.lifecycleqa.common.enums.AutoStepRunStatus;
import citi.equities.lifecycleqa.common.helpers.StepHelper;
import citi.equities.lifecycleqa.common.listeners.BaseTestClass;
import citi.equities.lifecycleqa.common.utils.DataTypeUtil;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.testng.Assert;
import org.testng.ITest;
import org.testng.annotations.Test;

import java.util.Map;
import java.util.SortedMap;
import java.util.TreeMap;

public class APITestClass extends BaseTestClass implements ITest {
    private static final Logger log = LoggerFactory.getLogger(APITestClass.class);
    private static final ObjectMapper objectMapper = new ObjectMapper();

    private final int index;
    private final AutoCaseAudit autoCaseAudit;

    public APITestClass(int index, AutoCaseAudit autoCaseAudit) {
        this.index = index;
        this.autoCaseAudit = autoCaseAudit;
    }

    @Override
    public String getTestName() {
        return autoCaseAudit.getCaseId() + " " + autoCaseAudit.getSummary();
    }

    @Test
    public void testMethod() {
        ArrayNode stepList = objectMapper.createArrayNode();
        String stepStatus = "passed";
        long startRuntime = DataTypeUtil.fetchShanghaiZoneTimeStampNow();

        SortedMap<Integer, JsonNode> scriptMap = autoCaseAudit.getScript();
        if (scriptMap == null || scriptMap.isEmpty()) {
            scriptMap = new TreeMap<>();
            scriptMap.put(1, objectMapper.createObjectNode());
        }

        for (Map.Entry<Integer, JsonNode> entry : scriptMap.entrySet()) {
            int stepId = entry.getKey();
            JsonNode stepNode = entry.getValue();

            if (!stepNode.isObject()) {
                continue;
            }

            ObjectNode stepObject = (ObjectNode) stepNode;
            AutomationStepBasicInfo automationBasicInfo = createAutomationStepBasicInfo(stepId, stepObject, autoCaseAudit);
            Map.Entry<AutoStepRunStatus, ArrayNode> result = StepHelper.doActionForAPITestStep(automationBasicInfo, stepObject);

            AutoStepRunStatus status = result.getKey();
            ArrayNode stepResults = result.getValue();

            for (JsonNode stepResult : stepResults) {
                if (stepResult.isObject()) {
                    stepList.add(stepResult);
                }
            }

            if (status != AutoStepRunStatus.Passed) {
                stepStatus = "failed";
                break;
            }
        }

        updateRunStatusAndElement(index, autoCaseAudit, startRuntime, stepStatus, stepList);
        updateAutoProgressTestCaseCount(autoCaseAudit, stepStatus);
        Assert.assertTrue("passed".equals(stepStatus));
    }

    @Override
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

    public static APITestClass getInstance(int index, AutoCaseAudit autoCaseAudit) {
        return new APITestClass(index, autoCaseAudit);
    }
}
