package com.warmpixel.economy.core;

public record EconomyResult(boolean success, String messageKey, Object[] messageArgs) {
    public EconomyResult {
        messageKey = messageKey == null ? "" : messageKey;
        messageArgs = messageArgs == null ? new Object[0] : messageArgs;
    }

    public static EconomyResult ok(String messageKey, Object... messageArgs) {
        return new EconomyResult(true, messageKey, messageArgs);
    }

    public static EconomyResult fail(String messageKey, Object... messageArgs) {
        return new EconomyResult(false, messageKey, messageArgs);
    }
}
