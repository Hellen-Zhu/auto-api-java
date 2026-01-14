package citi.equities.lifecycleqa.common.enums;

/**
 * Profile 枚举类
 * 表示不同的环境配置（开发、UAT、QA等）
 */
public enum Profile {
    NAMDEV,
    EMEADEV,
    APACDEV,
    NAMUAT,
    EMEAUAT,
    APACUAT,
    NAMQA,
    EMEAQA,
    APACQA,
    UNKNOWN;

    /**
     * 判断字符串是否是合法的 Profile 枚举值（忽略大小写）
     *
     * @param value 要检查的字符串
     * @return 如果是合法的 Profile 枚举值返回 true，否则返回 false
     */
    public static boolean isValid(String value) {
        if (value == null) {
            return false;
        }
        for (Profile p : values()) {
            if (p.name().equalsIgnoreCase(value)) {
                return true;
            }
        }
        return false;
    }

    /**
     * 根据字符串返回对应枚举；如果找不到则返回 UNKNOWN
     *
     * @param value 要转换的字符串
     * @return 对应的 Profile 枚举值，如果找不到则返回 UNKNOWN
     */
    public static Profile fromString(String value) {
        if (value == null) {
            return UNKNOWN;
        }
        for (Profile p : values()) {
            if (p.name().equalsIgnoreCase(value)) {
                return p;
            }
        }
        return UNKNOWN;
    }
}
