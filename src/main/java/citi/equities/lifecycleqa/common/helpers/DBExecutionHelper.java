package citi.equities.lifecycleqa.common.helpers;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import citi.equities.lifecycleqa.common.config.GlobalData;
import citi.equities.lifecycleqa.common.config.InitialConfig;
import citi.equities.lifecycleqa.common.entities.AutomationStepBasicInfo;
import citi.equities.lifecycleqa.common.entities.StopCondition;
import citi.equities.lifecycleqa.common.utils.DBUtil;
import citi.equities.lifecycleqa.common.utils.DataTypeUtil;
import org.apache.ibatis.session.SqlSessionManager;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class DBExecutionHelper {
    private static final Logger log = LoggerFactory.getLogger(DBExecutionHelper.class);
    private static final ObjectMapper objectMapper = new ObjectMapper();

    public static void handleJsonArrayForDBExecute(
            AutomationStepBasicInfo baseInfo, ArrayNode executeArray, StringBuilder errorMessage) {

        for (JsonNode element : executeArray) {
            JsonNode executeElement = PlaceholderReplaceHelper.replaceDataObjectForTestDataAndAutoSystemVariables(
                    baseInfo, element, errorMessage);

            if (!executeElement.isObject()) {
                continue;
            }

            ObjectNode executeObj = (ObjectNode) executeElement;

            JsonNode sqlNode = PlaceholderReplaceHelper.replaceDataValueFromAutoTestDataAndAutoSystemVariables(
                    baseInfo,
                    executeObj.has("sql") ? executeObj.get("sql").asText("") : "",
                    errorMessage);
            String sql = sqlNode.isTextual() ? sqlNode.asText() : "";

            String dbType = executeObj.has("dbType") ? executeObj.get("dbType").asText("") : "";
            String storedKey = executeObj.has("storedKey") ? executeObj.get("storedKey").asText(null) : null;

            int retryNumber = 1;
            int intervalSeconds = 1;
            StopCondition stopCondition = null;

            if (executeObj.has("retry")) {
                JsonNode retryNode = executeObj.get("retry");
                if (retryNode.isObject()) {
                    retryNumber = retryNode.has("attempt") ? retryNode.get("attempt").asInt(1) : 1;
                    intervalSeconds = retryNode.has("interval") ? retryNode.get("interval").asInt(1) : 1;

                    if (retryNode.has("stopCondition") && retryNode.get("stopCondition").isObject()) {
                        JsonNode sc = retryNode.get("stopCondition");
                        stopCondition = new StopCondition(
                                sc.has("largerThanSize") ? sc.get("largerThanSize").asInt() : null,
                                sc.has("lessThanSize") ? sc.get("lessThanSize").asInt() : null,
                                sc.has("contentEquals") ? sc.get("contentEquals").asText() : null,
                                sc.has("contentContains") ? sc.get("contentContains").asText() : null,
                                null, null, null
                        );
                    }
                }
            }

            String sqlSessionManagerName = DBUtil.buildSqlSessionManagerName(dbType, baseInfo.getRegion(), baseInfo.getEnv());
            boolean isSelect;
            try {
                isSelect = DBUtil.analyzeSqlWithIsSelectRowColumnNumber(sql).isSelect();
            } catch (Exception e) {
                isSelect = false;
            }

            SqlSessionManager sqlSessionManager = InitialConfig.DB_CONFIG.get(sqlSessionManagerName);
            if (sqlSessionManager == null) {
                errorMessage.append("SqlSessionManager not found for: ").append(sqlSessionManagerName);
                return;
            }

            JsonNode dbActionResult = DBUtil.executeSQL(sqlSessionManager, sql, errorMessage, retryNumber, intervalSeconds, stopCondition);

            if (isSelect) {
                if (errorMessage.length() > 0) {
                    String errMsg = "Failed to execute SQL: " + sql + "\n with error: " + errorMessage;
                    errorMessage.setLength(0);
                    log.error(errMsg);
                    errorMessage.append(errMsg);
                    return;
                }
            } else {
                if (storedKey == null) {
                    errorMessage.append("storedKey is null, skip to store");
                    log.error("storedKey is null, skip to store");
                    return;
                }
                updateTestDataInAutoCaseAudit(baseInfo, storedKey, dbActionResult, errorMessage);
                if (errorMessage.length() > 0) {
                    return;
                }
            }
        }
    }

    public static void updateTestDataInAutoCaseAudit(
            AutomationStepBasicInfo baseInfo, String testDataKey,
            JsonNode testDataValue, StringBuilder errorMessage) {

        if (baseInfo.isStepDebug()) {
            log.info("Debug mode, skip to store '{}'", testDataKey);
            GlobalData.addKeyInTestData(baseInfo.getRunId(), testDataKey, testDataValue);
        } else {
            JsonNode finalTestDataValue = DataTypeUtil.processJsonElementForSingleQuotation(testDataValue);
            String updateSql = "update auto_case_audit set \"testData\" = jsonb_set(\"testData\", '{" +
                    testDataKey + "}', '" + finalTestDataValue.toString() +
                    "') where \"runId\" = '" + baseInfo.getRunId() +
                    "' and \"caseId\" = " + baseInfo.getCaseId();

            DBUtil.executeLIF(updateSql, true, errorMessage);

            if (errorMessage.length() > 0) {
                String errMsg = "Failed to store " + testDataKey + " with error: " + errorMessage;
                log.error(errMsg);
                errorMessage.append(errMsg);
            }
            log.info("Successfully Prepare storedKey = {}, storedValue = {} for runId: {}, caseId: {}",
                    testDataKey, testDataValue, baseInfo.getRunId(), baseInfo.getCaseId());
        }
    }

    public static String findLastSuccessRecordsInAudit(
            AutomationStepBasicInfo baseInfo, int stepId,
            java.util.List<String> placeHolderMissingList, StringBuilder errorMessage) {

        String sql = "select COALESCE(\"testDataTrack\" -> 'step" + (stepId - 1) +
                "', \"testData\") from auto_case_audit where \"caseId\" = " + baseInfo.getCaseId() +
                " and region = '" + baseInfo.getRegion() +
                "' and env = '" + baseInfo.getEnv() +
                "' and \"runStatus\" = 'PASSED' and \"runMode\" = 'TEST' order by id desc limit 1";

        JsonNode result = DBUtil.executeLIF(sql, true, errorMessage);
        StringBuilder resultBuilder = new StringBuilder("Audit Data for Last success:\n");

        if (result.isObject()) {
            ObjectNode resultObj = (ObjectNode) result;
            for (String key : placeHolderMissingList) {
                if (resultObj.has(key)) {
                    resultBuilder.append(key).append(": ").append(resultObj.get(key)).append("\n");
                }
            }
        }

        return resultBuilder.toString();
    }
}
