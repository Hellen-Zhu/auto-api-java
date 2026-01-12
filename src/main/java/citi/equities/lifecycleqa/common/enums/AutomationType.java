package citi.equities.lifecycleqa.common.enums;

public enum AutomationType {
    UI, API, UNKNOWN;

    public static boolean isValid(String value) {
        if (value == null) return false;
        for (AutomationType r : AutomationType.values()) {
            if (r.name().equalsIgnoreCase(value)) {
                return true;
            }
        }
        return false;
    }

    public static AutomationType fromString(String value) {
        for (AutomationType r : AutomationType.values()) {
            if (r.name().equalsIgnoreCase(value)) {
                return r;
            }
        }
        return UNKNOWN;
    }
}
