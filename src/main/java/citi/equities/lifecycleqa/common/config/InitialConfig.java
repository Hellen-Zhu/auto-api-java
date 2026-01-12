package citi.equities.lifecycleqa.common.config;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import citi.equities.lifecycleqa.common.enums.DBConnectKey;
import citi.equities.lifecycleqa.common.enums.EHEureka;
import citi.equities.lifecycleqa.common.utils.DBUtil;
import citi.equities.lifecycleqa.common.utils.RSAUtil;
import io.restassured.RestAssured;
import io.restassured.response.Response;
import org.apache.ibatis.io.Resources;
import org.apache.ibatis.session.SqlSessionFactoryBuilder;
import org.apache.ibatis.session.SqlSessionManager;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.BufferedReader;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.concurrent.*;

public class InitialConfig {
    private static final Logger log = LoggerFactory.getLogger(InitialConfig.class);
    private static final ObjectMapper objectMapper = new ObjectMapper();

    public static Map<String, Object> INFO_CONFIG = new ConcurrentHashMap<>();
    public static Map<String, SqlSessionManager> DB_CONFIG = new ConcurrentHashMap<>();
    private static volatile boolean ifInitial = false;

    public static synchronized void initEHConfigurationAndOthers() {
        if (!ifInitial) {
            log.info("Start to init EHConfiguration and Others");
            fetchEHConfigurationMap();
            fetchComponentDependentsConfigMap();
            fetchOtherConfig();
            fetchComponentServiceNameRelated();
            ifInitial = true;
        } else {
            log.info("EHConfiguration and Others has been Initialized");
        }
    }

    public static SqlSessionManager getLIFSqlSessionManager() {
        return DB_CONFIG.get("lif");
    }

    @SuppressWarnings("unchecked")
    public static List<String> getComponentDependentDatabases(String component, String profile) {
        String serviceKey = (component + "-" + profile + "-databases").toLowerCase();
        Object value = INFO_CONFIG.get(serviceKey);
        return value != null ? (List<String>) value : Collections.emptyList();
    }

    @SuppressWarnings("unchecked")
    public static List<String> getComponentDependentServices(String component, String profile) {
        String serviceKey = (component + "-" + profile + "-services").toLowerCase();
        Object value = INFO_CONFIG.get(serviceKey);
        return value != null ? (List<String>) value : Collections.emptyList();
    }

    @SuppressWarnings("unchecked")
    public static List<String> getComponentDependentLabels(String component, String profile) {
        String serviceKey = (component + "-" + profile + "-labels").toLowerCase();
        Object value = INFO_CONFIG.get(serviceKey);
        return value != null ? (List<String>) value : Collections.emptyList();
    }

    public static String getComponentServicesName(String component) {
        String serviceKey = (component + "-servicename").toLowerCase();
        Object value = INFO_CONFIG.get(serviceKey);
        return value != null ? (String) value : "";
    }

    public static String fetchBaseUrl(String serviceName, String profile) {
        String sql = "select distinct base_url from auto_baseurl where profile is not null and lower(profile) = lower('" +
                profile + "') and lower(service_name) = lower('" + serviceName + "') limit 1";
        StringBuilder errorMessage = new StringBuilder();
        JsonNode result = DBUtil.executeLIF(sql, true, errorMessage);
        return result != null && result.isTextual() ? result.asText() : "";
    }

    private static void fetchComponentServiceNameRelated() {
        String sql = "select distinct component, \"serviceName\" from auto_api_configuration where \"serviceName\" is not null and \"serviceName\" != ''";
        StringBuilder errorMessage = new StringBuilder();
        JsonNode result = DBUtil.executeLIF(sql, true, errorMessage);
        if (result != null && result.isArray()) {
            for (JsonNode node : result) {
                String component = node.has("component") ? node.get("component").asText("").toLowerCase() : "";
                String serviceName = node.has("serviceName") ? node.get("serviceName").asText("") : "";
                INFO_CONFIG.put(component + "-servicename", serviceName);
            }
        }
    }

