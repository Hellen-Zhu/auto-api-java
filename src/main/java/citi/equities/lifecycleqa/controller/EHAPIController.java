package citi.equities.lifecycleqa.controller;

import citi.equities.lifecycleqa.service.impl.APIServiceImpl;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.github.f4b6a3.ulid.UlidCreator;
import citi.equities.lifecycleqa.common.entities.SuiteParameter;
import citi.equities.lifecycleqa.common.enums.LIFDBStatement;
import citi.equities.lifecycleqa.common.enums.Profile;
import citi.equities.lifecycleqa.common.utils.DBUtil;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.web.bind.annotation.*;

import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.stream.Collectors;

@RestController
@RequestMapping("automation")
public class EHAPIController {
    private static final Logger log = LoggerFactory.getLogger(EHAPIController.class);
    private static final ObjectMapper objectMapper = new ObjectMapper();

    private final APIServiceImpl apiServiceImpl;

    public EHAPIController(APIServiceImpl apiServiceImpl) {
        this.apiServiceImpl = apiServiceImpl;
    }

    @PostMapping("/runByCaseId/{id}/{profile}")
    public ObjectNode runByCaseIdAndProfile(@PathVariable String id, @PathVariable String profile) {
        log.info("Start to automation run by id {}, profile {}", id, profile);

        if (!Profile.isValid(profile)) {
            ObjectNode error = objectMapper.createObjectNode();
            error.put("error", "Profile " + profile + " is invalid");
            return error;
        }

        log.info("TestNG XML Suite for ID: {}, Profile: {}", id, profile);
        StringBuilder errorMessage = new StringBuilder();

        List<SuiteParameter> list = apiServiceImpl.buildSuiteParameterListForIdRun(id, profile);
        apiServiceImpl.run(list);

        String runIds = list.stream()
                .map(SuiteParameter::getRunId)
                .collect(Collectors.joining(","));

        Map<String, String> params = new HashMap<>();
        params.put("caseId", id);
        params.put("runIds", runIds);

        JsonNode stepResult = DBUtil.executeIf(
                LIFDBStatement.FetchSingleAutoCaseAuditDetail.name(),
                false,
                errorMessage,
                params
        );

        if (errorMessage.length() > 0) {
            ObjectNode error = objectMapper.createObjectNode();
            error.put("error", errorMessage.toString());
            return error;
        }

        if (stepResult.isArray() && stepResult.size() > 0) {
            return (ObjectNode) stepResult.get(0);
        }

        return objectMapper.createObjectNode();
    }

    @PostMapping("/runByComponentAndProfile")
    public String runByComponentAndProfile(@RequestBody ObjectNode body) {
        Map<String, String> request = new HashMap<>();
        body.fields().forEachRemaining(entry -> {
            if (entry.getValue().isTextual()) {
                request.put(entry.getKey(), entry.getValue().asText());
            } else {
                request.put(entry.getKey(), entry.getValue().toString());
            }
        });

        log.info("Start to component run by {}", request);

        String runMode = request.get("runMode");
        String component = request.get("component");
        String profile = request.get("profile");

        if (isNullOrBlank(runMode) || isNullOrBlank(component) || isNullOrBlank(profile)) {
            return "Please check fields Component, Profile, and runMode";
        }

        // Run asynchronously
        CompletableFuture.runAsync(() -> {
            List<SuiteParameter> list = apiServiceImpl.buildSuiteParameterListForComponentRun(request);
            apiServiceImpl.run(list);
        });

        return "Running with Request " + request;
    }

    @PostMapping("/runByModule")
    public ObjectNode runByModule(@RequestBody ObjectNode request) {
        log.info("Start to module run by {}", request);
        StringBuilder errorMessage = new StringBuilder();

        if (!request.has("module") || request.get("module").isNull()) {
            ObjectNode error = objectMapper.createObjectNode();
            error.put("error", "field 'module' is required, please input valid value !");
            return error;
        }

        String groupId = UlidCreator.getUlid().toString();
        String module = request.get("module").asText();

        String sql = "select distinct a.component,a.\"sanityOnly\", split_part(a.regions,',',1) as profile " +
                "from auto_api_configuration a join auto_case_scenario b " +
                "on a.component = b.component OR b.\"componentLike\" @> to_jsonb(a.component::text) " +
                "where a.module = '" + module + "' and b.enable = true " +
                "and (b.\"sanityOnly\" = true or b.\"sanityOnly\" = a.\"sanityOnly\")";

        JsonNode componentInfoResult = DBUtil.executeLIF(sql, true, errorMessage);

        if (errorMessage.length() > 0) {
            log.error(errorMessage.toString());
            ObjectNode error = objectMapper.createObjectNode();
            error.put("error", errorMessage.toString());
            return error;
        }

        ArrayNode componentInfoList = componentInfoResult.isArray()
                ? (ArrayNode) componentInfoResult
                : objectMapper.createArrayNode();

        String runBy = request.has("runBy") ? request.get("runBy").asText("mondo-module-run") : "mondo-module-run";
        final String finalGroupId = groupId;
        final String finalRunBy = runBy;

        // Run asynchronously
        CompletableFuture.runAsync(() -> {
            List<SuiteParameter> infoList = new ArrayList<>();

            for (JsonNode json : componentInfoList) {
                try {
                    Map<String, String> paramInfoMap = new HashMap<>();
                    paramInfoMap.put("runMode", "TEST");
                    paramInfoMap.put("runBy", finalRunBy);
                    paramInfoMap.put("groupId", finalGroupId);
                    paramInfoMap.put("component", json.has("component") ? json.get("component").asText("") : "");
                    paramInfoMap.put("sanityOnly", json.has("sanityOnly") ? json.get("sanityOnly").asText("false") : "false");
                    paramInfoMap.put("profile", json.has("profile") ? json.get("profile").asText("") : "");

                    List<SuiteParameter> params = apiServiceImpl.buildSuiteParameterListForComponentRun(paramInfoMap);
                    infoList.addAll(params);
                } catch (Exception e) {
                    log.error("Error building SuiteParameter: {}", e.getMessage());
                }
            }

            // Run all in parallel
            List<CompletableFuture<Void>> runFutures = infoList.stream()
                    .map(info -> CompletableFuture.runAsync(() -> {
                        try {
                            apiServiceImpl.run(Collections.singletonList(info));
                        } catch (Exception e) {
                            log.error("Parameter: {} with error {}", info, e.getMessage());
                        }
                    }))
                    .collect(Collectors.toList());

            // Wait for all to complete
            CompletableFuture.allOf(runFutures.toArray(new CompletableFuture[0])).join();

            // Wait 60 seconds then send signal
            try {
                Thread.sleep(60 * 1000);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
            apiServiceImpl.sendCompleteSignalForModule(finalGroupId);
        });

        ObjectNode result = objectMapper.createObjectNode();
        result.put("groupId", groupId);
        return result;
    }

    private boolean isNullOrBlank(String str) {
        return str == null || str.trim().isEmpty();
    }
}
