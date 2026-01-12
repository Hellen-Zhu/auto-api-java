package citi.equities.lifecycleqa.common.helpers;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.NullNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.fasterxml.jackson.databind.node.TextNode;
import citi.equities.lifecycleqa.common.entities.AutomationStepBasicInfo;
import citi.equities.lifecycleqa.common.enums.JsonElementValidatorCondition;
import citi.equities.lifecycleqa.common.utils.DataTypeUtil;
import citi.equities.lifecycleqa.common.utils.JsonElementValidationUtil;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class DBAssertionHelper {
    private static final Logger log = LoggerFactory.getLogger(DBAssertionHelper.class);

    public static boolean handleJsonArrayForDBAssert(
            AutomationStepBasicInfo baseInfo, ArrayNode assertArray,
            StringBuilder output, StringBuilder errorMessage) {

        for (JsonNode element : assertArray) {
            JsonNode executeElement = PlaceholderReplaceHelper.replaceDataObjectForTestDataAndAutoSystemVariables(
                    baseInfo, element, errorMessage);

            if (errorMessage.length() > 0) {
                return false;
            }

            if (!executeElement.isObject()) {
                continue;
            }

            ObjectNode executeObj = (ObjectNode) executeElement;
            JsonNode key = executeObj.has("key") ? executeObj.get("key") : new TextNode("Not found key");
            String condition = executeObj.has("condition") ?
                    executeObj.get("condition").asText(JsonElementValidatorCondition.UnknownCondition.name()) :
                    JsonElementValidatorCondition.UnknownCondition.name();
            JsonNode value = executeObj.has("value") ? executeObj.get("value") : NullNode.getInstance();
            StringBuilder errLog = new StringBuilder();

            boolean validateValue = JsonElementValidationUtil.validate(key, value, condition, errLog);

            if (!validateValue) {
                String appendReason = errLog.length() > 0 ? "with reason: " + errLog : "";
                String description = executeObj.has("description") ?
                        executeObj.get("description").asText("Missing Assert Description") :
                        "Missing Assert Description";
                DataTypeUtil.appendErrorMessage(errorMessage,
                        "\nError: Assertion failed for '" + description + "'" +
                        "\ncondition: " + condition +
                        "\nkey: " + key +
                        "\nvalue: " + value +
                        "\n" + appendReason);
                return false;
            } else {
                String description = executeObj.has("description") ?
                        executeObj.get("description").asText("Missing Assert Description") :
                        "Missing Assert Description";
                output.append("Success: Assertion passed for '").append(description).append("'\n");
                log.info("Success: Assertion passed for key: {}, value: {}, condition: {}", key, value, condition);
            }
        }
        return true;
    }
}
