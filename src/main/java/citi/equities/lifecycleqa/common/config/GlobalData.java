package citi.equities.lifecycleqa.common.config;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Used for step debug with dynamic update and select Test Data
 */
public class GlobalData {
    private static final ObjectMapper objectMapper = new ObjectMapper();
    private static final ThreadLocal<Map<String, ObjectNode>> threadLocalTestData =
            ThreadLocal.withInitial(ConcurrentHashMap::new);

    public static ObjectNode getTestData(String runId) {
        ObjectNode data = threadLocalTestData.get().get(runId);
        return data != null ? data : objectMapper.createObjectNode();
    }

    public static void setTestData(String runId, ObjectNode testData) {
        threadLocalTestData.get().put(runId, testData);
    }

    public static void addKeyInTestData(String runId, String storedKey, JsonNode testDataValue) {
        ObjectNode testData = getTestData(runId);
        ObjectNode newTestData = objectMapper.createObjectNode();
        testData.fields().forEachRemaining(entry ->
                newTestData.set(entry.getKey(), entry.getValue()));
        newTestData.set(storedKey, testDataValue);
        setTestData(runId, newTestData);
    }

    public static void removeTestData(String runId) {
        threadLocalTestData.get().remove(runId);
    }
}
