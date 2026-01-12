package citi.equities.lifecycleqa.common.entities;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@AllArgsConstructor
@NoArgsConstructor
public class AutomationResult {
    private int returnCode;
    private String returnMessage;
    private Object returnBody;

    public AutomationResult(int returnCode, String returnMessage) {
        this.returnCode = returnCode;
        this.returnMessage = returnMessage;
        this.returnBody = null;
    }

    public AutomationResult(int returnCode, Object returnBody) {
        this.returnCode = returnCode;
        this.returnMessage = "";
        this.returnBody = returnBody;
    }

    @Override
    public String toString() {
        return "AutomationResult(returnCode=" + returnCode +
               ", returnMessage=" + returnMessage +
               ", returnBody=" + returnBody + ")";
    }
}
