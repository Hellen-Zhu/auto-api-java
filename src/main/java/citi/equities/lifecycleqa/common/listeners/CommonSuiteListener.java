package citi.equities.lifecycleqa.common.listeners;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import citi.equities.lifecycleqa.common.enums.AutomationRunMode;
import citi.equities.lifecycleqa.common.enums.DashboardType;
import citi.equities.lifecycleqa.common.enums.LIFDBStatement;
import citi.equities.lifecycleqa.common.helpers.ReportHelper;
import citi.equities.lifecycleqa.common.utils.DBUtil;
import citi.equities.lifecycleqa.common.utils.DataTypeUtil;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.testng.ISuite;
import org.testng.ISuiteListener;

import java.util.HashMap;
import java.util.Map;

public class CommonSuiteListener implements ISuiteListener {
    private static final Logger log = LoggerFactory.getLogger(CommonSuiteListener.class);
    private static final ObjectMapper objectMapper = new ObjectMapper();

    @Override
    public void onStart(ISuite suite) {
        Map<String, String> suiteParameters = suite.getXmlSuite().getParameters();
        StringBuilder errorMessage = new StringBuilder();

        insertCaseAuditsBeforeSuiteStart(suiteParameters, errorMessage);
        insertRunProgressBeforeSuiteStart(suiteParameters, errorMessage);
        insertGroupProgressBeforeSuiteStart(suiteParameters, errorMessage);

        if (errorMessage.length() > 0) {
            log.error("Skipping suite execution due to errors: {}", errorMessage);
            return;
        }
        log.info("Start to Run suite with params {}", suite.getXmlSuite().getParameters());
    }

    @Override
    public void onFinish(ISuite suite) {
        Map<String, String> suiteParameters = suite.getXmlSuite().getParameters();

        updateCaseAuditsStatusWhenSuiteFinish(suiteParameters);
        updateRunProgressStatusWhenSuiteFinish(suiteParameters);
        updateGroupProgressWhenSuiteFinish(suiteParameters, suite.getResults().isEmpty());
        uploadDashboardAndUpdatePassrateWhenSuiteFinish(suiteParameters, !suite.getResults().isEmpty());
        log.info("Finish to Run suite with params {}", suite.getXmlSuite().getParameters());
    }

    private void insertCaseAuditsBeforeSuiteStart(Map<String, String> suiteParameters, StringBuilder errorMessage) {
        StringBuilder sb = new StringBuilder();
        String runId = suiteParameters.get("runId");
        JsonNode config = DataTypeUtil.convertToJsonNode(suiteParameters.getOrDefault("config", "{}"));
        JsonNode scenarioAudits = DataTypeUtil.convertToJsonNode(suiteParameters.getOrDefault("scenarioAudits", "{}"));
        String region = config.has("region") ? config.get("region").asText("") : "";
        String env = config.has("env") ? config.get("env").asText("") : "";

//        sb.append("delete from auto_case_audit where \"runId\"='").append(runId).append("';\n");

        if (scenarioAudits.isObject()) {
            scenarioAudits.fieldNames().forEachRemaining(key -> {
                JsonNode auditsArray = scenarioAudits.get(key);
                if (auditsArray != null && auditsArray.isArray()) {
                    auditsArray.forEach(finalAutoCaseAudit -> {
                        if (finalAutoCaseAudit.isObject()) {
                            ObjectNode audit = (ObjectNode) finalAutoCaseAudit;
                            String summary = audit.has("summary") ? audit.get("summary").asText("").replace("'", "''") : "";
                            String suite = audit.has("suite") ? audit.get("suite").asText("").replace("'", "''") : "";
                            String scenario = audit.has("scenario") ? audit.get("scenario").asText("").replace("'", "''") : "";
                            String runStatus = audit.has("runStatus") ? audit.get("runStatus").asText("") : "";
                            JsonNode scriptData = DataTypeUtil.processJsonElementForSingleQuotation(
                                    audit.has("caseScript") && audit.get("caseScript").isArray()
                                            ? audit.get("caseScript")
                                            : (audit.has("templateScript") && audit.get("templateScript").isArray()
                                            ? audit.get("templateScript")
                                            : objectMapper.createArrayNode())
                            );
                            String runMode = audit.has("runMode") ? audit.get("runMode").asText("") : "";
                            JsonNode variables = DataTypeUtil.processJsonElementForSingleQuotation(
                                    audit.has("variables") && audit.get("variables").isObject()
                                            ? audit.get("variables")
                                            : objectMapper.createObjectNode()
                            );
                            int caseId = audit.has("caseId") ? audit.get("caseId").asInt(0) : 0;
                            String label = audit.has("label") ? audit.get("label").asText("").replace("'", "''") : "";
                            String issueKey = audit.has("issueKey") ? audit.get("issueKey").asText("").replace("'", "''") : "";
                            JsonNode testData = DataTypeUtil.processJsonElementForSingleQuotation(
                                    audit.has("testData") && audit.get("testData").isObject()
                                            ? audit.get("testData")
                                            : objectMapper.createObjectNode()
                            );
                            JsonNode configProcessed = DataTypeUtil.processJsonElementForSingleQuotation(config);

                            sb.append("insert into auto_case_audit (\"runId\",suite,scenario,\"issueKey\",summary,\"runStatus\",script,\"testData\",element,config,")
                                    .append("\"runMode\",\"caseId\",label,region,env,variables) ")
                                    .append("values('").append(runId).append("','").append(suite).append("','").append(scenario).append("','")
                                    .append(issueKey).append("','").append(summary).append("','").append(runStatus).append("',")
                                    .append("'").append(scriptData.toString()).append("'::jsonb, '").append(testData.toString()).append("'::jsonb,")
                                    .append("'{}'::jsonb,'").append(configProcessed.toString()).append("'::jsonb,'").append(runMode).append("',")
                                    .append(caseId).append(",'").append(label).append("','").append(region).append("','").append(env).append("',")
                                    .append("'").append(variables.toString()).append("');\n");
                        }
                    });
                }
            });
        }

        DBUtil.executeLIF(sb.toString(), true, errorMessage);
        if (errorMessage.length() > 0) {
            log.error("Failed to insert CaseAudits Before Suite Start with error {}", errorMessage);
        }
    }

