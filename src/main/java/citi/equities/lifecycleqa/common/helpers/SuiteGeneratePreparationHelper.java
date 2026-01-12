package citi.equities.lifecycleqa.common.helpers;

import citi.equities.lifecycleqa.common.enums.*;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import citi.equities.lifecycleqa.common.config.InitialConfig;
import citi.equities.lifecycleqa.common.entities.SuiteParameter;
import citi.equities.lifecycleqa.common.entities.TestNGXMLParameter;
import citi.equities.lifecycleqa.common.enums.*;
import citi.equities.lifecycleqa.common.utils.DBUtil;
import io.restassured.RestAssured;
import org.apache.ibatis.session.SqlSession;
import org.apache.ibatis.session.SqlSessionManager;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.*;

public class SuiteGeneratePreparationHelper {
    private static final Logger log = LoggerFactory.getLogger(SuiteGeneratePreparationHelper.class);
    private static final ObjectMapper objectMapper = new ObjectMapper();

    public static ObjectNode prepareSuiteGenerateForSingleComponent(SuiteParameter suiteParameter, StringBuilder errorMessage) {
        TestNGXMLParameter request = prepareTestNGXMLParameter(suiteParameter, errorMessage);
        if (errorMessage.length() > 0) {
            ObjectNode errorNode = objectMapper.createObjectNode();
            errorNode.put("error", errorMessage.toString());
            return errorNode;
        }

        if (!checkDBAndServiceStatusForComponent(request, errorMessage)) {
            ObjectNode errorNode = objectMapper.createObjectNode();
            errorNode.put("error", "No Test Cases found! DB or Service connection refused for region-`" + request.getRegion() +
                    "` env-`" + request.getEnv() + "`, component-`" + request.getComponent() + "` with errorMessage: " + errorMessage);
            return errorNode;
        }

        List<ObjectNode> autoCaseAudits = fetchAutoCaseAuditsForComponentOrId(suiteParameter.getAutomationType(), request, errorMessage);
        if (errorMessage.length() > 0) {
            ObjectNode errorNode = objectMapper.createObjectNode();
            errorNode.put("error", errorMessage.toString());
            return errorNode;
        }

        List<ObjectNode> autoCaseAuditsAfterFilter;
        if (suiteParameter.getAutomationMode() == AutomationMode.ID) {
            autoCaseAuditsAfterFilter = autoCaseAudits;
        } else {
            autoCaseAuditsAfterFilter = filterAutoCaseAuditsByLabels(suiteParameter.getProfile().name(), request, autoCaseAudits);
        }

        if (autoCaseAuditsAfterFilter.isEmpty()) {
            log.error("No Test Cases Found! Can not find any test cases after filter for request-{}", request);
            ObjectNode errorNode = objectMapper.createObjectNode();
            errorNode.put("error", "No Test Cases Found! Can not find any test cases after filter for request-" + request);
            return errorNode;
        }

        ObjectNode autoConfigurationsDB = fetchAutoConfigurationForSingleComponent(request, autoCaseAuditsAfterFilter, errorMessage);
        if (autoConfigurationsDB.isEmpty()) {
            log.error("No Test Cases Found! Can not find any configuration for request-{} with errorMessage={}", request, errorMessage);
            ObjectNode errorNode = objectMapper.createObjectNode();
            errorNode.put("error", "No Test Cases Found! Can not find any configuration for request-" + request + " with errorMessage=" + errorMessage);
            return errorNode;
        }

        // Group scenarios
        Map<String, List<ObjectNode>> groupedScenarios = new LinkedHashMap<>();
        for (ObjectNode audit : autoCaseAuditsAfterFilter) {
            String scenarioName = audit.has("scenario") ? audit.get("scenario").asText("") : "";
            groupedScenarios.computeIfAbsent(scenarioName, k -> new ArrayList<>()).add(audit);
        }

        // Build result
        ObjectNode scenariosNode = objectMapper.createObjectNode();
        for (Map.Entry<String, List<ObjectNode>> entry : groupedScenarios.entrySet()) {
            ArrayNode auditsArray = objectMapper.createArrayNode();
            for (ObjectNode audit : entry.getValue()) {
                auditsArray.add(audit);
            }
            scenariosNode.set(entry.getKey(), auditsArray);
        }

        ObjectNode requestNode = objectMapper.createObjectNode();
        requestNode.set("scenarios", scenariosNode);
        requestNode.set("config", autoConfigurationsDB);

        ObjectNode result = objectMapper.createObjectNode();
        result.set(request.getComponent(), requestNode);
        result.put("runId", request.getRunId());
        result.put("groupId", suiteParameter.getGroupId());

        return result;
    }

