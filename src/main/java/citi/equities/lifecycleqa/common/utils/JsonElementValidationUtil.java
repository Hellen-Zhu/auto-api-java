package citi.equities.lifecycleqa.common.utils;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import citi.equities.lifecycleqa.common.enums.JsonElementValidatorCondition;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Map;

public class JsonElementValidationUtil {
    private static final Logger log = LoggerFactory.getLogger(JsonElementValidationUtil.class);

    public static boolean validate(JsonNode keyElement, JsonNode valueElement, String conditionString, StringBuilder errorMessage) {
        JsonNode key = keyElement;
        JsonNode value = valueElement;

        String finalCondition;
        switch (conditionString) {
            case "IsEqual":
                finalCondition = (key.isObject() || key.isArray()) ?
                        JsonElementValidatorCondition.IsJsonEqual.name() :
                        JsonElementValidatorCondition.IsEqual.name();
                break;
            case "IsNotEqual":
                finalCondition = (key.isObject() || key.isArray()) ?
                        JsonElementValidatorCondition.IsJsonNotEqual.name() :
                        JsonElementValidatorCondition.IsNotEqual.name();
                break;
            case "IsIn":
                if (value.isArray()) {
                    key = valueElement;
                    value = keyElement;
                    finalCondition = JsonElementValidatorCondition.IsJsonContain.name();
                } else {
                    finalCondition = JsonElementValidatorCondition.IsIn.name();
                }
                break;
            case "IsNotIn":
                if (value.isArray()) {
                    key = valueElement;
                    value = keyElement;
                    finalCondition = JsonElementValidatorCondition.IsJsonNotContain.name();
                } else {
                    finalCondition = JsonElementValidatorCondition.IsNotIn.name();
                }
                break;
            default:
                finalCondition = conditionString;
        }

        return validateElement(key, value, finalCondition, errorMessage);
    }

    private static boolean validateElement(JsonNode key, JsonNode value, String finalCondition, StringBuilder errorMessage) {
        try {
            JsonElementValidatorCondition condition = JsonElementValidatorCondition.valueOf(finalCondition);

            switch (condition) {
                case IsEqual:
                case IsNotEqual:
                    return isPrimitiveEqual(key, value, condition == JsonElementValidatorCondition.IsEqual);
                case IsLargerThan:
                case IsLessThan:
                case IsLargerThanOrEqual:
                case IsLessThanOrEqual:
                    return processIsLargerOrLessThan(key, value, condition);
                case IsNull:
                case IsNotNull:
                    return processIsNullOrNotNull(key, condition == JsonElementValidatorCondition.IsNull);
                case IsContain:
                case IsNotContain:
                case IsJsonContain:
                case IsJsonNotContain:
                    return processIsContainOrNotContain(key, value,
                            condition == JsonElementValidatorCondition.IsContain ||
                            condition == JsonElementValidatorCondition.IsJsonContain);
                case IsJsonEqual:
                case IsJsonNotEqual:
                    return processIsJsonEqualOrJsonNotEqual(key, value,
                            condition == JsonElementValidatorCondition.IsJsonEqual, errorMessage);
                case IsTrue:
                case IsFalse:
                    return processIsTrueOrFalse(key, condition == JsonElementValidatorCondition.IsTrue);
                case IsRegexMatch:
                case IsRegexNotMatch:
                    return processIsRegexMatchOrNotMatch(key, value,
                            condition == JsonElementValidatorCondition.IsRegexMatch);
                default:
                    log.error("Unsupported condition: {}", condition);
                    return false;
            }
        } catch (IllegalArgumentException e) {
            log.error("Invalid condition: {}", finalCondition);
            return false;
        }
    }

