package citi.equities.lifecycleqa.common.entities;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@AllArgsConstructor
@NoArgsConstructor
public class SqlObject {
    private boolean isSelect;
    private int rowNumber;
    private int columnNumber;
}
