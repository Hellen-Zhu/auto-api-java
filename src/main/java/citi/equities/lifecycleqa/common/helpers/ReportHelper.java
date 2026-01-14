package citi.equities.lifecycleqa.common.helpers;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import citi.equities.lifecycleqa.common.config.InitialConfig;
import citi.equities.lifecycleqa.common.entities.AutomationStepBasicInfo;
import citi.equities.lifecycleqa.common.entities.Result;
import citi.equities.lifecycleqa.common.entities.Step;
import citi.equities.lifecycleqa.common.enums.AutoStepRunStatus;
import citi.equities.lifecycleqa.common.enums.DashboardType;
import citi.equities.lifecycleqa.common.enums.LIFDBStatement;
import citi.equities.lifecycleqa.common.utils.DBUtil;
import citi.equities.lifecycleqa.common.utils.DataTypeUtil;
import io.restassured.RestAssured;
import io.restassured.http.ContentType;
import io.restassured.response.Response;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Arrays;

public class ReportHelper {
    private static final Logger log = LoggerFactory.getLogger(ReportHelper.class);
    private static final ObjectMapper objectMapper = new ObjectMapper();

    public static ObjectNode buildStepForAPIBasic(
            AutomationStepBasicInfo stepInfo, long stepStart, AutoStepRunStatus stepStatus,
            StringBuilder errorMessage, StringBuilder output) {

        String runId = stepInfo.getRunId();
        String region = stepInfo.getRegion();
        String env = stepInfo.getEnv();
        int stepId = stepInfo.getStepId();
        int indexId = stepInfo.getIndexId();
        String stepName = stepInfo.getStepName();
        JsonNode stepObjectNode = stepInfo.getStepObject();
        ObjectNode testObject = stepObjectNode.isObject() ? (ObjectNode) stepObjectNode : objectMapper.createObjectNode();

        if (output.length() == 0) {
            String serviceName = testObject.has("serviceName") ? testObject.get("serviceName").asText("") : "";
            String path = testObject.has("path") ? testObject.get("path").asText("") : "";
            String method = testObject.has("method") ? testObject.get("method").asText("") : "";
            JsonNode request = testObject.has("request") ? testObject.get("request") : objectMapper.createObjectNode();
            String url = InitialConfig.fetchBaseUrl(serviceName, region + env);

            output.append("RunId = ").append(runId).append("\n")
                    .append("----------------------------------------------------------------------\n")
                    .append("API Trigger for Step: '").append(stepName).append("'\n")
                    .append("BaseUrl: ").append(url).append("\n")
                    .append("Path: ").append(path).append("\n")
                    .append("Method: ").append(method).append("\n");
            output.append("Request: ").append(request).append("\n");
        }

        output.append("\n");

        Step currentStep = new Step();
        currentStep.setLine(stepId);
        currentStep.setName(stepName);
        currentStep.setSeqNumber(indexId);

        Result result = new Result();
        result.setDuration((DataTypeUtil.fetchShanghaiZoneTimeStampNow() - stepStart) * 1000000);
        result.setStatus(stepStatus.getStatus());

        if (errorMessage.length() > 0) {
            log.error("Error message: {}", errorMessage);
            result.setError_message("\n\n\nFailed Reason:\n----------------------------------------------------------------------" + errorMessage);
        } else {
            result.setError_message("");
        }

        currentStep.setOutput(Arrays.asList(output.toString().split("\n")));

        if (stepStatus == AutoStepRunStatus.TimeOut) {
            result.setStatus(AutoStepRunStatus.Failed.getStatus());
            result.setError_message("Request send takes so long, which is more than 30s.");
        }

        currentStep.setResult(result);

        return objectMapper.convertValue(currentStep, ObjectNode.class);
    }

