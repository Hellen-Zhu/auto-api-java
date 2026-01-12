package citi.equities.lifecycleqa.service.impl;

import citi.equities.lifecycleqa.service.APIService;
import com.fasterxml.jackson.databind.JsonNode;
import com.github.f4b6a3.ulid.UlidCreator;
import citi.equities.lifecycleqa.common.entities.SuiteParameter;
import citi.equities.lifecycleqa.common.enums.AutomationMode;
import citi.equities.lifecycleqa.common.enums.AutomationRunMode;
import citi.equities.lifecycleqa.common.enums.AutomationType;
import citi.equities.lifecycleqa.common.enums.Profile;
import citi.equities.lifecycleqa.common.utils.DBUtil;
import io.restassured.RestAssured;
import io.restassured.http.ContentType;
import io.restassured.response.Response;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.testng.TestNG;
import org.testng.xml.SuiteXmlParser;
import org.testng.xml.XmlSuite;

import java.io.InputStream;
import java.util.*;
import java.util.stream.Collectors;

@Service
public class APIServiceImpl implements APIService {
    private static final Logger log = LoggerFactory.getLogger(APIServiceImpl.class);
    private static final String XML_FILE = "testng.xml";

    private List<XmlSuite> createTestSuiteXml(List<SuiteParameter> infos) {
        SuiteXmlParser suiteXmlParser = new SuiteXmlParser();
        List<XmlSuite> suites = new ArrayList<>();

        for (SuiteParameter suiteParameter : infos) {
            InputStream inputStream = APIServiceImpl.class.getClassLoader().getResourceAsStream(XML_FILE);
            XmlSuite xmlSuite = suiteXmlParser.parse(XML_FILE, inputStream, true);

            Map<String, String> params = new HashMap<>();
            params.put("runId", suiteParameter.getRunId());
            params.put("automationType", suiteParameter.getAutomationType().name());
            params.put("runMode", suiteParameter.getRunMode().name());
            params.put("automationMode", suiteParameter.getAutomationMode().name());
            params.put("profile", suiteParameter.getProfile().name());
            params.put("componentOrId", String.valueOf(suiteParameter.getComponentOrId()));
            params.put("sanityOnly", String.valueOf(suiteParameter.isSanityOnly()));
            params.put("runBy", suiteParameter.getRunBy());

            if (suiteParameter.getProjectKey() != null) {
                params.put("projectKey", suiteParameter.getProjectKey());
            }
            if (suiteParameter.getReleaseVersion() != null) {
                params.put("releaseVersion", suiteParameter.getReleaseVersion());
            }
            if (suiteParameter.getGroupId() != null) {
                params.put("groupId", suiteParameter.getGroupId());
            }

            xmlSuite.setParameters(params);
            suites.add(xmlSuite);
        }
        return suites;
    }

    private void runTestNG(List<XmlSuite> xmlSuites) {
        TestNG testNG = new TestNG();
        testNG.setUseDefaultListeners(false);
        testNG.setXmlSuites(xmlSuites);
        testNG.run();
    }

    @Override
    public void run(List<SuiteParameter> list) {
        log.info("Start to run: {}", list);
        for (SuiteParameter param : list) {
            runTestNG(createTestSuiteXml(Collections.singletonList(param)));
        }
    }

    @Override
    public List<SuiteParameter> buildSuiteParameterListForIdRun(String id, String profile) {
        if (!validProfile(profile)) {
            return Collections.emptyList();
        }

        String[] profileArray = profile.split(",");
        List<SuiteParameter> result = new ArrayList<>();

        for (String p : profileArray) {
            String trimmedProfile = p.trim();
            SuiteParameter param = new SuiteParameter(
                    UlidCreator.getUlid().toString(),
                    AutomationType.API,
                    AutomationRunMode.DEBUG,
                    AutomationMode.ID,
                    Profile.fromString(trimmedProfile),
                    id,
                    "mondo-api-run",
                    false
            );
            result.add(param);
        }

        return result;
    }

    @Override
    public List<SuiteParameter> buildSuiteParameterListForComponentRun(Map<String, String> info) {
        String profileStr = info.get("profile");
        if (profileStr == null || profileStr.isEmpty()) {
            return Collections.emptyList();
        }

        String[] profileArray = profileStr.split(",");
        List<String> profileList = Arrays.stream(profileArray)
                .map(String::trim)
                .collect(Collectors.toList());

        // Check if any profile is invalid
        boolean hasInvalid = profileList.stream().anyMatch(p -> !Profile.isValid(p));
        if (hasInvalid) {
            log.error("Invalid profile in {}", info);
            return Collections.emptyList();
        }

        List<SuiteParameter> result = new ArrayList<>();
        for (String profile : profileList) {
            SuiteParameter param = new SuiteParameter(
                    UlidCreator.getUlid().toString(),
                    AutomationType.API,
                    AutomationRunMode.fromString(info.getOrDefault("runMode", "TEST")),
                    AutomationMode.COMPONENT,
                    Profile.fromString(profile),
                    info.getOrDefault("component", ""),
                    info.getOrDefault("runBy", "mondo-api-run"),
                    Boolean.parseBoolean(info.getOrDefault("sanityOnly", "false"))
            );
            param.setProjectKey(info.getOrDefault("projectKey", ""));
            param.setReleaseVersion(info.getOrDefault("releaseVersion", ""));
            param.setGroupId(info.get("groupId"));
            result.add(param);
        }

        return result;
    }

    @Override
    public void sendCompleteSignalForModule(String groupId) {
        if (groupId != null && !groupId.isEmpty()) {
            StringBuilder errorMessage = new StringBuilder();
            String sql = "select count(*) from report_progress where group_id = '" + groupId + "' and processed = 'N' limit 1";
            JsonNode result = DBUtil.executeLIF(sql, true, errorMessage);

            if (errorMessage.length() > 0) {
                log.error(errorMessage.toString());
                return;
            }

            int count = result.asInt(0);
            if (count == 0) {
                log.info("Send Complete Signal For Module with GroupId {}", groupId);
                Response res = RestAssured.given()
                        .contentType(ContentType.JSON)
                        .post("http://mondo.nam.nsroot.net:8000/api/automation/email/group_id?group_id=" + groupId);
                log.info("Send Complete Signal For Module with Response {}, {}", res.statusCode(), res.asString());
            } else {
                log.info("No Send Complete Signal For Module because of Not all processed for groupId {}", groupId);
            }
        }
    }

    private boolean validProfile(String profile) {
        String[] profiles = profile.split(",");
        List<String> invalidProfiles = Arrays.stream(profiles)
                .map(String::trim)
                .filter(p -> !Profile.isValid(p))
                .collect(Collectors.toList());

        if (!invalidProfiles.isEmpty()) {
            log.error("Invalid profiles found: {}, which should be in {}",
                    String.join(", ", invalidProfiles),
                    Arrays.toString(Profile.values()));
        }
        return invalidProfiles.isEmpty();
    }
}
