package citi.equities.lifecycleqa.common.entities;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@AllArgsConstructor
@NoArgsConstructor
public class AutomationStepBasicInfo {
    private boolean isStepDebug;
    private String runId;
    private int caseId;
    private String region;
    private String env;
    private ObjectNode variables;
    private int stepId;
    private String stepName;
    private int indexId;
    private ObjectNode stepObject;
}
