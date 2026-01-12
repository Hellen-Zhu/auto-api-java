package citi.equities.lifecycleqa.common.enums;

public enum AutomationRunMode {
    TEST, DEBUG;

    public static boolean isValid(String value) {
        if (value == null) return false;
        for (AutomationRunMode r : AutomationRunMode.values()) {
            if (r.name().equalsIgnoreCase(value)) {
                return true;
            }
        }
        return false;
    }

    public static AutomationRunMode fromString(String value) {
        for (AutomationRunMode r : AutomationRunMode.values()) {
            if (r.name().equalsIgnoreCase(value)) {
                return r;
            }
        }
        return DEBUG;
    }
}
