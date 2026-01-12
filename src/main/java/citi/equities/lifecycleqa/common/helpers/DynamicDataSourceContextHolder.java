package citi.equities.lifecycleqa.common.helpers;

import citi.equities.lifecycleqa.common.enums.DSEnum;

public class DynamicDataSourceContextHolder {
    private static final ThreadLocal<DSEnum> threadLocal = new ThreadLocal<>();

    public static void setDataSourceContext(DSEnum dsEnum) {
        threadLocal.set(dsEnum);
    }

    public static DSEnum getDataSourceContext() {
        return threadLocal.get();
    }

    public static void clearDataSourceContext() {
        threadLocal.remove();
    }
}
