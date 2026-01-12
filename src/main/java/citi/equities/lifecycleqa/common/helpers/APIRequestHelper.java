package citi.equities.lifecycleqa.common.helpers;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import citi.equities.lifecycleqa.common.config.InitialConfig;
import citi.equities.lifecycleqa.common.entities.AutomationStepBasicInfo;
import citi.equities.lifecycleqa.common.entities.StopCondition;
import citi.equities.lifecycleqa.common.utils.DataTypeUtil;
import io.github.resilience4j.retry.Retry;
import io.github.resilience4j.retry.RetryConfig;
import io.restassured.RestAssured;
import io.restassured.builder.ResponseBuilder;
import io.restassured.config.HttpClientConfig;
import io.restassured.config.RestAssuredConfig;
import io.restassured.http.ContentType;
import io.restassured.response.Response;
import io.restassured.specification.RequestSpecification;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.time.Duration;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.TimeoutException;

public class APIRequestHelper {
    private static final Logger log = LoggerFactory.getLogger(APIRequestHelper.class);
    private static final ObjectMapper objectMapper = new ObjectMapper();

    public static void handleAPIRequest(AutomationStepBasicInfo baseInfo, StringBuilder output, StringBuilder errorMessage) {
        JsonNode stepObject = baseInfo.getStepObject();
        JsonNode testObject = stepObject.has("test") ? stepObject.get("test") : objectMapper.createObjectNode();

        JsonNode testObjectFinal = PlaceholderReplaceHelper.replaceDataObjectForTestDataAndAutoSystemVariables(
                baseInfo, testObject, errorMessage);

        String path = testObjectFinal.has("path") ? testObjectFinal.get("path").asText("") : "";
        String method = testObjectFinal.has("method") ? testObjectFinal.get("method").asText("") : "";
        String serviceName = testObjectFinal.has("serviceName") ? testObjectFinal.get("serviceName").asText("") : "";
        String baseUrl = InitialConfig.fetchBaseUrl(serviceName, baseInfo.getRegion() + baseInfo.getEnv());

        if (baseUrl == null || baseUrl.isBlank()) {
            errorMessage.setLength(0);
            DataTypeUtil.appendErrorMessage(errorMessage, "\nError: baseUrl is null");
            log.error("Error: baseUrl is null");
            if (errorMessage.length() > 0) {
                return;
            }
        }

        JsonNode requestData;
        try {
            requestData = testObjectFinal.has("request") ? testObjectFinal.get("request") : objectMapper.createObjectNode();
        } catch (Exception e) {
            requestData = objectMapper.createObjectNode();
        }

        String startMessage = "------------------------------------------------------------------\n" +
                "API Trigger for Step: '" + baseInfo.getStepName() + "'\n" +
                "BaseUrl: " + baseUrl + "\n" +
                "Path: " + path + "\n" +
                "Method: " + method + "\n" +
                "Request: " + requestData + "\n";
        log.info(startMessage);
        output.append(startMessage).append("\n");

        int requestTimeout = Integer.parseInt(InitialConfig.INFO_CONFIG.getOrDefault("request.timeout.min", "5").toString());
        RestAssuredConfig config = RestAssuredConfig.config()
                .httpClient(HttpClientConfig.httpClientConfig()
                        .setParam("http.socket.timeout", requestTimeout * 60 * 1000)
                        .setParam("http.connection.timeout", requestTimeout * 60 * 1000));

        RequestSpecification finalRequest = RestAssured.given().config(config).baseUri(baseUrl);
        if (baseUrl != null && baseUrl.startsWith("https://")) {
            finalRequest = finalRequest.relaxedHTTPSValidation();
        }

        processAPIContent(finalRequest, requestData, method, errorMessage);

        if (errorMessage.length() > 0) {
            String message = "Failed to process API content, with error: " + errorMessage;
            log.error(message);
            errorMessage.setLength(0);
            errorMessage.append(message).append("\n");
            return;
        }

        Response response;
        try {
            JsonNode retryObject = testObjectFinal.has("retry") ? testObjectFinal.get("retry") : null;
            response = triggerAPIRequest(finalRequest, method, path, retryObject);
        } catch (Throwable exception) {
            response = processAPIRequestException(exception, requestTimeout);
        }

        saveResponse(baseInfo, response, output, errorMessage);
    }

