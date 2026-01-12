package citi.equities.lifecycleqa.common.enums;

import java.util.Arrays;
import java.util.List;

public enum Profile {
    DEV("DEV", "Development"),
    TEST("TEST", "Test"),
    STAGING("STAGING", "Staging"),
    PROD("PROD", "Production"),
    LOCAL("LOCAL", "Local"),
    UAT("UAT", "User Acceptance Testing"),
    INTEGRATION("INTEGRATION", "Integration Testing"),
    PERFORMANCE("PERFORMANCE", "Performance Testing"),
    DEMO("DEMO", "Demo"),
    SANDBOX("SANDBOX", "Sandbox");

    private final String profile;
    private final String description;

    Profile(String profile, String description) {
        this.profile = profile;
        this.description = description;
    }

    public String getProfile() {
        return profile;
    }

    public String getDescription() {
        return description;
    }

    public static Profile fromString(String profile) {
        for (Profile p : Profile.values()) {
            if (p.name().equalsIgnoreCase(profile) || p.profile.equalsIgnoreCase(profile)) {
                return p;
            }
        }
        return DEV;
    }

    public static boolean isValid(String profile) {
        for (Profile p : Profile.values()) {
            if (p.name().equalsIgnoreCase(profile) || p.profile.equalsIgnoreCase(profile)) {
                return true;
            }
        }
        return false;
    }

    public static boolean isProduction(Profile profile) {
        return profile == PROD;
    }

    public static boolean isDevelopment(Profile profile) {
        List<Profile> devProfiles = Arrays.asList(DEV, LOCAL, SANDBOX);
        return devProfiles.contains(profile);
    }

    public static boolean isTesting(Profile profile) {
        List<Profile> testProfiles = Arrays.asList(TEST, UAT, INTEGRATION, PERFORMANCE, STAGING);
        return testProfiles.contains(profile);
    }
}
