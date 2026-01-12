package citi.equities.lifecycleqa.common.utils;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.*;
import citi.equities.lifecycleqa.common.enums.DataType;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.*;

public class DataTypeUtil {
    private static final Logger log = LoggerFactory.getLogger(DataTypeUtil.class);
    private static final ObjectMapper objectMapper = new ObjectMapper();

    public static ObjectNode convertMapToJsonObject(Map<String, Object> map) {
        ObjectNode objectNode = objectMapper.createObjectNode();
        map.forEach((key, value) -> objectNode.set(key, convertToJsonNode(value)));
        return objectNode;
    }

    public static Map<String, String> convertStringToMapByTwoSeparator(String str, String regex, String secondSeparator) {
        Map<String, Set<String>> resultSet = new HashMap<>();
        String processStr = str.trim();
        if (processStr.endsWith(",")) {
            processStr = processStr.substring(0, processStr.length() - 1);
        }
        String[] splitArr = processStr.split(regex);

        for (String s : splitArr) {
            String[] secondArray = s.split(secondSeparator, 2);
            String key = secondArray[0];
            String value = secondArray.length > 1 ? secondArray[1] : "null";
            resultSet.computeIfAbsent(key, k -> new TreeSet<>()).add(value);
        }

        Map<String, String> result = new HashMap<>();
        resultSet.forEach((key, setValue) -> result.put(key, String.join(",", setValue)));
        return result;
    }

    public static String getParseDateFormat(String dateStr, List<String> formats) {
        if (dateStr == null) return null;
        for (String parse : formats) {
            try {
                SimpleDateFormat sdf = new SimpleDateFormat(parse);
                sdf.parse(dateStr);
                log.info("Printing the value of {} for [{}]", parse, dateStr);
                return parse;
            } catch (ParseException e) {
                // Ignore and continue
            }
        }
        return dateStr;
    }

    public static Date parseDate(String dateStr) {
        List<String> formats = Arrays.asList("yyyy-MM-dd", "yyyyMMdd");
        for (String format : formats) {
            try {
                SimpleDateFormat formatter = new SimpleDateFormat(format);
                return formatter.parse(dateStr);
            } catch (Exception e) {
                log.debug("Failed to parse date with format: {}. Error: {}", format, e.getMessage());
            }
        }
        return null;
    }

    public static <T> Set<T> convertArrayToSet(T[] array) {
        return new HashSet<>(Arrays.asList(array));
    }

    public static <T extends Enum<T>> T enumValueByName(Class<T> enumType, String name) {
        for (T constant : enumType.getEnumConstants()) {
            if (constant.name().equals(name)) {
                return constant;
            }
        }
        return null;
    }

    public static String removeSubstringsForUpperCase(String input, String... substrings) {
        if (input == null) return "";
        String result = input.toUpperCase();
        for (String substring : substrings) {
            result = result.replace(substring, "");
        }
        return result;
    }

    public static String removeSubstringsForLowerCase(String input, String... substrings) {
        if (input == null) return "";
        String result = input.toLowerCase();
        for (String substring : substrings) {
            result = result.replace(substring, "");
        }
        return result;
    }

    public static void flattenJson(String prefix, Map<String, Object> current, Map<String, Object> result) {
        current.forEach((key, value) -> {
            String newKey = prefix.isEmpty() ? key : prefix + "." + key;
            result.put(newKey, value);
            if (value instanceof Map) {
                flattenJson(newKey, (Map<String, Object>) value, result);
            } else if (value instanceof List) {
                flattenJsonArray(newKey, (List<Map<String, Object>>) value, result);
            }
        });
    }

    private static void flattenJsonArray(String prefix, List<Map<String, Object>> array, Map<String, Object> result) {
        for (int index = 0; index < array.size(); index++) {
            Object value = array.get(index);
            String newKey = prefix + "." + index;
            result.put(newKey, value);
            if (value instanceof Map) {
                flattenJson(newKey, (Map<String, Object>) value, result);
            } else if (value instanceof List) {
                flattenJsonArray(newKey, (List<Map<String, Object>>) value, result);
            }
        }
    }

    public static void appendErrorMessage(StringBuilder errorMessage, String mess) {
        if (errorMessage.length() == 0) {
            errorMessage.append(mess);
        }
    }

    public static long fetchShanghaiZoneTimeStampNow() {
        Instant now = Instant.now();
        return now.atZone(ZoneId.of("Asia/Shanghai"))
                .toLocalDateTime()
                .toInstant(ZoneOffset.UTC)
                .toEpochMilli();
    }

