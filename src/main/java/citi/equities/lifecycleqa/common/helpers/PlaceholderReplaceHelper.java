package citi.equities.lifecycleqa.common.helpers;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.*;
import citi.equities.lifecycleqa.common.config.GlobalData;
import citi.equities.lifecycleqa.common.config.InitialConfig;
import citi.equities.lifecycleqa.common.entities.AutomationStepBasicInfo;
import citi.equities.lifecycleqa.common.utils.DBUtil;
import citi.equities.lifecycleqa.common.utils.DataTypeUtil;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class PlaceholderReplaceHelper {
    private static final Logger log = LoggerFactory.getLogger(PlaceholderReplaceHelper.class);
    private static final ObjectMapper objectMapper = new ObjectMapper();
    private static final Pattern PLACEHOLDER_PATTERN = Pattern.compile("\\{\\{([^\\{\\}]*?)\\}\\}");

    public static List<String> findAllPlaceHolderKeyListInJsonElement(JsonNode jsonElement, List<String> ignoreOriginalList) {
        List<String> needList = new ArrayList<>();
        List<String> ignoreList = new ArrayList<>(ignoreOriginalList);

        traverseForPlaceholders(jsonElement, needList, ignoreList);

        Set<String> uniqueNeeds = new LinkedHashSet<>(needList);
        Set<String> uniqueIgnores = new HashSet<>(ignoreList);
        uniqueNeeds.removeAll(uniqueIgnores);

        return new ArrayList<>(uniqueNeeds);
    }

    private static void traverseForPlaceholders(JsonNode element, List<String> needList, List<String> ignoreList) {
        if (element.isObject()) {
            element.fields().forEachRemaining(entry -> {
                if ("storedKey".equals(entry.getKey()) && entry.getValue().isTextual()) {
                    ignoreList.add(entry.getValue().asText());
                } else {
                    traverseForPlaceholders(entry.getValue(), needList, ignoreList);
                }
            });
        } else if (element.isArray()) {
            for (JsonNode item : element) {
                traverseForPlaceholders(item, needList, ignoreList);
            }
        } else if (element.isTextual()) {
            String valueString = element.asText();
            Matcher matcher = PLACEHOLDER_PATTERN.matcher(valueString);
            while (matcher.find()) {
                String foundKey = matcher.group(1);
                if (!foundKey.startsWith("response")) {
                    needList.add(foundKey);
                }
            }
        }
    }

    public static JsonNode replaceDataObjectForTestDataAndAutoSystemVariables(
            AutomationStepBasicInfo baseInfo, JsonNode jsonElement, StringBuilder errorMessage) {
        return replaceDataObjectForTestDataAndAutoSystemVariables(
                baseInfo, jsonElement, errorMessage, 1,
                objectMapper.createObjectNode(), objectMapper.createObjectNode());
    }

    public static JsonNode replaceDataObjectForTestDataAndAutoSystemVariables(
            AutomationStepBasicInfo baseInfo, JsonNode jsonElement, StringBuilder errorMessage,
            int index, ObjectNode testData, ObjectNode variable) {

        final int currentIndex = index;

        if (jsonElement.isObject()) {
            ObjectNode newNode = objectMapper.createObjectNode();
            jsonElement.fields().forEachRemaining(entry -> {
                String key = entry.getKey();
                JsonNode value = entry.getValue();

                if ("sql".equals(key) && value.isTextual()) {
                    JsonNode replaced = replaceDataValueFromAutoTestDataAndAutoSystemVariables(
                            baseInfo, value.asText(), errorMessage, testData, variable, true);
                    newNode.set("sql", replaced);
                } else {
                    JsonNode finalKeyNode = replaceDataValueFromAutoTestDataAndAutoSystemVariables(
                            baseInfo, key, errorMessage, testData, variable, false);
                    String finalKey = finalKeyNode.isTextual() ? finalKeyNode.asText() : key;
                    JsonNode newValue = replaceDataObjectForTestDataAndAutoSystemVariables(
                            baseInfo, value, errorMessage, currentIndex, testData, variable);
                    newNode.set(finalKey, newValue);
                }
            });
            return newNode;
        } else if (jsonElement.isArray()) {
            ArrayNode newArray = objectMapper.createArrayNode();
            for (JsonNode value : jsonElement) {
                newArray.add(replaceDataObjectForTestDataAndAutoSystemVariables(
                        baseInfo, value, errorMessage, currentIndex, testData, variable));
            }
            return newArray;
        } else if (jsonElement.isTextual()) {
            String content = jsonElement.asText();
            if (currentIndex > 3 || !PLACEHOLDER_PATTERN.matcher(content).find()) {
                return jsonElement;
            }
            int nextIndex = currentIndex + 1;
            JsonNode replaceElement = replaceDataValueFromAutoTestDataAndAutoSystemVariables(
                    baseInfo, content, errorMessage, testData, variable, false);
            return replaceDataObjectForTestDataAndAutoSystemVariables(
                    baseInfo, replaceElement, errorMessage, nextIndex, testData, variable);
        }

        return jsonElement;
    }

    public static JsonNode replaceDataValueFromAutoTestDataAndAutoSystemVariables(
            AutomationStepBasicInfo baseInfo, String str, StringBuilder errorMessage) {
        return replaceDataValueFromAutoTestDataAndAutoSystemVariables(
                baseInfo, str, errorMessage, objectMapper.createObjectNode(),
                objectMapper.createObjectNode(), false);
    }

    public static JsonNode replaceDataValueFromAutoTestDataAndAutoSystemVariables(
            AutomationStepBasicInfo baseInfo, String str, StringBuilder errorMessage,
            ObjectNode testData, ObjectNode variable, boolean isInSQL) {

        // Type 1: {{key}}
        if (str.startsWith("{{") && str.endsWith("}}")) {
            String inner = str.substring(2, str.length() - 2);
            if (!inner.contains("{{")) {
                return fetchFinalDataByVariableSource(baseInfo, inner, errorMessage, testData, variable, isInSQL);
            }
        }

        // Type 2: {{key1}}[[key2]]{{key3}}
        StringBuffer result = new StringBuffer();
        Matcher matcher = PLACEHOLDER_PATTERN.matcher(str);
        while (matcher.find()) {
            String group = matcher.group(1);
            JsonNode jsonElement = fetchFinalDataByVariableSource(
                    baseInfo, group, errorMessage, testData, variable, isInSQL);
            String replacement;
            if (jsonElement.isObject() || jsonElement.isArray()) {
                replacement = jsonElement.toString();
            } else if (jsonElement.isTextual()) {
                replacement = jsonElement.asText();
            } else {
                replacement = jsonElement.toString();
            }
            matcher.appendReplacement(result, Matcher.quoteReplacement(replacement));
        }
        matcher.appendTail(result);

        return new TextNode(result.toString());
    }

    private static JsonNode fetchFinalDataByVariableSource(
            AutomationStepBasicInfo baseInfo, String group, StringBuilder errorMessage,
            ObjectNode testData, ObjectNode variable, boolean isInSQL) {

        boolean isStepDebug = baseInfo.isStepDebug();
        String sourceType = "Data";

        if (variable.has(group)) {
            sourceType = variable.get(group).asText("Data");
        } else if (baseInfo.getVariables() != null && baseInfo.getVariables().has(group)) {
            sourceType = baseInfo.getVariables().get(group).asText("Data");
        }

        JsonNode finalElement;

        if (isStepDebug && "Data".equals(sourceType)) {
            JsonNode fetchedElement = fetchElementFromJsonNode(testData, group);
            if (!fetchedElement.isNull()) {
                finalElement = fetchedElement;
            } else {
                ObjectNode testDataFromGlobal = GlobalData.getTestData(baseInfo.getRunId());
                finalElement = fetchElementFromJsonNode(testDataFromGlobal, group);
            }
        } else if ("Data".equals(sourceType)) {
            JsonNode fetchedElement = fetchElementFromJsonNode(testData, group);
            if (!fetchedElement.isNull()) {
                finalElement = fetchedElement;
            } else {
                String[] parts = group.split("\\.");
                StringBuilder selectKey = new StringBuilder();
                for (int i = 0; i < parts.length; i++) {
                    if (i > 0) selectKey.append(",");
                    selectKey.append("'").append(parts[i]).append("'::text");
                }
                String sql = "SELECT COALESCE(jsonb_extract_path(\"testData\", " + selectKey +
                        "),null) FROM auto_case_audit WHERE \"runId\" = '" + baseInfo.getRunId() +
                        "' AND \"caseId\" = " + baseInfo.getCaseId() + " LIMIT 1";
                errorMessage.setLength(0);
                finalElement = DBUtil.executeLIF(sql, true, errorMessage);
            }
        } else {
            String runId = baseInfo.getRunId();
            int caseId = baseInfo.getCaseId();
            String sql;

            switch (sourceType.toLowerCase()) {
                case "global":
                    sql = "select distinct COALESCE(value,'') from auto_system_variable where config_key like '%" + group + "%' limit 1";
                    break;
                case "scenario":
                    sql = "select \"" + group + "\" from auto_case_audit where \"runId\" = '" + runId + "' and \"caseId\" = " + caseId + " limit 1";
                    break;
                case "xml":
                    sql = "select COALESCE(value,'') from auto_case_xmlfile where name = '" + group + "' limit 1";
                    break;
                case "config":
                    Object configValue = InitialConfig.INFO_CONFIG.get(group);
                    return new TextNode(configValue != null ? configValue.toString() : "");
                default:
                    DataTypeUtil.appendErrorMessage(errorMessage, "\nError: Unknown sourceType: " + sourceType);
                    return new TextNode("");
            }

            String fullSql = sql;
            if ("global".equalsIgnoreCase(sourceType) || "component".equalsIgnoreCase(sourceType)) {
                fullSql = """
                    WITH cte AS (
                        SELECT COALESCE(value, '') AS value, profile
                        FROM auto_system_variable
                        WHERE config_key LIKE '%automation.variables%""" + group + """
                        %'
                        AND component IN (
                            SELECT config->>'component'
                            FROM auto_case_audit
                            WHERE "runId" = '""" + runId + "' AND \"caseId\" = " + caseId + """
                        )
                    )
                    SELECT value FROM cte WHERE upper(profile) = upper('""" + baseInfo.getRegion() + baseInfo.getEnv() + """
                    ')
                    UNION ALL
                    SELECT value FROM cte WHERE upper(profile) = 'GLOBAL'
                    LIMIT 1
                    """;
            }

            errorMessage.setLength(0);
            finalElement = DBUtil.executeLIF(fullSql, true, errorMessage);
        }

        if (isInSQL) {
            return DataTypeUtil.processJsonElementForSingleQuotation(finalElement);
        }
        return finalElement;
    }

    private static JsonNode fetchElementFromJsonNode(JsonNode jsonNode, String path) {
        String[] keys = path.split("\\.");
        JsonNode currentElement = jsonNode;

        for (String key : keys) {
            if (currentElement.isObject()) {
                currentElement = currentElement.get(key);
                if (currentElement == null) {
                    return NullNode.getInstance();
                }
            } else if (currentElement.isArray()) {
                try {
                    int index = Integer.parseInt(key);
                    currentElement = currentElement.get(index);
                    if (currentElement == null) {
                        return NullNode.getInstance();
                    }
                } catch (NumberFormatException e) {
                    return NullNode.getInstance();
                }
            } else {
                return NullNode.getInstance();
            }
        }
        return currentElement;
    }
}
