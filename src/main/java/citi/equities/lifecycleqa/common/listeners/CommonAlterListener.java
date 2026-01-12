package citi.equities.lifecycleqa.common.listeners;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.github.f4b6a3.ulid.UlidCreator;
import citi.equities.lifecycleqa.common.config.InitialConfig;
import citi.equities.lifecycleqa.common.entities.SuiteParameter;
import citi.equities.lifecycleqa.common.enums.AutomationMode;
import citi.equities.lifecycleqa.common.enums.AutomationRunMode;
import citi.equities.lifecycleqa.common.enums.AutomationType;
import citi.equities.lifecycleqa.common.enums.Profile;
import citi.equities.lifecycleqa.common.helpers.SuiteGeneratePreparationHelper;
import citi.equities.lifecycleqa.common.utils.DBUtil;
import citi.equities.lifecycleqa.common.utils.DataTypeUtil;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.testng.IAlterSuiteListener;
import org.testng.xml.XmlSuite;
import org.testng.xml.XmlTest;

import java.util.*;

public class CommonAlterListener implements IAlterSuiteListener {
    private static final Logger log = LoggerFactory.getLogger(CommonAlterListener.class);
    private static final ObjectMapper objectMapper = new ObjectMapper();

    @Override
    public void alter(List<XmlSuite> suites) {
        try {
            InitialConfig.initEHConfigurationAndOthers();
            Map<String, String> params = suites.get(0).getParameters();
            XmlTest xmlTest = suites.get(0).getTests().get(0);
            StringBuilder errorMessage = new StringBuilder();

            if (!validateMandatoryParameters(params, errorMessage)) {
                return;
            }

            SuiteParameter suiteParameter = buildSuiteParameter(params);
            Object suiteBuildObject = SuiteGeneratePreparationHelper.prepareSuiteGenerateForSingleComponent(suiteParameter, errorMessage);
            JsonNode suiteJsonObject = DataTypeUtil.convertToJsonNode(suiteBuildObject);

            if (errorMessage.length() > 0 || !suiteJsonObject.isObject() || suiteJsonObject.isEmpty()) {
                handleSuiteBuildError((ObjectNode) suiteJsonObject, errorMessage);
                return;
            }

            suites.clear();
            suites.addAll(createXmlSuites(suiteParameter, xmlTest, (ObjectNode) suiteJsonObject));
        } catch (Exception e) {
            log.error("Failed Finish Initial Dynamic Suites with error {}", e.getMessage());
        }
    }

    private void handleSuiteBuildError(ObjectNode suiteJsonObject, StringBuilder errorMessage) {
        log.error("Failed to fetch suite build response: {}", errorMessage);
        if (suiteJsonObject.has("groupId") && suiteJsonObject.has("runId")) {
            String groupId = suiteJsonObject.get("groupId").asText("");
            String runId = suiteJsonObject.get("runId").asText("");
            StringBuilder err = new StringBuilder();
            DBUtil.executeLIF(
                    "delete from report_progress where group_id = '" + groupId + "' and run_id = '" + runId + "'",
                    true,
                    err
            );
            if (err.length() > 0) {
                log.error("Failed to delete report_progress with error {}", err);
            }
        }
    }

    private boolean validateMandatoryParameters(Map<String, String> params, StringBuilder errorMessage) {
        validateParam(params, "automationType", AutomationType::isValid,
                "errorMsg: `automationType` must be provided in testng.xml, value can be [API, UI]", errorMessage);
        validateParam(params, "runMode", AutomationRunMode::isValid,
                "errorMsg: `runMode` must be provided in testng.xml, value can be [TEST, DEBUG]", errorMessage);
        validateParam(params, "profile", Profile::isValid,
                "errorMsg: `profile` must be provided in testng.xml, value can be [" + Arrays.toString(Profile.values()) + "]", errorMessage);
        validateParam(params, "automationMode", AutomationMode::isValid,
                "errorMsg: `automationMode` must be provided in testng.xml, value can be [ID, COMPONENT]", errorMessage);

        String automationMode = params.get("automationMode");
        if (AutomationMode.ID.name().equals(automationMode)) {
            String componentOrId = params.get("componentOrId");
            if (componentOrId == null || !componentOrId.matches("\\d+")) {
                String errMsg = "errorMsg: `componentOrId` must be a valid integer in testng.xml when `automationMode` is `ID`";
                errorMessage.append(errMsg).append("\n");
                log.error(errMsg);
            }
        } else if (AutomationMode.COMPONENT.name().equals(automationMode)) {
            String componentOrId = params.get("componentOrId");
            if (componentOrId == null || componentOrId.matches("\\d+")) {
                String errMsg = "errorMsg: `componentOrId` must be a valid string (not an integer) in testng.xml when `automationMode` is `COMPONENT`";
                errorMessage.append(errMsg).append("\n");
                log.error(errMsg);
            }
        }

        return errorMessage.length() == 0;
    }

