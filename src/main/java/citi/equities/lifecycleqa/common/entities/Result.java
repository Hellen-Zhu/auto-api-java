package citi.equities.lifecycleqa.common.entities;

import lombok.Data;

@Data
public class Result {
    private long duration;
    private String status;
    private String error_message;

    public Result() {
    }

    public Result(long duration, String status, String error_message) {
        this.duration = duration;
        this.status = status;
        this.error_message = error_message;
    }
}
