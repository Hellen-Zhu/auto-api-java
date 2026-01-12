package citi.equities.lifecycleqa.common.enums;

import java.util.Arrays;
import java.util.List;

public enum DSEnum {
    PRIMARY("PRIMARY", "Primary DataSource"),
    SECONDARY("SECONDARY", "Secondary DataSource"),
    BACKUP("BACKUP", "Backup DataSource"),
    READ_ONLY("READ_ONLY", "Read Only DataSource"),
    WRITE_ONLY("WRITE_ONLY", "Write Only DataSource"),
    MASTER("MASTER", "Master DataSource"),
    SLAVE("SLAVE", "Slave DataSource"),
    CACHE("CACHE", "Cache DataSource"),
    TEMP("TEMP", "Temp DataSource"),
    ARCHIVE("ARCHIVE", "Archive DataSource");

    private final String key;
    private final String description;

    DSEnum(String key, String description) {
        this.key = key;
        this.description = description;
    }

    public String getKey() {
        return key;
    }

    public String getDescription() {
        return description;
    }

    public static DSEnum fromKey(String key) {
        for (DSEnum ds : DSEnum.values()) {
            if (ds.key.equalsIgnoreCase(key)) {
                return ds;
            }
        }
        return null;
    }

    public static boolean isReadable(DSEnum key) {
        List<DSEnum> readableList = Arrays.asList(PRIMARY, SECONDARY, READ_ONLY, MASTER, SLAVE, CACHE, ARCHIVE);
        return readableList.contains(key);
    }

    public static boolean isWritable(DSEnum key) {
        List<DSEnum> writableList = Arrays.asList(PRIMARY, SECONDARY, WRITE_ONLY, MASTER, CACHE, TEMP);
        return writableList.contains(key);
    }
}
