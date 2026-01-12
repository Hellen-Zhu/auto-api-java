package citi.equities.lifecycleqa.common.enums;

import java.util.Arrays;
import java.util.List;

public enum DataType {
    JsonObject("JsonObject", "JSON Object"),
    JsonArray("JsonArray", "JSON Array"),
    JsonPrimitive("JsonPrimitive", "JSON Primitive"),
    JsonNull("JsonNull", "JSON Null"),
    Unknown("Unknown", "Unknown Type");

    private final String type;
    private final String description;

    DataType(String type, String description) {
        this.type = type;
        this.description = description;
    }

    public String getType() {
        return type;
    }

    public String getDescription() {
        return description;
    }

    public static DataType fromType(String type) {
        for (DataType dt : DataType.values()) {
            if (dt.type.equalsIgnoreCase(type)) {
                return dt;
            }
        }
        return Unknown;
    }

    public static boolean isNumeric(DataType type) {
        return type == JsonPrimitive;
    }

    public static boolean isTextual(DataType type) {
        List<DataType> textualTypes = Arrays.asList(JsonObject, JsonArray, JsonPrimitive);
        return textualTypes.contains(type);
    }
}
