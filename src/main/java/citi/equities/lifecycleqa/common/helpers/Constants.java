package citi.equities.lifecycleqa.common.helpers;

import org.testng.ITestResult;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

public class Constants {
    // TestNG Steps tracking
    public static Map<ITestResult, ArrayList<FastTestNGStep>> testngSteps = new ConcurrentHashMap<>();

    // Eureka URLs
    public static final List<String> EUREKA_URL = Arrays.asList(
            "http://vhkeqlcap2u:8092/",
            "http://lhkeqelaap3u:8092/",
            "http://vrdeqlcap2u:8092/",
            "http://lrdeqelaap3u:8092/",
            "http://vnyeqlcap2u:8092/",
            "http://lnyeqelaap3u:8092/"
    );

    // Service to folder name mapping
    public static final Map<String, String> SVC_FOLDERNAME = createSvcFolderNameMap();

    private static Map<String, String> createSvcFolderNameMap() {
        Map<String, String> map = new HashMap<>();
        map.put("lcdividendservice", "eh_lc_dividends_svc");
        map.put("lcmodelevalservice", "eh_lc_modeleval_svc");
        map.put("lcexerciseservice", "eh_lc_exercise_svc");
        map.put("lcpaymentservice", "eh_lc_payment_svc");
        return Collections.unmodifiableMap(map);
    }

    // Service relation mapping
    public static final Map<String, String> SVC_RELATIONMAP = createSvcRelationMap();

    private static Map<String, String> createSvcRelationMap() {
        Map<String, String> map = new HashMap<>();
        map.put("eh-ca-outturn-subscription-svc", "eh-ca-outturn-messaging-svc");
        return Collections.unmodifiableMap(map);
    }

    // APIs to ignore
    public static final List<String> API_IGNORE = Arrays.asList(
            "/app-metrics/memory.usage.info",
            "/app-metrics/memory.usage.info.top.objects",
            "/app-metrics/memory.usage.info.object"
    );

    // API Constants
    public static final String API_VERSION = "v1";
    public static final int DEFAULT_TIMEOUT = 30000;
    public static final int DEFAULT_RETRY_COUNT = 3;
    public static final int DEFAULT_RETRY_INTERVAL = 1000;

    // Status Constants
    public static final String STATUS_PASSED = "passed";
    public static final String STATUS_FAILED = "failed";
    public static final String STATUS_SKIPPED = "skipped";
    public static final String STATUS_PENDING = "pending";

    // DB Constants
    public static final String DB_TYPE_DRMS = "DRMS";
    public static final String DB_TYPE_IGNITE = "IGNITE";
    public static final String DB_TYPE_CFIM = "CFIM";

    // Placeholder Constants
    public static final String PLACEHOLDER_PREFIX = "{{";
    public static final String PLACEHOLDER_SUFFIX = "}}";

    private Constants() {
        // Private constructor to prevent instantiation
    }
}
