package citi.equities.lifecycleqa.common.helpers;

import lombok.Data;

import java.util.ArrayList;
import java.util.List;

@Data
public class FastTestNGStep {
    private String stepName;
    private long startTime;
    private List<String> messages = new ArrayList<>();

    public FastTestNGStep() {
    }

    public FastTestNGStep(String stepName, long startTime) {
        this.stepName = stepName;
        this.startTime = startTime;
    }

    public void addMessage(String message) {
        messages.add(message);
    }

    @Override
    public String toString() {
        return "FastTestNGStep(stepName=" + stepName + ", startTime=" + startTime + ", messages=" + messages + ")";
    }
}
