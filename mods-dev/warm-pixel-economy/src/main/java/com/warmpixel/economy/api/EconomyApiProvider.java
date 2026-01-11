package com.warmpixel.economy.api;

public final class EconomyApiProvider {
    private static EconomyApi api;

    private EconomyApiProvider() {
    }

    public static EconomyApi getApi() {
        return api;
    }

    public static void setApi(EconomyApi newApi) {
        api = newApi;
    }
}