    private void insertRunProgressBeforeSuiteStart(Map<String, String> suiteParameters, StringBuilder errorMessage) {
        String runMode = suiteParameters.get("runMode");
        if (runMode != null && runMode.equalsIgnoreCase(AutomationRunMode.TEST.name())) {
            Map<String, Object> params = new HashMap<>();
            params.put("runId", suiteParameters.get("runId"));
            DBUtil.executeLIF(LIFDBStatement.InsertAutoProgressFromAudit.name(), false, errorMessage, params);
            if (errorMessage.length() > 0) {
                log.error("Failed to Insert RunProgress Before Suite Start with error {}", errorMessage);
            }
        }
    }

    private void updateRunProgressStatusWhenSuiteFinish(Map<String, String> suiteParameters) {
        StringBuilder errorMessage = new StringBuilder();
        String runMode = suiteParameters.get("runMode");
        if (runMode != null && runMode.equalsIgnoreCase(AutomationRunMode.TEST.name())) {
            String runId = suiteParameters.get("runId");
            Map<String, Object> params = new HashMap<>();
            params.put("runId", runId);
            params.put("type", "end");
            DBUtil.executeLIF(LIFDBStatement.UpdateAutoProgress.name(), false, errorMessage, params);
            if (errorMessage.length() > 0) {
                log.error("Failed to update RunProgress Status When Suite Finish with error {}", errorMessage);
            }
        }
    }

    private void updateCaseAuditsStatusWhenSuiteFinish(Map<String, String> suiteParameters) {
        StringBuilder errorMessage = new StringBuilder();
        String runId = suiteParameters.get("runId");
        DBUtil.executeLIF(
                "update auto_case_audit set \"runStatus\" = 'FAILED' where \"runStatus\" in ( 'PENDING', 'PROCESSING' ) and \"runId\" = '" + runId + "'",
                true,
                errorMessage
        );
        if (errorMessage.length() > 0) {
            log.error("Failed to update CaseAudits Status When Suite finish with error {}", errorMessage);
        }
    }

    private void insertGroupProgressBeforeSuiteStart(Map<String, String> suiteParameters, StringBuilder errorMessage) {
        String groupId = suiteParameters.get("groupId");
        if (groupId != null) {
            String runId = suiteParameters.get("runId");
            String component = suiteParameters.get("component");
            String sql = "insert into report_progress(group_id,run_id,component,report,date,processed,module,label) \n" +
                    "select '" + groupId + "', '" + runId + "','" + component + "',module,'CURRENT_DATE','N',module,null from auto_api_configuration where component = '" + component + "' limit 1";
            DBUtil.executeLIF(sql, true, errorMessage);
            if (errorMessage.length() > 0) {
                log.error("Failed to Insert GroupProgress Before Suite Start with error {}", errorMessage);
            }
        }
    }

    private void updateGroupProgressWhenSuiteFinish(Map<String, String> suiteParameters, boolean ifDelete) {
        StringBuilder errorMessage = new StringBuilder();
        String groupId = suiteParameters.get("groupId");
        if (groupId != null) {
            String runId = suiteParameters.get("runId");
            if (ifDelete) {
                DBUtil.executeLIF(
                        "Update report_progress set processed = 'Y' where run_id = '" + runId + "' and group_id = '" + groupId + "'",
                        true,
                        errorMessage
                );
            } else {
                DBUtil.executeLIF(
                        "delete from report_progress where run_id = '" + runId + "' and group_id = '" + groupId + "'",
                        true,
                        errorMessage
                );
            }
            if (errorMessage.length() > 0) {
                log.error("Failed to updateGroupProgressWhenSuiteFinish with error {}", errorMessage);
            }
        }
    }

    private void uploadDashboardAndUpdatePassrateWhenSuiteFinish(Map<String, String> suiteParameters, boolean ifValid) {
        StringBuilder errorMessage = new StringBuilder();
        String runMode = suiteParameters.get("runMode");
        if (runMode != null && runMode.equalsIgnoreCase(AutomationRunMode.TEST.name()) && ifValid) {
            ReportHelper.uploadDashboard(suiteParameters.get("runId"), DashboardType.ALL);
            Map<String, Object> params = new HashMap<>();
            params.put("component", suiteParameters.get("component"));
            DBUtil.executeLIF(LIFDBStatement.UpdateAutoCasePassrate.name(), false, errorMessage, params);
            if (errorMessage.length() > 0) {
                log.error("Failed to UpdateAutoCasePassrate with error {}", errorMessage);
            }
        }
    }
}
