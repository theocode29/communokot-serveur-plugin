package io.communokot.progress.github;

import io.communokot.progress.config.PluginSettings;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.util.Base64;
import java.util.zip.GZIPOutputStream;
import org.bukkit.plugin.java.JavaPlugin;

public final class GitHubSyncService {
    private static final String API_VERSION = "2022-11-28";

    private final JavaPlugin plugin;
    private final PluginSettings settings;
    private final HttpClient httpClient;

    public GitHubSyncService(JavaPlugin plugin, PluginSettings settings) {
        this.plugin = plugin;
        this.settings = settings;
        this.httpClient = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(10))
            .build();
    }

    public DispatchResult dispatchSnapshot(String snapshotJson, Instant generatedAt) {
        if (!settings.githubEnabled()) {
            return DispatchResult.skipped("GitHub sync disabled in config");
        }

        if (isBlank(settings.githubOwner()) || isBlank(settings.githubRepo()) || isBlank(settings.githubEventType())) {
            return DispatchResult.skipped("GitHub owner/repo/eventType is incomplete");
        }

        if (isBlank(settings.githubToken())) {
            return DispatchResult.skipped("GitHub token is empty (github.token or COMMUNOKOT_GITHUB_TOKEN)");
        }

        try {
            byte[] rawSnapshot = snapshotJson.getBytes(StandardCharsets.UTF_8);
            String encoding = "base64";
            if (settings.githubPayloadCompressionEnabled()) {
                rawSnapshot = gzip(rawSnapshot);
                encoding = "gzip+base64";
            }

            String base64Snapshot = Base64.getEncoder().encodeToString(rawSnapshot);
            String requestBody = buildRequestBody(base64Snapshot, encoding, generatedAt);

            byte[] bodyBytes = requestBody.getBytes(StandardCharsets.UTF_8);
            if (bodyBytes.length > settings.githubMaxPayloadBytes()) {
                String message = "Dispatch payload too large (%d bytes > %d bytes)".formatted(
                    bodyBytes.length,
                    settings.githubMaxPayloadBytes()
                );
                return DispatchResult.skipped(message);
            }

            return sendWithRetry(requestBody);
        } catch (Exception exception) {
            return DispatchResult.failure("GitHub dispatch failed: " + exception.getMessage(), 0);
        }
    }

    private DispatchResult sendWithRetry(String requestBody) {
        URI endpoint = URI.create(
            "https://api.github.com/repos/%s/%s/dispatches".formatted(settings.githubOwner(), settings.githubRepo())
        );

        int retries = settings.githubMaxRetries();
        int lastStatusCode = 0;
        String lastResponseBody = "";

        for (int attempt = 1; attempt <= retries; attempt++) {
            try {
                HttpRequest request = HttpRequest.newBuilder(endpoint)
                    .timeout(Duration.ofSeconds(15))
                    .header("Accept", "application/vnd.github+json")
                    .header("Authorization", "Bearer " + settings.githubToken())
                    .header("X-GitHub-Api-Version", API_VERSION)
                    .header("Content-Type", "application/json")
                    .POST(HttpRequest.BodyPublishers.ofString(requestBody, StandardCharsets.UTF_8))
                    .build();

                HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
                lastStatusCode = response.statusCode();
                lastResponseBody = response.body() == null ? "" : response.body();

                if (response.statusCode() >= 200 && response.statusCode() < 300) {
                    return DispatchResult.success("repository_dispatch accepted by GitHub", response.statusCode());
                }

                if (!isRetriableStatus(response.statusCode()) || attempt == retries) {
                    return DispatchResult.failure(
                        "GitHub dispatch rejected (%d): %s".formatted(response.statusCode(), truncate(lastResponseBody, 300)),
                        response.statusCode()
                    );
                }

                sleepBackoff(attempt);
            } catch (IOException | InterruptedException exception) {
                if (exception instanceof InterruptedException) {
                    Thread.currentThread().interrupt();
                    return DispatchResult.failure("Dispatch interrupted", lastStatusCode);
                }

                if (attempt == retries) {
                    return DispatchResult.failure("Dispatch network failure: " + exception.getMessage(), lastStatusCode);
                }

                sleepBackoff(attempt);
            }
        }

        return DispatchResult.failure(
            "GitHub dispatch failed after retries: " + truncate(lastResponseBody, 300),
            lastStatusCode
        );
    }

    private String buildRequestBody(String base64Snapshot, String encoding, Instant generatedAt) {
        return """
            {
              "event_type": "%s",
              "client_payload": {
                "schema_version": 1,
                "generated_at": "%s",
                "target_branch": "%s",
                "target_path": "%s",
                "commit_message": "%s",
                "encoding": "%s",
                "snapshot": "%s"
              }
            }
            """.formatted(
            escapeJson(settings.githubEventType()),
            escapeJson(generatedAt.toString()),
            escapeJson(settings.githubBranch()),
            escapeJson(settings.githubTargetPath()),
            escapeJson(settings.githubCommitMessage()),
            escapeJson(encoding),
            escapeJson(base64Snapshot)
        );
    }

    private byte[] gzip(byte[] raw) throws IOException {
        ByteArrayOutputStream outputStream = new ByteArrayOutputStream();
        try (GZIPOutputStream gzip = new GZIPOutputStream(outputStream)) {
            gzip.write(raw);
        }
        return outputStream.toByteArray();
    }

    private boolean isRetriableStatus(int statusCode) {
        return statusCode == 429 || statusCode >= 500;
    }

    private void sleepBackoff(int attempt) {
        long waitMs = Math.min(4000L, 1000L * (1L << (attempt - 1)));
        try {
            Thread.sleep(waitMs);
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            plugin.getLogger().warning("Dispatch backoff interrupted");
        }
    }

    private String escapeJson(String value) {
        StringBuilder escaped = new StringBuilder();
        for (int i = 0; i < value.length(); i++) {
            char ch = value.charAt(i);
            switch (ch) {
                case '"' -> escaped.append("\\\"");
                case '\\' -> escaped.append("\\\\");
                case '\b' -> escaped.append("\\b");
                case '\f' -> escaped.append("\\f");
                case '\n' -> escaped.append("\\n");
                case '\r' -> escaped.append("\\r");
                case '\t' -> escaped.append("\\t");
                default -> {
                    if (ch < 0x20) {
                        escaped.append(String.format("\\u%04x", (int) ch));
                    } else {
                        escaped.append(ch);
                    }
                }
            }
        }
        return escaped.toString();
    }

    private String truncate(String value, int maxChars) {
        if (value == null || value.length() <= maxChars) {
            return value;
        }
        return value.substring(0, maxChars) + "...";
    }

    private boolean isBlank(String value) {
        return value == null || value.isBlank();
    }
}
