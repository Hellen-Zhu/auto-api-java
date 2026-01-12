package citi.equities.lifecycleqa.common.entities;

import com.alibaba.fastjson.JSON;
import com.alibaba.fastjson.JSONArray;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@AllArgsConstructor
@NoArgsConstructor
public class APIAutoCaseForUISingleIdResponse {
    private int id;
    private boolean enable;
    private Boolean isSanity;
    private String component;
    private String template;
    private String issueKey;
    private String summary;
    private String testData;
    private String scenario;
    private String label;
    private String componentLikeStr;
    private String profileStr;
    private String caseVariables;
    private String script;
    private String templateVariables;
    private Boolean isTemplate;
    private String endpoint;
    private String method;
    private String endpointLike;
    private boolean authIgnore;
    private boolean sslIgnore;
    private float namuat;
    private float namqa;
    private float emeauat;
    private float emeaqa;
    private float apacuat;
    private float apacdev;
    private float apacqa;
    private float average;

    public String[] getProfile() {
        return parseJsonArray(profileStr);
    }

    public String[] getComponentLike() {
        return parseJsonArray(componentLikeStr);
    }

    private String[] parseJsonArray(String jsonArrayStr) {
        if (jsonArrayStr == null || jsonArrayStr.isEmpty()) {
            return null;
        }
        JSONArray jsonArray = JSON.parseArray(jsonArrayStr);
        String[] result = new String[jsonArray.size()];
        for (int i = 0; i < jsonArray.size(); i++) {
            result[i] = jsonArray.getString(i);
        }
        return result;
    }
}
