package citi.equities.lifecycleqa.common.helpers;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.github.f4b6a3.ulid.UlidCreator;
import citi.equities.lifecycleqa.common.entities.AutomationStepBasicInfo;
import citi.equities.lifecycleqa.common.enums.AutoStepRunStatus;
import citi.equities.lifecycleqa.common.enums.AutomationLoopKey;
import citi.equities.lifecycleqa.common.utils.DBUtil;
import citi.equities.lifecycleqa.common.utils.DataTypeUtil;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.*;

public class StepHelper {
    private static final Logger log = LoggerFactory.getLogger(StepHelper.class);
    private static final ObjectMapper objectMapper = new ObjectMapper();

    public static Map.Entry<AutoStepRunStatus, ArrayNode> doActionForAPITestStep(
            AutomationStepBasicInfo baseInfo, ObjectNode stepObject) {
        StringBuilder errorMessage = new StringBuilder();

        // step 1: check before Loop and Loop
        JsonNode stepObjectNode = baseInfo.getStepObject();
        ObjectNode stepObjectForLoop = stepObjectNode.isObject() ? (ObjectNode) stepObjectNode : objectMapper.createObjectNode();
        Map<Integer, ObjectNode> stepArray = processBeforeLoopAndLoop(
                baseInfo, stepObjectForLoop, AutomationLoopKey.StepLoopObject, errorMessage);

        if (errorMessage.length() > 0) {
            return handlePreparationError(baseInfo, errorMessage);
        }

        Map.Entry<AutoStepRunStatus, ArrayNode> result;
        if (stepArray.size() == 1) {
            result = handleSingleStep(baseInfo, stepArray, errorMessage);
        } else {
            result = handleMultipleSteps(baseInfo, stepArray, errorMessage);
        }

        // backup testData on Step Level
        backupTestData(baseInfo, errorMessage, result.getKey());
        return result;
    }

    private static void dbActionForJsonArray(AutomationStepBasicInfo baseInfo, ArrayNode details, StringBuilder errorMessage) {
        List<JsonNode> sortedDetails = new ArrayList<>();
        for (JsonNode node : details) {
            sortedDetails.add(node);
        }
        sortedDetails.sort(Comparator.comparingInt(a -> a.has("id") ? a.get("id").asInt(0) : 0));

        for (JsonNode element : sortedDetails) {
            ObjectNode jsonObject = (ObjectNode) element;

            // step 1: check before dependency and dependency
            boolean ifRun = processSetForBeforeDependencyAndDependency(baseInfo, jsonObject, errorMessage);
            if (errorMessage.length() > 0) {
                log.error(errorMessage.toString());
                errorMessage.setLength(0);
            }

            if (ifRun) {
                // only dependency is true, will do action
                // step 2: beforeWait
                if (jsonObject.has("beforeWait")) {
                    int waitTime = jsonObject.get("beforeWait").asInt(0);
                    try {
                        Thread.sleep(waitTime * 1000L);
                    } catch (InterruptedException e) {
                        Thread.currentThread().interrupt();
                    }
                }

                // step 3: execute
                ArrayNode executeArray = jsonObject.has("execute") && jsonObject.get("execute").isArray()
                        ? (ArrayNode) jsonObject.get("execute")
                        : objectMapper.createArrayNode();
                DBExecutionHelper.handleJsonArrayForDBExecute(baseInfo, executeArray, errorMessage);
                if (errorMessage.length() > 0) return;
            } else {
                log.info("Skip to run action for id {}", jsonObject.has("id") ? jsonObject.get("id").asInt() : 0);
            }
        }
    }

