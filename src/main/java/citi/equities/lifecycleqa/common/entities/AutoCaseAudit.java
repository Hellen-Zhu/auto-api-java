package citi.equities.lifecycleqa.common.entities;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import citi.equities.lifecycleqa.common.enums.AutomationRunMode;
import citi.equities.lifecycleqa.common.enums.AutomationRunStatus;
import lombok.Data;

import java.util.SortedMap;
import java.util.TreeMap;

@Data
public class AutoCaseAudit {
    private final String runId;
    private final String suite;
    private final String component;
    private final AutomationRunMode runMode;
    private final AutomationRunStatus runStatus;
    private final ObjectNode element;
    private final String caseId;
    private final String mainComponent;
    private final String componentLikeFirst;
    private final String scenario;
    private final String serviceName;
    private final String region;
    private final String env;
    private final String label;
    private final String issueKey;
    private final String summary;
    private final ArrayNode caseScript;
    private final ArrayNode templateScript;
    private final ObjectNode testDataString;
    private final ObjectNode caseVariables;
    private final ObjectNode templateVariables;
    private final boolean isTemplate;

    private SortedMap<Integer, JsonNode> script;
    private ObjectNode variables;

    public AutoCaseAudit(String runId, String suite, String component, AutomationRunMode runMode,
                         AutomationRunStatus runStatus, ObjectNode element, String caseId,
                         String mainComponent, String componentLikeFirst, String scenario,
                         String serviceName, String region, String env, String label,
                         String issueKey, String summary, ArrayNode caseScript,
                         ArrayNode templateScript, ObjectNode testDataString,
                         ObjectNode caseVariables, ObjectNode templateVariables, boolean isTemplate) {
        this.runId = runId;
        this.suite = suite;
        this.component = component;
        this.runMode = runMode;
        this.runStatus = runStatus;
        this.element = element;
        this.caseId = caseId;
        this.mainComponent = mainComponent;
        this.componentLikeFirst = componentLikeFirst;
        this.scenario = scenario;
        this.serviceName = serviceName;
        this.region = region;
        this.env = env;
        this.label = label;
        this.issueKey = issueKey;
        this.summary = summary;
        this.caseScript = caseScript;
        this.templateScript = templateScript;
        this.testDataString = testDataString;
        this.caseVariables = caseVariables;
        this.templateVariables = templateVariables;
        this.isTemplate = isTemplate;

        initScript();
        this.variables = isTemplate ? templateVariables : caseVariables;
    }

    private void initScript() {
        ArrayNode scriptArray = isTemplate ? templateScript : caseScript;
        this.script = new TreeMap<>();
        if (scriptArray != null) {
            for (JsonNode node : scriptArray) {
                int id = node.has("id") ? node.get("id").asInt(0) : 0;
                this.script.put(id, node);
            }
        }
    }
}
