package citi.equities.lifecycleqa.common.entities;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@AllArgsConstructor
@NoArgsConstructor
public class Feature {
    private int line;
    private Element[] elements = new Element[0];
    private String name;
    private String description;
    private String id;
    private String profile;
    private String keyword;
    private String uri;
    private int passedCases;
    private int totalCases;
}