    private static boolean dbAssertActionForJsonArray(
            AutomationStepBasicInfo baseInfo, ArrayNode details,
            StringBuilder output, StringBuilder errorMessage) {

        List<JsonNode> sortedDetails = new ArrayList<>();
        for (JsonNode node : details) {
            sortedDetails.add(node);
        }
        sortedDetails.sort(Comparator.comparingInt(a -> a.has("id") ? a.get("id").asInt(0) : 0));

        for (JsonNode element : sortedDetails) {
            ObjectNode jsonObject = (ObjectNode) element;

            // step 1: check beforeLoop and loop to build final array
            Map<Integer, ObjectNode> assertArray = processBeforeLoopAndLoop(
                    baseInfo, jsonObject, AutomationLoopKey.AssertLoopObject, errorMessage);

            if (errorMessage.length() > 0) {
                return false;
            }

            for (Map.Entry<Integer, ObjectNode> entry : assertArray.entrySet()) {
                int index = entry.getKey();
                ObjectNode stepItem = entry.getValue();

                // step 2: check beforeDependency and dependency
                boolean ifAssert = processSetForBeforeDependencyAndDependency(baseInfo, stepItem, errorMessage);
                if (errorMessage.length() > 0) {
                    continue;
                }

                if (ifAssert) {
                    int id = stepItem.has("id") ? stepItem.get("id").asInt(0) : 0;
                    log.info("Start to handle assert for id {}, index {}", id, index);

                    // step 3: check beforeAssert and assert
                    ArrayNode beforeAssertArray = stepItem.has("beforeAssert") && stepItem.get("beforeAssert").isArray()
                            ? (ArrayNode) stepItem.get("beforeAssert")
                            : objectMapper.createArrayNode();
                    dbActionForJsonArray(baseInfo, beforeAssertArray, errorMessage);
                    if (errorMessage.length() > 0) {
                        continue;
                    }

                    ArrayNode lowerKeyAssertArray = stepItem.has("assert") && stepItem.get("assert").isArray()
                            ? (ArrayNode) stepItem.get("assert")
                            : objectMapper.createArrayNode();
                    boolean ifAssertSuccess = DBAssertionHelper.handleJsonArrayForDBAssert(
                            baseInfo, lowerKeyAssertArray, output, errorMessage);

                    if (errorMessage.length() > 0 || !ifAssertSuccess) {
                        continue;
                    }
                }
            }
        }
        return true;
    }

    private static JsonNode doReplacePlaceHolderForAction(
            AutomationStepBasicInfo baseInfo, JsonNode jsonElement, StringBuilder errorMessage) {
        return PlaceholderReplaceHelper.replaceDataObjectForTestDataAndAutoSystemVariables(
                baseInfo, jsonElement, errorMessage);
    }

    public static AutomationStepBasicInfo buildAutomationStepBasicInfo(boolean isStepDebug, ObjectNode request) {
        String runId = isStepDebug ? UlidCreator.getUlid().toString()
                : (request.has("runId") ? request.get("runId").asText("") : "");
        int caseId = request.has("caseId") ? request.get("caseId").asInt(0) : 0;
        String region = request.has("region") ? request.get("region").asText("") : "";
        String env = request.has("env") ? request.get("env").asText("") : "";
        ObjectNode variables = request.has("variables") && request.get("variables").isObject()
                ? (ObjectNode) request.get("variables")
                : objectMapper.createObjectNode();
        int stepId = request.has("id") ? request.get("id").asInt(1) : 1;
        String stepName = request.has("name") ? request.get("name").asText("") : "";
        JsonNode stepObject = request.has("detail") && request.get("detail").isObject()
                ? (ObjectNode) request.get("detail")
                : objectMapper.createObjectNode();

        return new AutomationStepBasicInfo(
                isStepDebug, runId, caseId, region, env, variables, stepId, stepName, 0, stepObject);
    }

    private static boolean processSetForBeforeDependencyAndDependency(
            AutomationStepBasicInfo baseInfo, ObjectNode detail, StringBuilder errorMessage) {
        // step 1: check before dependency
        ArrayNode beforeDependencyArray = detail.has("beforeDependency") && detail.get("beforeDependency").isArray()
                ? (ArrayNode) detail.get("beforeDependency")
                : objectMapper.createArrayNode();
        dbActionForJsonArray(baseInfo, beforeDependencyArray, errorMessage);

        if (errorMessage.length() > 0) {
            return false;
        }

        // step 2: check dependency
        ArrayNode dependencyArray = detail.has("dependency") && detail.get("dependency").isArray()
                ? (ArrayNode) detail.get("dependency")
                : objectMapper.createArrayNode();
        boolean ifAssertSuccess = dbAssertActionForJsonArray(baseInfo, dependencyArray, new StringBuilder(), errorMessage);

        if (errorMessage.toString().contains("Error: Assertion failed for")) {
            errorMessage.setLength(0);
        }
        return ifAssertSuccess;
    }

