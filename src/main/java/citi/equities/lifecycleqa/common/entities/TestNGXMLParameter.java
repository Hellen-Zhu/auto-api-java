package citi.equities.lifecycleqa.common.entities;

import com.fasterxml.jackson.databind.JsonNode;
import citi.equities.lifecycleqa.common.enums.AutomationMode;
import citi.equities.lifecycleqa.common.enums.AutomationRunMode;
import citi.equities.lifecycleqa.common.enums.AutomationType;
import citi.equities.lifecycleqa.common.enums.Profile;
import citi.equities.lifecycleqa.common.utils.DBUtil;
import citi.equities.lifecycleqa.common.utils.DataTypeUtil;
import io.restassured.RestAssured;
import lombok.Data;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@Data
public class TestNGXMLParameter {
    private static final Logger log = LoggerFactory.getLogger(TestNGXMLParameter.class);

    private final AutomationType automationType;
    private final String runId;
    private final AutomationRunMode runMode;
    private boolean sanityOnly;
    private final String component;
    private final String projectKey;
    private final String releaseVersion;
    private final Profile profile;
    private final String serviceName;
    private final Integer id;
    private final String runBy;

    // Derived fields
    private final String region;
    private final String env;
    private final String buildNumber;

    public TestNGXMLParameter(AutomationType automationType, String runId, AutomationRunMode runMode,
                              boolean sanityOnly, String component, String projectKey,
                              String releaseVersion, Profile profile, String serviceName,
                              Integer id, String runBy) {
        this.automationType = automationType;
        this.runId = runId;
        this.runMode = runMode;
        this.sanityOnly = sanityOnly;
        this.component = component;
        this.projectKey = projectKey;
        this.releaseVersion = releaseVersion;
        this.profile = profile;
        this.serviceName = serviceName;
        this.id = id;
        this.runBy = runBy;

        this.region = DataTypeUtil.removeSubstringsForUpperCase(profile.name(), "QA", "DEV", "UAT");
        this.env = DataTypeUtil.removeSubstringsForUpperCase(profile.name(), "NAM", "EMEA", "APAC");

        if (automationType == AutomationType.API && runMode == AutomationRunMode.TEST
                && serviceName != null && !serviceName.isBlank()) {
            this.buildNumber = fetchBuildNumber(serviceName, region, env);
        } else {
            this.buildNumber = "";
        }
    }

    public static TestNGXMLParameter fetchInstanceForComponentRun(SuiteParameter suiteParameter) {
        String component = String.valueOf(suiteParameter.getComponentOrId());
        String[] result = getComponentAndProjectKeyAndReleaseVersionAndServiceName(
                suiteParameter.getAutomationType(),
                suiteParameter.getRunMode(),
                AutomationMode.COMPONENT,
                component,
                suiteParameter.getProjectKey(),
                suiteParameter.getReleaseVersion()
        );

        return new TestNGXMLParameter(
                suiteParameter.getAutomationType(),
                suiteParameter.getRunId(),
                suiteParameter.getRunMode(),
                suiteParameter.isSanityOnly(),
                result[0],
                result[1],
                result[2],
                suiteParameter.getProfile(),
                result[3],
                null,
                suiteParameter.getRunBy()
        );
    }

    public static TestNGXMLParameter fetchInstanceForIdRun(SuiteParameter suiteParameter) {
        int id = Integer.parseInt(String.valueOf(suiteParameter.getComponentOrId()));
        String[] result = getComponentAndProjectKeyAndReleaseVersionAndServiceName(
                AutomationType.API,
                AutomationRunMode.DEBUG,
                AutomationMode.ID,
                String.valueOf(id),
                null,
                null
        );

        return new TestNGXMLParameter(
                suiteParameter.getAutomationType(),
                suiteParameter.getRunId(),
                AutomationRunMode.DEBUG,
                false,
                result[0],
                result[1],
                result[2],
                suiteParameter.getProfile(),
                result[3],
                id,
                suiteParameter.getRunBy()
        );
    }

    private static String fetchBuildNumber(String serviceName, String region, String env) {
        String url = "http://mondo.nam.nsroot.net:8080/api/eh/service/buildService?serviceName=" +
                serviceName + "&region=" + region + "&env=" + env;
        try {
            String result = RestAssured.given().get(url).body().asString();
            log.info("Fetch buildNumber via {} with {}", url, result);
            return result != null ? result : "";
        } catch (Exception e) {
            log.error("Error fetching build number: {}", e.getMessage());
            return "";
        }
    }

    private static String[] getComponentAndProjectKeyAndReleaseVersionAndServiceName(
            AutomationType automationType, AutomationRunMode runMode,
            AutomationMode automationMode, String value, String projectKey, String releaseVersion) {

        String defaultSQL;
        if (automationMode == AutomationMode.COMPONENT) {
            defaultSQL = "select component, \"projectKey\", \"dailyrunVersion\", COALESCE(\"serviceName\",'') as \"serviceName\" " +
                    "from auto_api_configuration where component = '" + value + "' limit 1";
        } else if (automationMode == AutomationMode.ID) {
            String tableName;
            if (automationType == AutomationType.API) {
                tableName = "auto_case_scenario";
            } else if (automationType == AutomationType.UI) {
                tableName = "auto_case_ui";
            } else {
                tableName = "";
            }
            defaultSQL = "select a.component, a.\"projectKey\", a.\"dailyrunVersion\", COALESCE(a.\"serviceName\",'') as \"serviceName\" " +
                    "from auto_api_configuration a join " + tableName + " b on a.component = b.component where b.id = " + value + " limit 1";
        } else {
            defaultSQL = "";
        }

        StringBuilder errorMessage = new StringBuilder();
        JsonNode result = DBUtil.executeLIF(defaultSQL, true, errorMessage);

        String finalComponent = result.has("component") ? result.get("component").asText("") : "";
        String defaultProjectKey = result.has("projectKey") ? result.get("projectKey").asText("") : "";
        String defaultVersion = result.has("dailyrunVersion") ? result.get("dailyrunVersion").asText("") : "";
        String serviceName = result.has("serviceName") ? result.get("serviceName").asText("") : "";

        String finalProjectKey = (projectKey != null && !projectKey.isBlank()) ? projectKey : defaultProjectKey;
        String finalReleaseVersion = (releaseVersion != null && !releaseVersion.isBlank())
                ? releaseVersion
                : fetchReleaseVersion(runMode, defaultVersion, finalProjectKey);

        return new String[]{finalComponent, finalProjectKey, finalReleaseVersion, serviceName};
    }

    private static String fetchReleaseVersion(AutomationRunMode runMode, String defaultVersion, String projectKey) {
        if (runMode == AutomationRunMode.DEBUG) {
            return defaultVersion;
        }

        String url = "http://mondo.nam.nsroot.net:8080/api/releases/latestversion";
        try {
            String responseString = RestAssured.given().param("projectKey", projectKey).get(url).asString();
            JsonNode jsonNode = DataTypeUtil.convertToJsonNode(responseString);
            String version = jsonNode.has("version") ? jsonNode.get("version").asText("") : "";
            log.info("Fetch releaseVersion via {} with {}", url, version);
            if (version.isBlank() || "null".equalsIgnoreCase(version)) {
                return defaultVersion;
            }
            return version;
        } catch (Exception e) {
            log.error("Error fetching release version: {}", e.getMessage());
            return defaultVersion;
        }
    }
}
