package citi.equities.lifecycleqa.common.entities;

import citi.equities.lifecycleqa.common.enums.AutomationMode;
import citi.equities.lifecycleqa.common.enums.AutomationRunMode;
import citi.equities.lifecycleqa.common.enums.AutomationType;
import citi.equities.lifecycleqa.common.enums.Profile;
import lombok.Data;

@Data
public class SuiteParameter {
    private final String runId;
    private final AutomationType automationType;
    private final AutomationRunMode runMode;
    private final AutomationMode automationMode;
    private final Profile profile;
    private Object componentOrId;
    private final String runBy;
    private final boolean sanityOnly;

    private String projectKey;
    private String releaseVersion;
    private String groupId;

    public SuiteParameter(String runId, AutomationType automationType, AutomationRunMode runMode,
                          AutomationMode automationMode, Profile profile, Object componentOrId,
                          String runBy, boolean sanityOnly) {
        this.runId = runId;
        this.automationType = automationType;
        this.runMode = runMode;
        this.automationMode = automationMode;
        this.profile = profile;
        this.componentOrId = componentOrId;
        this.runBy = runBy;
        this.sanityOnly = sanityOnly;
    }
}
