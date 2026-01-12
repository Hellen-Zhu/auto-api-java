package citi.equities.lifecycleqa.common.entities;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;
import java.util.Map;

@Data
@AllArgsConstructor
@NoArgsConstructor
public class StopCondition {
    private static final Logger log = LoggerFactory.getLogger(StopCondition.class);
    private static final ObjectMapper objectMapper = new ObjectMapper();

    private Integer largerThanSize;
    private Integer lessThanSize;
    private Object contentEquals;
    private String contentContains;
    private ObjectNode jsonNotContains;
    private ObjectNode jsonContains;
    private String jsonContainsKey;

    public boolean evaluateForDB(List<Object> result) {
        boolean largerThanSizeValid = largerThanSize == null || result.size() > largerThanSize;
        boolean lessThanSizeValid = lessThanSize == null || result.size() < lessThanSize;

        boolean contentEqualsValid = true;
        if (contentEquals != null) {
            contentEqualsValid = result.stream().anyMatch(item -> {
                if (item instanceof Map) {
                    return ((Map<?, ?>) item).values().stream().anyMatch(v -> {
                        if (v instanceof Number && contentEquals instanceof Number) {
                            return Math.abs(((Number) v).doubleValue() - ((Number) contentEquals).doubleValue()) < 0.0001;
                        }
                        return v != null && v.toString().equalsIgnoreCase(contentEquals.toString());
                    });
                }
                return false;
            });
        }

        boolean contentContainsValid = true;
        if (contentContains != null) {
            contentContainsValid = result.stream().anyMatch(item -> {
                if (item instanceof Map) {
                    return ((Map<?, ?>) item).values().stream()
                            .anyMatch(v -> v != null && v.toString().toLowerCase().contains(contentContains.toLowerCase()));
                }
                return false;
            });
        }

        return largerThanSizeValid && lessThanSizeValid && contentEqualsValid && contentContainsValid;
    }

    public boolean evaluateForAPI(String responseBody) {
        if (responseBody == null) return false;

        boolean contentEqualValid = contentEquals == null || responseBody.equals(contentEquals.toString());
        boolean contentContainsValid = contentContains == null || responseBody.toLowerCase().contains(contentContains.toLowerCase());

        boolean largerThanSizeValid = true;
        if (largerThanSize != null) {
            try {
                double value = Double.parseDouble(responseBody);
                largerThanSizeValid = value > largerThanSize;
            } catch (NumberFormatException e) {
                largerThanSizeValid = false;
            }
        }

        boolean lessThanSizeValid = true;
        if (lessThanSize != null) {
            try {
                double value = Double.parseDouble(responseBody);
                lessThanSizeValid = value < lessThanSize;
            } catch (NumberFormatException e) {
                lessThanSizeValid = false;
            }
        }

        boolean jsonContainsValid = true;
        if (jsonContains != null) {
            try {
                JsonNode jsonNode = objectMapper.readTree(responseBody);
                if (jsonNode.isObject()) {
                    ObjectNode objectNode = (ObjectNode) jsonNode;
                    var fields = jsonContains.fields();
                    while (fields.hasNext()) {
                        var entry = fields.next();
                        String key = entry.getKey();
                        String expectedValue = entry.getValue().asText();
                        String actualValue = objectNode.has(key) ? objectNode.get(key).asText() : null;

                        if (actualValue == null) {
                            jsonContainsValid = false;
                            break;
                        }

                        if (expectedValue.startsWith("%") && expectedValue.endsWith("%")) {
                            String pattern = expectedValue.substring(1, expectedValue.length() - 1);
                            if (!actualValue.toLowerCase().contains(pattern.toLowerCase())) {
                                jsonContainsValid = false;
                                break;
                            }
                        } else if (!actualValue.equals(expectedValue)) {
                            jsonContainsValid = false;
                            break;
                        }
                    }
                }
            } catch (Exception e) {
                log.debug("Error parsing JSON for jsonContains validation: {}", e.getMessage());
            }
        }

        boolean jsonContainKeyValid = true;
        if (jsonContainsKey != null) {
            try {
                JsonNode jsonNode = objectMapper.readTree(responseBody);
                if (jsonNode.isObject()) {
                    jsonContainKeyValid = jsonNode.has(jsonContainsKey);
                }
            } catch (Exception e) {
                jsonContainKeyValid = false;
            }
        }

        return contentEqualValid && contentContainsValid && largerThanSizeValid &&
               lessThanSizeValid && jsonContainsValid && jsonContainKeyValid;
    }
}