    private static ObjectNode buildFinalTestDataBasedOnProfile(JsonNode testData, String profile) {
        String p = profile.toLowerCase();
        Set<String> knownProfiles = new HashSet<>(Arrays.asList("namuat", "emeauat", "apacuat", "namdev", "emeadev", "apacdev", "namqa", "emeaqa", "apacqa"));

        ObjectNode result = objectMapper.createObjectNode();

        // Add non-profile keys first
        if (testData.isObject()) {
            testData.fieldNames().forEachRemaining(key -> {
                if (!knownProfiles.contains(key.toLowerCase()) && !key.equalsIgnoreCase("default")) {
                    result.set(key, testData.get(key));
                }
            });
        }

        // Override with profile-specific data
        if (testData.has(p) && testData.get(p).isObject()) {
            testData.get(p).fieldNames().forEachRemaining(key -> {
                result.set(key, testData.get(p).get(key));
            });
        }

        return result;
    }

    private static TestNGXMLParameter prepareTestNGXMLParameter(SuiteParameter suiteParameter, StringBuilder errorMessage) {
        switch (suiteParameter.getAutomationMode()) {
            case ID:
                return TestNGXMLParameter.fetchInstanceForIdRun(suiteParameter);
            case COMPONENT:
                return TestNGXMLParameter.fetchInstanceForComponentRun(suiteParameter);
            default:
                errorMessage.append("Invalid mode=").append(suiteParameter.getAutomationMode())
                        .append(" for suiteParameter=").append(suiteParameter).append("\n");
                log.error("Invalid mode={} for suiteParameter={}", suiteParameter.getAutomationMode(), suiteParameter);
                return new TestNGXMLParameter(
                        AutomationType.API, "", AutomationRunMode.DEBUG, false, "",
                        "", "", Profile.DEV, "", null, ""
                );
        }
    }

    private static boolean checkDBAndServiceStatusForComponent(TestNGXMLParameter request, StringBuilder errorMessage) {
        if (!checkDBStatus(request.getComponent(), request.getRegion(), request.getEnv(), errorMessage)) {
            errorMessage.append("DB connection refused for region='").append(request.getRegion())
                    .append("', env='").append(request.getEnv())
                    .append("', component='").append(request.getComponent()).append("'\n");
            log.error("DB connection refused for region='{}', env='{}', component='{}'",
                    request.getRegion(), request.getEnv(), request.getComponent());
            return false;
        }
        if (!checkSVCSStatus(request.getComponent(), request.getRegion(), request.getEnv(), errorMessage)) {
            errorMessage.append("Service connection refused for region='").append(request.getRegion())
                    .append("', env='").append(request.getEnv())
                    .append("', component='").append(request.getComponent()).append("'\n");
            log.error("Service connection refused for region='{}', env='{}', component='{}'",
                    request.getRegion(), request.getEnv(), request.getComponent());
            return false;
        }
        return true;
    }

