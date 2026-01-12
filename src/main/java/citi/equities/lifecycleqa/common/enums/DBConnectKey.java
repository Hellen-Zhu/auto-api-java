package citi.equities.lifecycleqa.common.enums;

public enum DBConnectKey {
    LIF("lif", "lif", "global"),
    DRMS_EMEA_UAT("drms-emeauat", "drms", "emeauat"),
    DRMS_EMEA_DEV("drms-emeadev", "drms", "emeadev"),
    DRMS_NAM_UAT("drms-namuat", "drms", "namuat"),
    DRMS_NAM_DEV("drms-namdev", "drms", "namdev"),
    DRMS_APAC_UAT("drms-apacuat", "drms", "apacuat"),
    DRMS_APAC_DEV("drms-apacdev", "drms", "apacdev");

    private final String connectName;
    private final String dbName;
    private final String profile;

    DBConnectKey(String connectName, String dbName, String profile) {
        this.connectName = connectName;
        this.dbName = dbName;
        this.profile = profile;
    }

    public String getConnectName() {
        return connectName;
    }

    public String getDbName() {
        return dbName;
    }

    public String getProfile() {
        return profile;
    }

    public static DBConnectKey fromConnectName(String connectName) {
        for (DBConnectKey key : DBConnectKey.values()) {
            if (key.connectName.equalsIgnoreCase(connectName)) {
                return key;
            }
        }
        return null;
    }
}