    private static Map<Integer, ObjectNode> processBeforeLoopAndLoop(
            AutomationStepBasicInfo baseInfo, ObjectNode jsonObject,
            AutomationLoopKey loopKey, StringBuilder errorMessage) {

        // step 1: check beforeLoop
        ArrayNode beforeLoopArray = jsonObject.has("beforeLoop") && jsonObject.get("beforeLoop").isArray()
                ? (ArrayNode) jsonObject.get("beforeLoop")
                : objectMapper.createArrayNode();
        dbActionForJsonArray(baseInfo, beforeLoopArray, errorMessage);

        if (errorMessage.length() > 0) {
            Map<Integer, ObjectNode> result = new LinkedHashMap<>();
            result.put(1, jsonObject);
            return result;
        }

        // step 2: check loop
        if (!jsonObject.has("loop") || jsonObject.get("loop").isNull()) {
            Map<Integer, ObjectNode> result = new LinkedHashMap<>();
            result.put(1, jsonObject);
            return result;
        }

        String loopName = jsonObject.get("loop").asText("");
        if (loopName.matches("\\{\\{(.*?)\\}\\}")) {
            errorMessage.append("Error: The value for loop '").append(loopName).append("' seems be null, please check!\n");
            Map<Integer, ObjectNode> result = new LinkedHashMap<>();
            result.put(1, jsonObject);
            return result;
        }

        ObjectNode loopObject = objectMapper.createObjectNode();
        loopObject.put("loop", loopName);
        JsonNode loopObjectAfterReplace = doReplacePlaceHolderForAction(baseInfo, loopObject, errorMessage);
        int loopSize = 0;
        if (loopObjectAfterReplace.has("loop") && loopObjectAfterReplace.get("loop").isArray()) {
            loopSize = loopObjectAfterReplace.get("loop").size();
        }

        return LoopHelper.handleLoopForJson(jsonObject, loopName, loopSize, loopKey.getKey(), errorMessage);
    }

    private static boolean processSetForAfterTestAndBeforeEnd(
            AutomationStepBasicInfo baseInfo, ObjectNode detail,
            StringBuilder output, StringBuilder errorMessage) {
        // step 5: afterTest
        ArrayNode afterTestArray = detail.has("afterTest") && detail.get("afterTest").isArray()
                ? (ArrayNode) detail.get("afterTest")
                : objectMapper.createArrayNode();
        boolean ifAssertSuccess = dbAssertActionForJsonArray(baseInfo, afterTestArray, output, errorMessage);

        if (errorMessage.length() > 0) {
            return false;
        }

        // step 6: beforeEnd
        boolean ifRollBack = detail.has("ifRollBack") && detail.get("ifRollBack").asBoolean(false);
        if (ifRollBack) {
            ArrayNode beforeEndArray = detail.has("beforeEnd") && detail.get("beforeEnd").isArray()
                    ? (ArrayNode) detail.get("beforeEnd")
                    : objectMapper.createArrayNode();
            dbActionForJsonArray(baseInfo, beforeEndArray, errorMessage);
            if (errorMessage.length() > 0) {
                return false;
            }
        }
        return ifAssertSuccess;
    }

    private static Map.Entry<AutoStepRunStatus, ArrayNode> handlePreparationError(
            AutomationStepBasicInfo baseInfo, StringBuilder errorMessage) {
        ObjectNode currentStepRun = ReportHelper.buildStepForAPIBasic(
                baseInfo,
                DataTypeUtil.fetchShanghaiZoneTimeStampNow(),
                AutoStepRunStatus.Failed,
                errorMessage,
                new StringBuilder()
        );
        ArrayNode resultArray = objectMapper.createArrayNode();
        resultArray.add(currentStepRun);
        return new AbstractMap.SimpleEntry<>(AutoStepRunStatus.Failed, resultArray);
    }

    private static Map.Entry<AutoStepRunStatus, ArrayNode> handleSingleStep(
            AutomationStepBasicInfo baseInfo, Map<Integer, ObjectNode> stepArray,
            StringBuilder errorMessage) {
        Map.Entry<Integer, ObjectNode> first = stepArray.entrySet().iterator().next();
        baseInfo.setIndexId(first.getKey());
        baseInfo.setStepObject(first.getValue());
        Map.Entry<AutoStepRunStatus, ObjectNode> pair = doActionForAPISingle(baseInfo, errorMessage);
        ArrayNode resultArray = objectMapper.createArrayNode();
        resultArray.add(pair.getValue());
        return new AbstractMap.SimpleEntry<>(pair.getKey(), resultArray);
    }

    private static Map.Entry<AutoStepRunStatus, ArrayNode> handleMultipleSteps(
            AutomationStepBasicInfo baseInfo, Map<Integer, ObjectNode> stepArray,
            StringBuilder errorMessage) {
        AutoStepRunStatus stepStatus = AutoStepRunStatus.Passed;
        ArrayNode stepList = objectMapper.createArrayNode();

        for (Map.Entry<Integer, ObjectNode> entry : stepArray.entrySet()) {
            int index = entry.getKey();
            ObjectNode stepItem = entry.getValue();

            baseInfo.setIndexId(index);
            baseInfo.setStepName(baseInfo.getStepName() + " (Loop " + index + ")");
            baseInfo.setStepObject(stepItem);

            Map.Entry<AutoStepRunStatus, ObjectNode> pair = doActionForAPISingle(baseInfo, errorMessage);
            stepList.add(pair.getValue());

            if (pair.getKey() == AutoStepRunStatus.Failed) {
                stepStatus = pair.getKey();
            }
        }
        return new AbstractMap.SimpleEntry<>(stepStatus, stepList);
    }

