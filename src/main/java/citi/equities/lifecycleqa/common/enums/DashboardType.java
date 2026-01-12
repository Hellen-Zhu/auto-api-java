package citi.equities.lifecycleqa.common.enums;

public enum DashboardType {
    MONDO("MONDO", "Mondo Dashboard"),
    FAST("FAST", "Fast Dashboard"),
    ALL("ALL", "All Dashboards"),
    UNKNOWN("UNKNOWN", "Unknown Dashboard");

    private final String type;
    private final String description;

    DashboardType(String type, String description) {
        this.type = type;
        this.description = description;
    }

    public String getType() {
        return type;
    }

    public String getDescription() {
        return description;
    }

    public static DashboardType fromType(String type) {
        for (DashboardType dt : DashboardType.values()) {
            if (dt.type.equalsIgnoreCase(type)) {
                return dt;
            }
        }
        return UNKNOWN;
    }
}