    private static void processAPIContent(RequestSpecification finalRequest, JsonNode requestData, String method, StringBuilder errorMessage) {
        if (requestData.has("cookie") && requestData.get("cookie").isTextual()) {
            finalRequest.cookie("SMSESSION", requestData.get("cookie").asText());
        }

        if (requestData.has("headers") && requestData.get("headers").isObject()) {
            Map<String, String> headers = new HashMap<>();
            requestData.get("headers").fields().forEachRemaining(entry ->
                    headers.put(entry.getKey(), entry.getValue().asText("")));
            finalRequest.headers(headers);
        }

        if (requestData.has("pathParams") && requestData.get("pathParams").isObject()) {
            Map<String, String> pathParams = new HashMap<>();
            requestData.get("pathParams").fields().forEachRemaining(entry ->
                    pathParams.put(entry.getKey(), entry.getValue().asText("")));
            finalRequest.pathParams(pathParams);
        }

        if (requestData.has("body")) {
            JsonNode body = requestData.get("body");
            if (body.isObject() || body.isArray()) {
                finalRequest.contentType(ContentType.JSON).body(body.toString());
            } else {
                String bodyContent = body.asText("").trim();
                ContentType contentType;
                if (bodyContent.startsWith("<") && bodyContent.endsWith(">") && bodyContent.contains("</")) {
                    contentType = ContentType.XML;
                } else {
                    contentType = ContentType.TEXT;
                }
                finalRequest.contentType(contentType).body(bodyContent.replace("\\\"", "\""));
            }
        }

        if (requestData.has("params") && requestData.get("params").isObject()) {
            Map<String, String> params = new HashMap<>();
            requestData.get("params").fields().forEachRemaining(entry ->
                    params.put(entry.getKey(), entry.getValue().asText("")));

            if (!requestData.has("body") && method.equalsIgnoreCase("get")) {
                try {
                    finalRequest.params(params);
                } catch (Exception e) {
                    DataTypeUtil.appendErrorMessage(errorMessage, "Error sending request: " + e.getMessage());
                }
            } else {
                finalRequest.queryParams(params);
            }
        }
    }

    private static Response triggerAPIRequest(RequestSpecification finalRequest, String method, String path, JsonNode retryObject) {
        int attempt = 2;
        long interval = 0L;

        if (retryObject != null) {
            attempt = retryObject.has("attempt") ? retryObject.get("attempt").asInt(2) : 2;
            interval = retryObject.has("interval") ? retryObject.get("interval").asLong(0L) : 0L;
        }

        StopCondition stopCondition = parseStopCondition(retryObject);

        RetryConfig retryConfig = createRetryConfig(attempt, interval, stopCondition);
        Retry retry = Retry.of("apiRetry", retryConfig);
        final int[] attemptCounter = {1};
        final long finalInterval = interval;

        retry.getEventPublisher().onRetry(event -> {
            attemptCounter[0] = event.getNumberOfRetryAttempts() + 1;
        });

        return Retry.decorateSupplier(retry, () -> {
            log.info("Trigger API request with method: {}, path: {} with retry {} time, interval {} second",
                    method, path, attemptCounter[0], finalInterval);
            Response response;
            switch (method.toLowerCase()) {
                case "post":
                    response = finalRequest.when().post(path);
                    break;
                case "get":
                    response = finalRequest.when().get(path);
                    break;
                case "put":
                    response = finalRequest.when().put(path);
                    break;
                case "delete":
                    response = finalRequest.when().delete(path);
                    break;
                default:
                    response = null;
            }
            return response;
        }).get();
    }

    private static StopCondition parseStopCondition(JsonNode retryObject) {
        if (retryObject == null || !retryObject.has("stopCondition")) {
            return new StopCondition();
        }

        JsonNode sc = retryObject.get("stopCondition");
        StopCondition stopCondition = new StopCondition();

        if (sc.has("largerThanSize")) {
            stopCondition.setLargerThanSize(sc.get("largerThanSize").asInt());
        }
        if (sc.has("lessThanSize")) {
            stopCondition.setLessThanSize(sc.get("lessThanSize").asInt());
        }
        if (sc.has("contentEquals")) {
            stopCondition.setContentEquals(sc.get("contentEquals").asText());
        }
        if (sc.has("contentContains")) {
            stopCondition.setContentContains(sc.get("contentContains").asText());
        }
        if (sc.has("jsonNotContains") && sc.get("jsonNotContains").isObject()) {
            stopCondition.setJsonNotContains((ObjectNode) sc.get("jsonNotContains"));
        }
        if (sc.has("jsonContains") && sc.get("jsonContains").isObject()) {
            stopCondition.setJsonContains((ObjectNode) sc.get("jsonContains"));
        }
        if (sc.has("jsonContainsKey")) {
            stopCondition.setJsonContainsKey(sc.get("jsonContainsKey").asText());
        }

        return stopCondition;
    }

