package io.communokot.progress.github;

public record DispatchResult(boolean attempted, boolean success, String message, int statusCode) {
    public static DispatchResult skipped(String message) {
        return new DispatchResult(false, false, message, 0);
    }

    public static DispatchResult success(String message, int statusCode) {
        return new DispatchResult(true, true, message, statusCode);
    }

    public static DispatchResult failure(String message, int statusCode) {
        return new DispatchResult(true, false, message, statusCode);
    }
}