    private static List<ObjectNode> fetchAutoCaseAuditsForComponentOrId(
            AutomationType automationType,
            TestNGXMLParameter request,
            StringBuilder errorMessage) {

        Map<String, Object> queryParams = new HashMap<>();
        queryParams.put("component", request.getComponent());
        queryParams.put("region", request.getRegion());
        queryParams.put("profile", request.getProfile().name());
        queryParams.put("sanityOnly", String.valueOf(request.isSanityOnly()));
        queryParams.put("runMode", request.getRunMode().name());
        queryParams.put("runStatus", "PENDING");
        queryParams.put("runId", request.getRunId());

        if (request.getId() != null) {
            queryParams.put("id", request.getId().toString());
        }

        String statementName;
        if (automationType == AutomationType.API) {
            statementName = LIFDBStatement.FetchAutoCaseAuditBaseOnComponentForAPI.name();
        } else {
            statementName = LIFDBStatement.FetchAutoCaseAuditBaseOnComponentForUI.name();
        }

        try {
            JsonNode autoCaseAuditsFromDB = DBUtil.executeLIF(statementName, false, errorMessage, queryParams);
            if (errorMessage.length() > 0) {
                log.error("No Test Cases Found! Cannot find any test cases for request={} with errorMessage={}", request, errorMessage);
                return Collections.emptyList();
            }

            List<ObjectNode> result = new ArrayList<>();
            if (autoCaseAuditsFromDB.isArray()) {
                for (JsonNode audit : autoCaseAuditsFromDB) {
                    if (audit.isObject()) {
                        ObjectNode newAudit = objectMapper.createObjectNode();
                        audit.fieldNames().forEachRemaining(key -> {
                            if (key.equalsIgnoreCase("testData")) {
                                ObjectNode processedTestData = buildFinalTestDataBasedOnProfile(
                                        audit.get(key),
                                        request.getProfile().name().toLowerCase()
                                );
                                newAudit.set(key, processedTestData);
                            } else {
                                newAudit.set(key, audit.get(key));
                            }
                        });
                        result.add(newAudit);
                    }
                }
            }
            return result;
        } catch (Exception e) {
            log.error("No Test Cases Found! Can not find any test cases in auto_case_scenario table for request={}", request, e);
            return Collections.emptyList();
        }
    }

    private static List<ObjectNode> filterAutoCaseAuditsByLabels(String profileStr, TestNGXMLParameter request, List<ObjectNode> autoCaseAudits) {
        Set<String> componentLikeFirsts = new HashSet<>();
        for (ObjectNode audit : autoCaseAudits) {
            String componentLikeFirst = audit.has("componentLikeFirst") ? audit.get("componentLikeFirst").asText("") : "";
            if (!componentLikeFirst.isEmpty()) {
                componentLikeFirsts.add(componentLikeFirst);
            }
        }

        Map<String, List<String>> labelsMap = new HashMap<>();
        for (String clf : componentLikeFirsts) {
            List<String> labels = InitialConfig.getComponentDependentLabels(clf, profileStr);
            if (labels.isEmpty()) {
                labels = InitialConfig.getComponentDependentLabels(clf, "global");
            }
            if (!labels.isEmpty()) {
                labelsMap.put(clf, labels);
            }
        }

        List<ObjectNode> result = new ArrayList<>();
        for (ObjectNode audit : autoCaseAudits) {
            String componentLikeFirst = audit.has("componentLikeFirst") ? audit.get("componentLikeFirst").asText("") : "";
            String label = audit.has("label") ? audit.get("label").asText("") : "";

            if (componentLikeFirst.isEmpty() ||
                    !labelsMap.containsKey(componentLikeFirst) ||
                    labelsMap.get(componentLikeFirst).contains(label)) {
                result.add(audit);
            } else {
                log.error("No Test Cases Found after filtering for component={}", request.getComponent());
            }
        }
        return result;
    }