    private static Response processAPIRequestException(Throwable exception, int requestTimeout) {
        ResponseBuilder responseBuilder = new ResponseBuilder();

        if (exception instanceof TimeoutException) {
            responseBuilder.setStatusCode(408);
            responseBuilder.setHeader("Content-Type", "application/json");
            ObjectNode body = objectMapper.createObjectNode();
            body.put("error", "Request timed out which occur in eh.api with timeout = " + requestTimeout + " mins. Please try again later.");
            responseBuilder.setBody(body.toString());
        } else if (exception instanceof IllegalStateException) {
            if (exception.getMessage() != null && exception.getMessage().contains("Target host is null")) {
                responseBuilder.setStatusCode(404);
                responseBuilder.setHeader("Content-Type", "application/json");
                ObjectNode body = objectMapper.createObjectNode();
                body.put("error", "Target host not found. Please check the URL in LIF.auto_baseurl and try again.");
                responseBuilder.setBody(body.toString());
            } else {
                responseBuilder.setStatusCode(500);
                responseBuilder.setHeader("Content-Type", "application/json");
                ObjectNode body = objectMapper.createObjectNode();
                body.put("error", "An unexpected error occurred: " + exception.getMessage());
                responseBuilder.setBody(body.toString());
            }
        } else {
            responseBuilder.setStatusCode(500);
            responseBuilder.setHeader("Content-Type", "application/json");
            ObjectNode body = objectMapper.createObjectNode();
            body.put("error", "An unexpected error occurred: " + exception.getMessage());
            responseBuilder.setBody(body.toString());
        }

        return responseBuilder.build();
    }

    private static void saveResponse(AutomationStepBasicInfo baseInfo, Response response, StringBuilder output, StringBuilder errorMessage) {
        if (response == null) {
            DataTypeUtil.appendErrorMessage(errorMessage, "\nError: Response is null");
            return;
        }

        String responseHeaders = response.headers().toString();
        String responseString = cleanString(response.then().extract().asString());
        if (responseString.length() > 2147483600) {
            responseString = responseString.substring(0, 2147483600);
        }

        JsonNode responseInDB = DataTypeUtil.convertToJsonNode(responseString);

        ObjectNode responseHeadersInDB = objectMapper.createObjectNode();
        for (String line : responseHeaders.split("\n")) {
            String[] parts = line.split("=", 2);
            String key = parts[0];
            String value = parts.length > 1 ? parts[1] : "";
            responseHeadersInDB.put(key, value);
        }

        int statusCode = response.statusCode();

        output.append("* StatusCode: ").append(statusCode).append("\n");
        output.append("* Response: ").append(responseInDB).append("\n");

        try {
            ObjectNode responseData = objectMapper.createObjectNode();
            responseData.set("responseHeaders", responseHeadersInDB);
            responseData.set("responseBody", responseInDB);
            responseData.put("statusCode", statusCode);

            DBExecutionHelper.updateTestDataInAutoCaseAudit(
                    baseInfo,
                    "response" + baseInfo.getStepId(),
                    responseData,
                    errorMessage
            );
        } catch (Exception e) {
            log.error(e.getMessage());
        }
    }

    private static RetryConfig createRetryConfig(int attempt, long interval, StopCondition stopCondition) {
        return RetryConfig.<Response>custom()
                .maxAttempts(attempt)
                .waitDuration(Duration.ofSeconds(interval))
                .retryExceptions(IOException.class, TimeoutException.class)
                .retryOnResult(response -> !stopCondition.evaluateForAPI(response.asString()))
                .build();
    }

    private static String cleanString(String input) {
        return input.replaceAll("[^\\x20-\\x7E]", "");
    }
}
