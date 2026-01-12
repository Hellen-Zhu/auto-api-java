package citi.equities.lifecycleqa.common.entities;

import lombok.Data;
import java.util.ArrayList;
import java.util.List;

@Data
public class Step {
    private Result result;
    private int line;
    private String name;
    private String keyword;
    private int seqNumber;
    private List<String> output = new ArrayList<>();

    public Step() {
    }
}