    private static boolean processIsContainOrNotContain(JsonNode key, JsonNode value, boolean flag) {
        if (key.isTextual() && value.isTextual()) {
            String keyStr = key.asText();
            String valueStr = value.asText();
            return keyStr.contains(valueStr) == flag;
        }
        if (key.isObject() && value.isObject()) {
            return isJsonObjectContain((ObjectNode) key, (ObjectNode) value) == flag;
        }
        if (key.isArray() && value.isArray()) {
            return isJsonArrayContain((ArrayNode) key, (ArrayNode) value) == flag;
        }
        if (key.isArray() && value.isObject()) {
            return isJsonArrayContainsObject((ArrayNode) key, value) == flag;
        }
        if (key.isArray() && !value.isArray()) {
            return isJsonArrayContainsValue((ArrayNode) key, value) == flag;
        }
        return false;
    }

    private static boolean processIsLargerOrLessThan(JsonNode key, JsonNode value,
                                                      JsonElementValidatorCondition comparisonType) {
        try {
            if (!key.isNumber() && !value.isNumber()) {
                if (key.isTextual() && value.isTextual()) {
                    double keyNum = Double.parseDouble(key.asText());
                    double valueNum = Double.parseDouble(value.asText());
                    return compareNumbers(keyNum, valueNum, comparisonType);
                }
                return false;
            }
            double keyNum = key.asDouble();
            double valueNum = value.asDouble();
            return compareNumbers(keyNum, valueNum, comparisonType);
        } catch (Exception e) {
            log.error("Key and Value should be numeric types for comparison operations");
            return false;
        }
    }

    private static boolean compareNumbers(double keyNum, double valueNum,
                                           JsonElementValidatorCondition comparisonType) {
        switch (comparisonType) {
            case IsLargerThan:
                return keyNum > valueNum;
            case IsLessThan:
                return keyNum < valueNum;
            case IsLargerThanOrEqual:
                return keyNum >= valueNum;
            case IsLessThanOrEqual:
                return keyNum <= valueNum;
            default:
                return false;
        }
    }

    private static boolean processIsNullOrNotNull(JsonNode key, boolean isNull) {
        return key.isNull() == isNull;
    }

    private static boolean processIsJsonEqualOrJsonNotEqual(JsonNode key, JsonNode value,
                                                             boolean isJsonEqual, StringBuilder errorMessage) {
        List<String> differences = new ArrayList<>();
        boolean isEqual = compareJsonElements(key, value, differences, "");
        if (!differences.isEmpty()) {
            errorMessage.append(String.join(", ", differences));
        }
        return isEqual == isJsonEqual;
    }

    private static boolean processIsTrueOrFalse(JsonNode key, boolean flag) {
        if (key.isBoolean()) {
            return key.asBoolean() == flag;
        }
        return false;
    }

    private static boolean processIsRegexMatchOrNotMatch(JsonNode key, JsonNode value, boolean flag) {
        if (key.isTextual() && value.isTextual()) {
            String keyStr = key.asText();
            String valueStr = value.asText();
            boolean matches = keyStr.matches(valueStr);
            return matches == flag;
        }
        log.error("Key and Value should be in String for IsRegexMatch or IsRegexNotMatch operation");
        return false;
    }

    private static boolean isJsonObjectContain(ObjectNode container, ObjectNode containee) {
        Iterator<Map.Entry<String, JsonNode>> fields = containee.fields();
        while (fields.hasNext()) {
            Map.Entry<String, JsonNode> entry = fields.next();
            String key = entry.getKey();
            JsonNode containeeValue = entry.getValue();

            if (!container.has(key)) {
                return false;
            }

            JsonNode containerValue = container.get(key);
            if (!compareJsonElements(containerValue, containeeValue, new ArrayList<>(), key)) {
                return false;
            }
        }
        return true;
    }

    private static boolean isJsonArrayContain(ArrayNode container, ArrayNode containee) {
        for (JsonNode containeeElement : containee) {
            boolean found = false;
            for (JsonNode containerElement : container) {
                if (compareJsonElements(containerElement, containeeElement, new ArrayList<>(), "")) {
                    found = true;
                    break;
                }
            }
            if (!found) {
                return false;
            }
        }
        return true;
    }

    private static boolean isJsonArrayContainsObject(ArrayNode container, JsonNode value) {
        for (JsonNode element : container) {
            if (compareJsonElements(element, value, new ArrayList<>(), "")) {
                return true;
            }
        }
        return false;
    }

