package citi.equities.lifecycleqa.common.enums;

public enum AutoStepRunStatus {
    Passed("passed"),
    Failed("failed"),
    Skipped("skipped"),
    Continue("continue"),
    TimeOut("timeout");

    private final String status;

    AutoStepRunStatus(String status) {
        this.status = status;
    }

    public String getStatus() {
        return status;
    }
}
