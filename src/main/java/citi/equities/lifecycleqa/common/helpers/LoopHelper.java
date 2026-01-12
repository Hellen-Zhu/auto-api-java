package citi.equities.lifecycleqa.common.helpers;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.fasterxml.jackson.databind.node.TextNode;
import citi.equities.lifecycleqa.common.utils.DataTypeUtil;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.HashMap;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class LoopHelper {
    private static final Logger log = LoggerFactory.getLogger(LoopHelper.class);
    private static final ObjectMapper objectMapper = new ObjectMapper();

    public static Map<Integer, ObjectNode> handleLoopForJson(
            ObjectNode lowercaseObject, String loopName, int loopSize,
            String loopIndexKey, StringBuilder errorMessage) {

        String id = lowercaseObject.has("id") ? lowercaseObject.get("id").asText("") : "";
        Map<Integer, ObjectNode> stepArray = new HashMap<>();

        ObjectNode jsonObjectAfterRemoveBeforeLoop = DataTypeUtil.removeLoopKeyFromJsonObject(lowercaseObject, "beforeLoop");
        ObjectNode jsonObjectAfterRemoveLoop = DataTypeUtil.removeLoopKeyFromJsonObject(jsonObjectAfterRemoveBeforeLoop, "loop");

        for (int i = 0; i < loopSize; i++) {
            String cleanLoopName = loopName.replace("{{", "").replace("}}", "");
            JsonNode element = replaceLoopObjectWithTestData(
                    jsonObjectAfterRemoveLoop,
                    cleanLoopName + "." + i,
                    loopIndexKey + id,
                    errorMessage
            );
            if (element.isObject()) {
                stepArray.put(i, (ObjectNode) element);
            }
        }

        return stepArray;
    }

    private static JsonNode replaceLoopObjectWithTestData(
            JsonNode jsonElement, String loopKey, String loopIndexKey, StringBuilder errorMessage) {

        if (errorMessage.length() > 0) {
            return jsonElement;
        }

        try {
            Pattern regex = Pattern.compile("\\[\\[" + Pattern.quote(loopIndexKey) + "(\\..*?)?]]");

            if (jsonElement.isObject()) {
                ObjectNode newNode = objectMapper.createObjectNode();
                jsonElement.fields().forEachRemaining(entry -> {
                    JsonNode newValue = replaceLoopObjectWithTestData(
                            entry.getValue(), loopKey, loopIndexKey, errorMessage);
                    newNode.set(entry.getKey(), newValue);
                });
                return newNode;
            } else if (jsonElement.isArray()) {
                ArrayNode newArray = objectMapper.createArrayNode();
                for (JsonNode element : jsonElement) {
                    newArray.add(replaceLoopObjectWithTestData(element, loopKey, loopIndexKey, errorMessage));
                }
                return newArray;
            } else if (jsonElement.isTextual()) {
                String content = jsonElement.asText();
                Matcher matcher = regex.matcher(content);
                StringBuffer result = new StringBuffer();

                while (matcher.find()) {
                    String key = matcher.group(1);
                    String replacement;
                    if (key == null || key.isEmpty()) {
                        replacement = "{{" + loopKey + "}}";
                    } else {
                        replacement = "{{" + loopKey + key + "}}";
                    }
                    matcher.appendReplacement(result, Matcher.quoteReplacement(replacement));
                }
                matcher.appendTail(result);
                return new TextNode(result.toString());
            }

            return jsonElement;
        } catch (Exception e) {
            DataTypeUtil.appendErrorMessage(errorMessage,
                    "\nError when replaceLoopObjectWithTestData: " + e.getMessage());
            return jsonElement;
        }
    }
}
