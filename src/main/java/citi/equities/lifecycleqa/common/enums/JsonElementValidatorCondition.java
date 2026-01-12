package citi.equities.lifecycleqa.common.enums;

public enum JsonElementValidatorCondition {
    IsEqual,
    IsNotEqual,
    IsIn,
    IsNotIn,
    IsTrue,
    IsFalse,
    IsApproximate4,
    IsNotApproximate4,
    IsStartWith,
    IsEndWith,
    IsLengthEqual,
    IsLengthNotEqual,
    IsLengthLargerThan,
    IsLengthLessThan,
    IsJsonContain,
    IsJsonNotContain,
    IsContain,
    IsNotContain,
    IsLargerThan,
    IsLessThan,
    IsLargerThanOrEqual,
    IsLessThanOrEqual,
    IsNull,
    IsNotNull,
    IsRegexMatch,
    IsRegexNotMatch,
    IsJsonEqual,
    IsJsonNotEqual,
    IsOpposite,
    IsDateEqual,
    IsDateNotEqual,
    IsDateLargerThanOrEqual,
    IsDateLargerThan,
    IsDateLessThanOrEqual,
    IsDateLessThan,
    UnknownCondition;

    public static boolean isValid(String value) {
        if (value == null) return false;
        for (JsonElementValidatorCondition r : JsonElementValidatorCondition.values()) {
            if (r.name().equalsIgnoreCase(value)) {
                return true;
            }
        }
        return false;
    }

    public static JsonElementValidatorCondition fromString(String value) {
        for (JsonElementValidatorCondition r : JsonElementValidatorCondition.values()) {
            if (r.name().equalsIgnoreCase(value)) {
                return r;
            }
        }
        return UnknownCondition;
    }
}
