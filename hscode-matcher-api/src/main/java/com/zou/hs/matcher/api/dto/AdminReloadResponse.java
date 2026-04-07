package com.zou.hs.matcher.api.dto;

/** Result of {@code POST /api/v1/admin/reload}. */
public record AdminReloadResponse(String status, boolean anyLanguageReady, String error) {

    public static AdminReloadResponse reloaded(boolean anyLanguageReady) {
        return new AdminReloadResponse("RELOADED", anyLanguageReady, null);
    }

    public static AdminReloadResponse failed(String message) {
        return new AdminReloadResponse("ERROR", false, message);
    }
}
