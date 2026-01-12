package citi.equities.lifecycleqa.common.enums;

public enum EHEureka {
    HK_UAT("http://vhkeqlcap2u:8092", "hkuat"),
    HK_DEV("http://lhkeqelaap3u:8092", "hkdev"),
    EMEA_UAT("http://vrdeqlcap2u:8092", "emeauat"),
    EMEA_DEV("http://lrdeqelaap3u:8092", "emeadev"),
    NAM_UAT("http://vnyeqlcap2u:8092", "namuat"),
    NAM_DEV("http://lnyeqelaap3u:8092", "namdev");

    private final String url;
    private final String profile;

    EHEureka(String url, String profile) {
        this.url = url;
        this.profile = profile;
    }

    public String getUrl() {
        return url;
    }

    public String getProfile() {
        return profile;
    }

    public static EHEureka fromUrl(String url) {
        for (EHEureka eureka : EHEureka.values()) {
            if (url.contains(eureka.url)) {
                return eureka;
            }
        }
        return null;
    }
}
