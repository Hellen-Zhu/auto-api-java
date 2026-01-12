package citi.equities.lifecycleqa.common.entities;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.Map;

@Data
@AllArgsConstructor
@NoArgsConstructor
public class Element {
    private int line;
    private String name;
    private String start_timestamp;  // 2024-06-28T08:53:24.854Z
    private String description;
    private String type;
    private String keyword;
    private Step[] steps = new Step[0];
    private String id;
    private long startRuntime;
    private long endRuntime;
    private int passedSteps;
    private long duration;
    private Map<?, ?> details;
}