    public static ObjectNode uploadDashboard(String runId, DashboardType dashboardType) {
        if (dashboardType == DashboardType.UNKNOWN) {
            ObjectNode errorNode = objectMapper.createObjectNode();
            errorNode.put("error", "Error: Type " + dashboardType + " not in [MONDO, FAST]");
            return errorNode;
        }

        log.info("Start to upload dashboard by runId {}", runId);
        StringBuilder errorMessage = new StringBuilder();

        JsonNode featuresNode = DBUtil.executeLIF(LIFDBStatement.FetchFinalFeature.name(), false, errorMessage,
                java.util.Collections.singletonMap("runId", runId));
        ArrayNode features = featuresNode.isArray() ? (ArrayNode) featuresNode : objectMapper.createArrayNode();

        String configSql = "select distinct config from auto_case_audit where \"runId\" = '" + runId + "' limit 1";
        JsonNode configNode = DBUtil.executeLIF(configSql, true, errorMessage);
        ObjectNode config = configNode.isObject() ? (ObjectNode) configNode : objectMapper.createObjectNode();

        if (errorMessage.length() > 0) {
            ObjectNode errorResult = objectMapper.createObjectNode();
            errorResult.put("error", errorMessage.toString());
            return errorResult;
        }

        ObjectNode responseElement = objectMapper.createObjectNode();
        switch (dashboardType) {
            case MONDO:
                responseElement.set("mondo", uploadMondo(features, config));
                break;
            case FAST:
                responseElement.set("fast", uploadFast(features, config));
                break;
            case ALL:
                responseElement.set("mondo", uploadMondo(features, config));
                responseElement.set("fast", uploadFast(features, config));
                break;
            default:
                break;
        }

        ObjectNode result = objectMapper.createObjectNode();
        result.put("releaseVersion", config.has("releaseVersion") ? config.get("releaseVersion").asText("") : "");
        result.put("suiteName", config.has("suite") ? config.get("suite").asText("") : "");
        result.put("fastDashboardEnv", config.has("fastDashboardEnv") ? config.get("fastDashboardEnv").asText("") : "");
        result.put("fastProject", config.has("fastProject") ? config.get("fastProject").asText("") : "");
        result.put("errorMessage", "");
        result.set("returnMessage", responseElement);

        return result;
    }

    private static JsonNode uploadMondo(ArrayNode features, ObjectNode config) {
        log.info("Start to uploadDashboard");
        ObjectNode mondoJSON = buildMondo(config, features);
        String mondoUrl = "http://mondo.nam.nsroot.net:8000/api/automation2/upload";
        log.info("Mondo URL = {}", mondoUrl);

        Response mondoResponse = RestAssured.given()
                .contentType(ContentType.JSON)
                .body(mondoJSON.toString())
                .post(mondoUrl);

        log.info("Mondo Response = {}", mondoResponse.asString());
        log.info("Mondo StatusCode = {}", mondoResponse.statusCode());
        return DataTypeUtil.convertToJsonNode(mondoResponse.asString());
    }

    private static JsonNode uploadFast(ArrayNode features, ObjectNode config) {
        double totalPassedCases = 0;
        double totalCases = 0;

        for (JsonNode feature : features) {
            if (feature.isObject()) {
                totalPassedCases += feature.has("passedCases") ? feature.get("passedCases").asDouble(0) : 0;
                totalCases += feature.has("totalCases") ? feature.get("totalCases").asDouble(1) : 1;
            }
        }

        double averagePassRate = totalCases > 0 ? totalPassedCases / totalCases : 0.0;

        StringBuilder errorMessage = new StringBuilder();
        JsonNode passRateNode = DBUtil.executeLIF(
                "select value from auto_system_variable where config_key = 'dashboard.fast.passrate' limit 1",
                true, errorMessage);
        double passRate = passRateNode.isNumber() ? passRateNode.asDouble(0.45) : 0.45;

        if (errorMessage.length() > 0) {
            return objectMapper.convertValue(errorMessage.toString(), JsonNode.class);
        }

        if (averagePassRate >= passRate) {
            ObjectNode fastJSON = buildFast(config, features);
            String fastDashboardEnv = config.has("fastDashboardEnv") ? config.get("fastDashboardEnv").asText("") : "";
            String baseUrl = "uat".equalsIgnoreCase(fastDashboardEnv)
                    ? "https://fast-equities-api-icg-msst-fast-167813.apps.namicg39034u.ecs.dyn.nsroot.net"
                    : "https://fast-equities-api-icg-msst-fast-167813.apps.namicggtd39d.ecs.dyn.nsroot.net";
            String fastServiceUpload = baseUrl + "/v2/api/report";
            log.info("Fast URL = {}", fastServiceUpload);

            Response fastResponse = RestAssured.given()
                    .contentType(ContentType.JSON)
                    .body(fastJSON.toString())
                    .when()
                    .post(fastServiceUpload);

            log.info("Fast StatusCode = {}", fastResponse.statusCode());
            return DataTypeUtil.convertToJsonNode(fastResponse.asString());
        } else {
            return objectMapper.convertValue("All features pass rate is lower than 25%, seems like Environment issue!", JsonNode.class);
        }
    }

