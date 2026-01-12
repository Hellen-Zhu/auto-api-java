package citi.equities.lifecycleqa.common.entities;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

@Data
@AllArgsConstructor
@NoArgsConstructor
public class EndpointInfo {
    private String module;
    private String serviceName;
    private List<String> docUrlList;
    private String inactiveReason;
    private String path;
    private String method;
    private boolean isCoverage;
    private boolean isActive;
}