    private static ObjectNode fetchAutoConfigurationForSingleComponent(
            TestNGXMLParameter request,
            List<ObjectNode> autoCaseAuditsAfterFilter,
            StringBuilder errorMessage) {

        try {
            ObjectNode firstAudit = autoCaseAuditsAfterFilter.get(0);
            Map<String, Object> params = new HashMap<>();
            params.put("componentLikeFirst", firstAudit.has("componentLikeFirst") ? firstAudit.get("componentLikeFirst").asText("") : "");
            params.put("component", firstAudit.has("component") ? firstAudit.get("component").asText("") : "");
            params.put("mainComponent", firstAudit.has("mainComponent") ? firstAudit.get("mainComponent").asText("") : "");
            params.put("region", request.getRegion());
            params.put("env", request.getEnv());
            params.put("buildId", request.getBuildNumber());
            params.put("releaseVersion", request.getReleaseVersion());
            params.put("runId", request.getRunId());
            params.put("runMode", request.getRunMode().name());
            params.put("runBy", request.getRunBy());
            params.put("id", request.getId());
            params.put("projectKey", request.getProjectKey());

            JsonNode result = DBUtil.executeLIF(LIFDBStatement.FetchAutoConfigurationBasedOnSingleComponent.name(), false, errorMessage, params);
            if (result.isObject()) {
                return (ObjectNode) result;
            }
            return objectMapper.createObjectNode();
        } catch (Exception e) {
            log.error("No Test Cases Found! Can not find any config for request={} with errorMessage={}", request, errorMessage, e);
            return objectMapper.createObjectNode();
        }
    }

    private static boolean checkDBStatus(String component, String region, String env, StringBuilder errorMessage) {
        List<String> databasesList = InitialConfig.getComponentDependentDatabases(component, region + env);

        for (String dbType : databasesList) {
            String connectionName;
            if (dbType.equalsIgnoreCase("lif")) {
                connectionName = dbType.toLowerCase();
            } else {
                connectionName = DBUtil.buildSqlSessionManagerName(dbType, region, env);
            }

            boolean isConnected = false;
            try {
                SqlSessionManager sessionManager = InitialConfig.DB_CONFIG.get(connectionName);
                if (sessionManager != null) {
                    SqlSession session = sessionManager.openSession();
                    if (session != null && session.getConnection() != null) {
                        isConnected = !session.getConnection().isClosed();
                    }
                }
            } catch (Exception e) {
                isConnected = false;
            }

            if (!isConnected) {
                errorMessage.append("Database connection refused for region='").append(region)
                        .append("', env='").append(env)
                        .append("', dbType='").append(dbType).append("'\n");
                log.error("Database connection refused for region='{}', env='{}', dbType='{}'", region, env, dbType);
                return false;
            }
            log.info("DB Connection Status for region='{}', env='{}', dbType='{}': {}", region, env, dbType, isConnected);
        }
        return true;
    }

    private static boolean checkSVCSStatus(String component, String region, String env, StringBuilder errorMessage) {
        List<String> services = InitialConfig.getComponentDependentServices(component, region + env);

        List<String> urlList;
        try {
            urlList = new ArrayList<>();
            for (String service : services) {
                urlList.add(InitialConfig.fetchBaseUrl(service, region + env));
            }
        } catch (Exception e) {
            log.error("Target host is null for region='{}', env='{}'", region, env);
            errorMessage.append("Target host is null for region='").append(region).append("', env='").append(env).append("'\n");
            return false;
        }

        for (String url : urlList) {
            boolean isConnected;
            try {
                int statusCode = RestAssured.given().baseUri(url).get("/actuator/info").then().extract().statusCode();
                isConnected = statusCode >= 200 && statusCode <= 502;
            } catch (Exception e) {
                isConnected = false;
            }

            if (!isConnected) {
                errorMessage.append("Service connection refused for region='").append(region)
                        .append("', env='").append(env)
                        .append("', url='").append(url).append("'\n");
                log.error("Service connection refused for region='{}', env='{}', url='{}'", region, env, url);
                return false;
            }
            log.info("Service Connection Status for region='{}', env='{}', url='{}': {}", region, env, url, isConnected);
        }
        return true;
    }
}