    private static boolean isJsonArrayContainsValue(ArrayNode container, JsonNode value) {
        for (JsonNode element : container) {
            if (isPrimitiveEqual(element, value, true)) {
                return true;
            }
        }
        return false;
    }

    private static boolean compareJsonElements(JsonNode left, JsonNode right,
                                                List<String> differences, String path) {
        if (left.isObject() && right.isObject()) {
            return compareJsonObjects((ObjectNode) left, (ObjectNode) right, differences, path);
        }
        if (left.isArray() && right.isArray()) {
            return compareJsonArrays((ArrayNode) left, (ArrayNode) right, differences, path);
        }
        if (left.isValueNode() && right.isValueNode()) {
            return isPrimitiveEqual(left, right, true);
        }
        differences.add("Type mismatch at " + path + ": " + left + " vs " + right);
        return false;
    }

    private static boolean compareJsonObjects(ObjectNode left, ObjectNode right,
                                               List<String> differences, String path) {
        Iterator<String> leftFields = left.fieldNames();
        Iterator<String> rightFields = right.fieldNames();

        List<String> leftKeys = new ArrayList<>();
        List<String> rightKeys = new ArrayList<>();
        leftFields.forEachRemaining(leftKeys::add);
        rightFields.forEachRemaining(rightKeys::add);

        for (String key : leftKeys) {
            String newPath = path.isEmpty() ? key : path + "." + key;
            if (!right.has(key)) {
                differences.add("Missing key in right at " + newPath);
                return false;
            }
            if (!compareJsonElements(left.get(key), right.get(key), differences, newPath)) {
                return false;
            }
        }

        for (String key : rightKeys) {
            if (!left.has(key)) {
                String newPath = path.isEmpty() ? key : path + "." + key;
                differences.add("Missing key in left at " + newPath);
                return false;
            }
        }

        return true;
    }

    private static boolean compareJsonArrays(ArrayNode left, ArrayNode right,
                                              List<String> differences, String path) {
        if (left.size() != right.size()) {
            differences.add("Array size mismatch at " + path + ": " + left.size() + " vs " + right.size());
            return false;
        }

        for (int i = 0; i < left.size(); i++) {
            String newPath = path + "[" + i + "]";
            if (!compareJsonElements(left.get(i), right.get(i), differences, newPath)) {
                return false;
            }
        }
        return true;
    }

    private static boolean isPrimitiveEqual(JsonNode left, JsonNode right, boolean shouldBeEqual) {
        if (left.isTextual() && left.asText().equals("${ignore}") ||
            right.isTextual() && right.asText().equals("${ignore}")) {
            return true;
        }

        boolean isEqual;
        if (left.isTextual() && right.isTextual()) {
            isEqual = isPrimitiveStringEqual(left.asText(), right.asText());
        } else if (left.isBoolean() && right.isBoolean()) {
            isEqual = left.asBoolean() == right.asBoolean();
        } else if (left.isNull() && right.isNull()) {
            isEqual = true;
        } else if (left.isNumber() && right.isNumber()) {
            isEqual = left.asDouble() == right.asDouble();
        } else {
            isEqual = left.asText().equals(right.asText());
        }

        return shouldBeEqual ? isEqual : !isEqual;
    }

    private static boolean isPrimitiveStringEqual(String leftStr, String rightStr) {
        if (rightStr.startsWith("%") && rightStr.endsWith("%")) {
            String pattern = rightStr.substring(1, rightStr.length() - 1);
            return leftStr.toLowerCase().contains(pattern.toLowerCase());
        }
        if (rightStr.startsWith("%")) {
            String pattern = rightStr.substring(1);
            return leftStr.toLowerCase().endsWith(pattern.toLowerCase());
        }
        if (rightStr.endsWith("%")) {
            String pattern = rightStr.substring(0, rightStr.length() - 1);
            return leftStr.toLowerCase().startsWith(pattern.toLowerCase());
        }
        return leftStr.equals(rightStr);
    }
}