    private static void fetchComponentDependentsConfigMap() {
        String sql = """
            select distinct TRIM(a.component) as component, TRIM(a.profile) as profile, TRIM(a.config_key) as config_key, a.value as value from (
                (select component, profile, replace(config_key,'automation.dependen.','') as config_key, value from auto_system_variable
                where config_key = 'automation.dependen.labels' and (component_like = '' or component_like is null))
                union
                (select component_like as component, profile, replace(config_key,'automation.dependen.','') as config_key, value from auto_system_variable
                where config_key = 'automation.dependen.labels' and component_like is not null)
                union
                (select component, profile, replace(config_key,'automation.dependen.','') as config_key, value from auto_system_variable where config_key
                in('automation.dependen.services','automation.dependen.databases') and (component_like = '' or component_like is null))
                union
                (select component, profile, replace(config_key,'automation.variables.','') as config_key, value from auto_system_variable
                where config_key like 'automation.variables.%' and (component_like = '' or component_like is null))
            ) a
            """;

        StringBuilder errorMessage = new StringBuilder();
        JsonNode result = DBUtil.executeLIF(sql, true, errorMessage);
        if (result != null && result.isArray()) {
            for (JsonNode node : result) {
                String component = node.has("component") ? node.get("component").asText("") : "";
                String profile = node.has("profile") ? node.get("profile").asText("") : "";
                String configKey = node.has("config_key") ? node.get("config_key").asText("") : "";
                String value = node.has("value") ? node.get("value").asText("") : "";

                String key = (component + "-" + profile + "-" + configKey).toLowerCase();
                List<String> values = Arrays.asList(value.split(","));
                INFO_CONFIG.put(key, values);
            }
        }
    }

    private static void fetchEHConfigurationMap() {
        List<String> urls = new ArrayList<>();
        for (EHEureka eureka : EHEureka.values()) {
            urls.add(eureka.getUrl());
        }

        String urlSet = fetchEurekaAndConfigSVCUrl(urls);
        if (urlSet == null) {
            log.error("Failed to fetch Eureka and Config SVC URL");
            return;
        }

        String[] parts = urlSet.split("##");
        String eurekaUrl = parts[0];
        String configSVCUrl = parts[1];
        log.info("Success to connect Eureka URL: {}, Config SVC URL: {}", eurekaUrl, configSVCUrl);

        String activeProfile = null;
        for (EHEureka eureka : EHEureka.values()) {
            if (eurekaUrl.contains(eureka.getUrl())) {
                activeProfile = eureka.getProfile();
                break;
            }
        }

        if (activeProfile == null) {
            log.error("Failed to find active profile");
            return;
        }

        Map<String, String> resultMap = buildEHConfiguration(configSVCUrl, activeProfile);

        for (DBConnectKey key : DBConnectKey.values()) {
            Map<String, String> dbConnectMap = buildDBConnectMap(resultMap, key.getDbName(), key.getProfile().toLowerCase());
            INFO_CONFIG.put(key.getConnectName(), dbConnectMap);
            DB_CONFIG.put(key.getConnectName(), getSqlSessionManager(dbConnectMap));
        }
    }

    private static String fetchEurekaAndConfigSVCUrl(List<String> urls) {
        ExecutorService executor = Executors.newFixedThreadPool(10);
        CompletableFuture<String> resultFuture = new CompletableFuture<>();

        for (String url : urls) {
            executor.submit(() -> {
                try {
                    if (resultFuture.isDone()) return;

                    Response response = RestAssured.given().get(url + "/eureka/apps");
                    if (response.getStatusCode() == 200) {
                        String homePageUrl = findHomePageUrl(response.asInputStream());
                        if (!homePageUrl.isEmpty()) {
                            resultFuture.complete(url + "##" + homePageUrl);
                        }
                    }
                } catch (Exception e) {
                    log.debug("Failed to connect to URL: {}", url);
                }
            });
        }

        try {
            return resultFuture.get(30, TimeUnit.SECONDS);
        } catch (Exception e) {
            log.error("Failed to fetch Eureka URL: {}", e.getMessage());
            return null;
        } finally {
            executor.shutdown();
        }
    }