    private static void backupTestData(AutomationStepBasicInfo baseInfo, StringBuilder errorMessage, AutoStepRunStatus stepStatus) {
        String sql = "update auto_case_audit set \"testDataTrack\" = jsonb_set(\"testDataTrack\",'{\"" +
                baseInfo.getStepId() + "\"}','\"" + stepStatus + "\"') where \"runId\" = '" +
                baseInfo.getRunId() + "'";
        DBUtil.executeLIF(sql, true, errorMessage);
        log.info("End to run caseId={}, StepId={}, Status={}, update auto_case_audit.testDataTrack",
                baseInfo.getCaseId(), baseInfo.getStepId(), stepStatus);
    }

    private static Map.Entry<AutoStepRunStatus, ObjectNode> doActionForAPISingle(
            AutomationStepBasicInfo baseInfo, StringBuilder errorMessage) {
        StringBuilder output = new StringBuilder();
        int stepId = baseInfo.getStepId();
        int indexId = baseInfo.getIndexId();
        JsonNode stepNode = baseInfo.getStepObject();
        ObjectNode stepObject = stepNode.isObject() ? (ObjectNode) stepNode : objectMapper.createObjectNode();
        long stepStart = DataTypeUtil.fetchShanghaiZoneTimeStampNow();

        log.info("Start Run: runId={}, caseId={}, stepId={}, index={}", baseInfo.getRunId(), baseInfo.getCaseId(), stepId, indexId);
        output.append("RunId=").append(baseInfo.getRunId()).append(", caseId=").append(baseInfo.getCaseId())
                .append(", stepId=").append(stepId).append(", index=").append(indexId).append("\n");

        // step 2: check before dependency and dependency
        processSetForBeforeDependencyAndDependency(baseInfo, stepObject, errorMessage);
        if (errorMessage.length() > 0) {
            return new AbstractMap.SimpleEntry<>(
                    AutoStepRunStatus.Skipped,
                    ReportHelper.buildStepForAPIBasic(baseInfo, stepStart, AutoStepRunStatus.Skipped, errorMessage, output)
            );
        }

        // step 3: beforeTest
        ArrayNode beforeTestArray = stepObject.has("beforeTest") && stepObject.get("beforeTest").isArray()
                ? (ArrayNode) stepObject.get("beforeTest")
                : objectMapper.createArrayNode();
        dbActionForJsonArray(baseInfo, beforeTestArray, errorMessage);
        if (errorMessage.length() > 0) {
            return new AbstractMap.SimpleEntry<>(
                    AutoStepRunStatus.Failed,
                    ReportHelper.buildStepForAPIBasic(baseInfo, stepStart, AutoStepRunStatus.Failed, errorMessage, output)
            );
        }

        // step 4: api request
        APIRequestHelper.handleAPIRequest(baseInfo, output, errorMessage);
        if (errorMessage.length() > 0) {
            return new AbstractMap.SimpleEntry<>(
                    AutoStepRunStatus.Failed,
                    ReportHelper.buildStepForAPIBasic(baseInfo, stepStart, AutoStepRunStatus.Failed, errorMessage, output)
            );
        }

        // step 5: afterTest and beforeEnd
        output.append("\nAssert after API Request\n---------------------------------\n");
        boolean ifAssertSuccess = processSetForAfterTestAndBeforeEnd(baseInfo, stepObject, output, errorMessage);
        if (errorMessage.length() > 0) {
            return new AbstractMap.SimpleEntry<>(
                    AutoStepRunStatus.Failed,
                    ReportHelper.buildStepForAPIBasic(baseInfo, stepStart, AutoStepRunStatus.Failed, errorMessage, output)
            );
        }

        AutoStepRunStatus stepStatus = ifAssertSuccess ? AutoStepRunStatus.Passed : AutoStepRunStatus.Failed;
        return new AbstractMap.SimpleEntry<>(
                stepStatus,
                ReportHelper.buildStepForAPIBasic(baseInfo, stepStart, stepStatus, errorMessage, output)
        );
    }
}
