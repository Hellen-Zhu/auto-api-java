package citi.equities.lifecycleqa.common.enums;

public enum AutomationLoopKey {
    AssertLoopObject("assertLoopObject"),
    StepLoopObject("stepLoopObject");

    private final String key;

    AutomationLoopKey(String key) {
        this.key = key;
    }

    public String getKey() {
        return key;
    }
}
