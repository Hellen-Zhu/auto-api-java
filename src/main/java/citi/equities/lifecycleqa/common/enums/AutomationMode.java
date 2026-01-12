package citi.equities.lifecycleqa.common.enums;

public enum AutomationMode {
    ID, COMPONENT, MODULE, UNKNOWN;

    public static boolean isValid(String value) {
        if (value == null) return false;
        for (AutomationMode r : AutomationMode.values()) {
            if (r.name().equalsIgnoreCase(value)) {
                return true;
            }
        }
        return false;
    }

    public static AutomationMode fromString(String value) {
        for (AutomationMode r : AutomationMode.values()) {
            if (r.name().equalsIgnoreCase(value)) {
                return r;
            }
        }
        return UNKNOWN;
    }
}
