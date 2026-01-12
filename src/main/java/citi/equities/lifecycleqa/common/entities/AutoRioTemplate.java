package citi.equities.lifecycleqa.common.entities;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@AllArgsConstructor
@NoArgsConstructor
public class AutoRioTemplate {
    private String name;
    private String projectKey;
    private String component;
    private String feed;
    private String bookingSystem;
    private String region;
    private String baseline;
    private String ignoreTags;
}