    private static ObjectNode buildMondo(ObjectNode autoConfiguration, ArrayNode features) {
        ObjectNode result = objectMapper.createObjectNode();
        result.put("name", "Mondo");
        result.put("project", autoConfiguration.has("fastProject") ? autoConfiguration.get("fastProject").asText("") : "");
        result.put("group", autoConfiguration.has("group") ? autoConfiguration.get("group").asText("") : "");
        result.put("releaseVersion", autoConfiguration.has("releaseVersion") ? autoConfiguration.get("releaseVersion").asText("") : "");
        result.put("suite", autoConfiguration.has("suite") ? autoConfiguration.get("suite").asText("") : "");
        result.put("runBy", autoConfiguration.has("runBy") ? autoConfiguration.get("runBy").asText("") : "");
        result.put("testType", autoConfiguration.has("testType") ? autoConfiguration.get("testType").asText("") : "");
        result.put("projectKey", autoConfiguration.has("projectKey") ? autoConfiguration.get("projectKey").asText("") : "");
        result.put("buildId", autoConfiguration.has("buildId") ? autoConfiguration.get("buildId").asText("") : "");
        result.put("env", autoConfiguration.has("env") ? autoConfiguration.get("env").asText("") : "");
        result.put("region", autoConfiguration.has("region") ? autoConfiguration.get("region").asText("") : "");
        result.put("component", autoConfiguration.has("component") ? autoConfiguration.get("component").asText("") : "");
        result.put("runId", autoConfiguration.has("runId") ? autoConfiguration.get("runId").asText("") : "");
        result.put("label", autoConfiguration.has("label") ? autoConfiguration.get("label").asText("") : "");
        result.put("serviceName", autoConfiguration.has("serviceName") ? autoConfiguration.get("serviceName").asText("") : "");
        result.set("json", features);
        return result;
    }

    private static ObjectNode buildFast(ObjectNode autoConfiguration, ArrayNode features) {
        ObjectNode result = objectMapper.createObjectNode();
        result.put("releaseTest", true);
        result.put("releaseVersion", autoConfiguration.has("releaseVersion") ? autoConfiguration.get("releaseVersion").asText("") : "");
        result.put("project", autoConfiguration.has("fastProject") ? autoConfiguration.get("fastProject").asText("") : "");
        result.put("group", autoConfiguration.has("group") ? autoConfiguration.get("group").asText("") : "");
        result.put("suite", autoConfiguration.has("suite") ? autoConfiguration.get("suite").asText("") : "");
        result.put("runBy", autoConfiguration.has("runBy") ? autoConfiguration.get("runBy").asText("") : "");
        String env = (autoConfiguration.has("region") ? autoConfiguration.get("region").asText("") : "") +
                (autoConfiguration.has("env") ? autoConfiguration.get("env").asText("") : "");
        result.put("env", env);
        result.put("testScope", autoConfiguration.has("testScope") ? autoConfiguration.get("testScope").asText("") : "");
        result.put("json", features.toString());
        result.put("buildId", autoConfiguration.has("buildId") ? autoConfiguration.get("buildId").asText("") : "");
        return result;
    }
}