    public static ObjectNode removeLoopKeyFromJsonObject(ObjectNode jsonObject, String removeKey) {
        ObjectNode modifiedNode = objectMapper.createObjectNode();
        jsonObject.fields().forEachRemaining(entry -> {
            if (!entry.getKey().equalsIgnoreCase(removeKey)) {
                modifiedNode.set(entry.getKey(), entry.getValue());
            }
        });
        return modifiedNode;
    }

    public static JsonNode changeJsonElementKeyToLowerCase(JsonNode jsonElement) {
        if (jsonElement.isObject()) {
            ObjectNode newNode = objectMapper.createObjectNode();
            jsonElement.fields().forEachRemaining(entry -> {
                String finalKey = entry.getKey().toLowerCase();
                newNode.set(finalKey, changeJsonElementKeyToLowerCase(entry.getValue()));
            });
            return newNode;
        } else if (jsonElement.isArray()) {
            ArrayNode newArray = objectMapper.createArrayNode();
            jsonElement.forEach(value -> newArray.add(changeJsonElementKeyToLowerCase(value)));
            return newArray;
        }
        return jsonElement;
    }

    public static ObjectNode mapToJsonObject(Map<?, ?> map) {
        ObjectNode objectNode = objectMapper.createObjectNode();
        map.forEach((key, value) -> objectNode.set(key.toString(), convertToJsonNode(value)));
        return objectNode;
    }

    public static ArrayNode listToJsonArray(List<?> list) {
        ArrayNode arrayNode = objectMapper.createArrayNode();
        list.forEach(item -> arrayNode.add(convertToJsonNode(item)));
        return arrayNode;
    }

    public static JsonNode convertToJsonNode(Object value) {
        if (value == null) return NullNode.getInstance();

        if (value instanceof JsonNode) {
            return (JsonNode) value;
        }
        if (value instanceof String) {
            try {
                return objectMapper.readTree((String) value);
            } catch (JsonProcessingException e) {
                return new TextNode((String) value);
            }
        }
        if (value instanceof Number) {
            if (value instanceof Integer) {
                return new IntNode((Integer) value);
            } else if (value instanceof Long) {
                return new LongNode((Long) value);
            } else if (value instanceof Double) {
                return new DoubleNode((Double) value);
            } else if (value instanceof Float) {
                return new FloatNode((Float) value);
            }
        }
        if (value instanceof Boolean) {
            return BooleanNode.valueOf((Boolean) value);
        }
        if (value instanceof List) {
            return listToJsonArray((List<?>) value);
        }
        if (value instanceof Map) {
            return mapToJsonObject((Map<?, ?>) value);
        }

        try {
            return objectMapper.readTree(value.toString());
        } catch (JsonProcessingException e) {
            return new TextNode(value.toString());
        }
    }

    public static JsonNode processJsonElementForSingleQuotation(JsonNode element) {
        if (element.isObject()) {
            ObjectNode newNode = objectMapper.createObjectNode();
            element.fields().forEachRemaining(entry -> {
                String newKey = entry.getKey().replace("'", "''");
                newNode.set(newKey, processJsonElementForSingleQuotation(entry.getValue()));
            });
            return newNode;
        } else if (element.isArray()) {
            ArrayNode newArray = objectMapper.createArrayNode();
            element.forEach(item -> newArray.add(processJsonElementForSingleQuotation(item)));
            return newArray;
        } else if (element.isTextual()) {
            return new TextNode(element.asText().replace("'", "''"));
        }
        return element;
    }

    public static Map.Entry<DataType, JsonNode> fetchDataType(Object dataValue) {
        if (dataValue == null) {
            return new AbstractMap.SimpleEntry<>(DataType.JsonNull, NullNode.getInstance());
        }

        JsonNode valueObject;
        try {
            if (dataValue instanceof JsonNode) {
                valueObject = (JsonNode) dataValue;
            } else {
                valueObject = objectMapper.readTree(dataValue.toString());
            }
        } catch (JsonProcessingException e) {
            valueObject = new TextNode(dataValue.toString());
        }

        DataType dataType;
        if (valueObject.isObject()) {
            dataType = DataType.JsonObject;
        } else if (valueObject.isArray()) {
            dataType = DataType.JsonArray;
        } else if (valueObject.isNull()) {
            dataType = DataType.JsonNull;
        } else {
            dataType = DataType.JsonPrimitive;
        }

        return new AbstractMap.SimpleEntry<>(dataType, valueObject);
    }
}