    private static String findHomePageUrl(InputStream inputStream) {
        try (BufferedReader reader = new BufferedReader(new InputStreamReader(inputStream, StandardCharsets.UTF_8))) {
            String line;
            boolean foundName = false;
            while ((line = reader.readLine()) != null) {
                if (line.contains("<name>EH-MICROSERVICE-CONFIG</name>")) {
                    foundName = true;
                } else if (foundName && line.contains("<homePageUrl>")) {
                    int start = line.indexOf("<homePageUrl>") + "<homePageUrl>".length();
                    int end = line.indexOf("</homePageUrl>");
                    return line.substring(start, end);
                }
            }
        } catch (Exception e) {
            log.error("Error reading input stream: {}", e.getMessage());
        }
        return "";
    }

    private static Map<String, String> buildDBConnectMap(Map<String, String> dbPropertyMap, String dbName, String profile) {
        Map<String, String> result = new HashMap<>();
        result.put("driver", dbPropertyMap.getOrDefault("ehapi." + dbName + "." + profile + ".driver", ""));
        result.put("url", dbPropertyMap.getOrDefault("ehapi." + dbName + "." + profile + ".url", ""));
        result.put("username", dbPropertyMap.getOrDefault("ehapi." + dbName + "." + profile + ".username", ""));
        result.put("password", RSAUtil.decrypt(dbPropertyMap.getOrDefault("ehapi." + dbName + "." + profile + ".password", "")));
        return result;
    }

    private static SqlSessionManager getSqlSessionManager(Map<String, String> dbPropertyMap) {
        try {
            Properties properties = new Properties();
            properties.setProperty("driver", dbPropertyMap.get("driver"));
            properties.setProperty("url", dbPropertyMap.get("url"));
            properties.setProperty("username", dbPropertyMap.get("username"));
            properties.setProperty("password", dbPropertyMap.get("password"));

            InputStream inputStream = Resources.getResourceAsStream("mybatis.xml");
            return SqlSessionManager.newInstance(
                    new SqlSessionFactoryBuilder().build(inputStream, properties)
            );
        } catch (Exception e) {
            log.error("Failed to create SqlSessionManager: {}", e.getMessage());
            return null;
        }
    }

    private static Map<String, String> buildEHConfiguration(String configSVCUrl, String profile) {
        String finalProfile = profile.toLowerCase().replace("dev", "dailyrefresh");
        try {
            Response response = RestAssured.given()
                    .get(configSVCUrl + "/manage-config/getConfigurations/mondo-testng-api-service/" + finalProfile);

            Map<String, String> result = new HashMap<>();
            JsonNode jsonArray = objectMapper.readTree(response.asString());
            if (jsonArray.isArray()) {
                for (JsonNode node : jsonArray) {
                    String key = node.has("pk") && node.get("pk").has("configKey")
                            ? node.get("pk").get("configKey").asText("") : "";
                    String value = node.has("value") ? node.get("value").asText("") : "";
                    result.put(key, value);
                }
            }
            return result;
        } catch (Exception e) {
            log.error("Failed to build EH Configuration: {}", e.getMessage());
            return new HashMap<>();
        }
    }

    private static void fetchOtherConfig() {
        String sql = "select config_key, value from auto_system_variable where component = '-' or component is null";
        StringBuilder errorMessage = new StringBuilder();
        JsonNode result = DBUtil.executeLIF(sql, true, errorMessage);
        if (result != null && result.isArray()) {
            for (JsonNode config : result) {
                String key = config.has("config_key") ? config.get("config_key").asText("") : "";
                String value = config.has("value") ? config.get("value").asText("") : "";
                if (!key.isEmpty()) {
                    INFO_CONFIG.put(key, value);
                }
            }
        }
    }
}