    private void validateParam(Map<String, String> params, String paramName,
                               java.util.function.Predicate<String> isValid, String errorMsg, StringBuilder errorMessage) {
        String value = params.get(paramName);
        if (value == null || value.isBlank() || !isValid.test(value)) {
            errorMessage.append(errorMsg).append("\n");
            log.error(errorMsg);
        }
    }

    private SuiteParameter buildSuiteParameter(Map<String, String> params) {
        SuiteParameter sp = new SuiteParameter(
                params.getOrDefault("runId", UlidCreator.getUlid().toString()),
                AutomationType.fromString(params.getOrDefault("automationType", "")),
                AutomationRunMode.fromString(params.getOrDefault("runMode", "")),
                AutomationMode.fromString(params.getOrDefault("automationMode", "")),
                Profile.fromString(params.getOrDefault("profile", "")),
                params.getOrDefault("componentOrId", ""),
                params.getOrDefault("runBy", ""),
                Boolean.parseBoolean(params.getOrDefault("sanityOnly", "false"))
        );
        sp.setProjectKey(params.getOrDefault("projectKey", ""));
        sp.setReleaseVersion(params.getOrDefault("releaseVersion", ""));
        sp.setGroupId(params.get("groupId"));
        return sp;
    }

    private List<XmlSuite> createXmlSuites(SuiteParameter suiteParameter, XmlTest xmlTest, ObjectNode suiteBuildJsonObject) {
        Iterator<String> keys = suiteBuildJsonObject.fieldNames();
        String component = keys.hasNext() ? keys.next() : "";

        JsonNode componentNode = suiteBuildJsonObject.get(component);
        ObjectNode config = componentNode != null && componentNode.has("config") && componentNode.get("config").isObject()
                ? (ObjectNode) componentNode.get("config")
                : objectMapper.createObjectNode();
        ObjectNode scenarios = componentNode != null && componentNode.has("scenarios") && componentNode.get("scenarios").isObject()
                ? (ObjectNode) componentNode.get("scenarios")
                : objectMapper.createObjectNode();
        String runId = suiteBuildJsonObject.has("runId") ? suiteBuildJsonObject.get("runId").asText("") : "";

        Map<String, String> suiteParams = new HashMap<>();
        suiteParams.put("runId", suiteParameter.getRunId());
        suiteParams.put("runMode", suiteParameter.getRunMode().name());
        suiteParams.put("component", component);
        suiteParams.put("scenarioAudits", scenarios.toString());
        suiteParams.put("config", config.toString());
        if (suiteParameter.getGroupId() != null) {
            suiteParams.put("groupId", suiteParameter.getGroupId());
        }

        XmlSuite newSuite = new XmlSuite();
        newSuite.setName(config.has("suite") ? config.get("suite").asText("") : "");
        newSuite.setParameters(suiteParams);

        List<XmlTest> xmlTests = new ArrayList<>();
        scenarios.fieldNames().forEachRemaining(scenarioName -> {
            JsonNode scenarioData = scenarios.get(scenarioName);
            XmlTest clonedTest = (XmlTest) xmlTest.clone();
            clonedTest.setName(scenarioName);
            Map<String, String> testParams = new HashMap<>();
            testParams.put("runId", runId);
            testParams.put("scenario", scenarioName);
            testParams.put("audits", scenarioData.toString());
            clonedTest.setParameters(testParams);
            clonedTest.setPreserveOrder(false);
            xmlTests.add(clonedTest);
        });

        newSuite.setTests(xmlTests);
        return Collections.singletonList(newSuite);
    }
}
