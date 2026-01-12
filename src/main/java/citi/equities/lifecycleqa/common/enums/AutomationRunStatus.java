package citi.equities.lifecycleqa.common.enums;

public enum AutomationRunStatus {
    PENDING, PASSED, FAILED, SKIPPED, PROCESSING;

    public static boolean isValid(String value) {
        if (value == null) return false;
        for (AutomationRunStatus r : AutomationRunStatus.values()) {
            if (r.name().equalsIgnoreCase(value)) {
                return true;
            }
        }
        return false;
    }

    public static AutomationRunStatus fromString(String value) {
        for (AutomationRunStatus r : AutomationRunStatus.values()) {
            if (r.name().equalsIgnoreCase(value)) {
                return r;
            }
        }
        return PENDING;
    }
}
