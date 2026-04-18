package io.communokot.progress.config;

import java.nio.file.Path;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.plugin.java.JavaPlugin;

public final class SettingsLoader {
    private static final int MIN_SNAPSHOT_INTERVAL = 10;
    private static final int MIN_DISPATCH_INTERVAL = 60;
    private static final int MIN_MAX_PAYLOAD = 4096;
    private static final int MIN_MAX_RETRIES = 1;
    private static final int MAX_MAX_RETRIES = 10;

    private SettingsLoader() {
    }

    public static PluginSettings load(JavaPlugin plugin) {
        FileConfiguration config = plugin.getConfig();

        String tokenFromConfig = config.getString("github.token", "").trim();
        String tokenFromEnv = System.getenv("COMMUNOKOT_GITHUB_TOKEN");
        String token = !tokenFromConfig.isEmpty() ? tokenFromConfig : nullToEmpty(tokenFromEnv).trim();

        int snapshotIntervalSeconds = Math.max(
            MIN_SNAPSHOT_INTERVAL,
            config.getInt("export.snapshotIntervalSeconds", 60)
        );
        int dispatchIntervalSeconds = Math.max(
            MIN_DISPATCH_INTERVAL,
            config.getInt("github.dispatchIntervalSeconds", 300)
        );

        int maxPayloadBytes = Math.max(
            MIN_MAX_PAYLOAD,
            config.getInt("github.maxPayloadBytes", 60000)
        );

        int maxRetries = Math.clamp(
            config.getInt("github.maxRetries", 3),
            MIN_MAX_RETRIES,
            MAX_MAX_RETRIES
        );

        return new PluginSettings(
            Path.of(getString(config, "database.path", "data/progress.db")),
            Path.of(getString(config, "snapshot.localPath", "snapshots/stone-progress.json")),
            snapshotIntervalSeconds,
            config.getBoolean("github.enabled", true),
            getString(config, "github.owner", ""),
            getString(config, "github.repo", ""),
            getString(config, "github.branch", "main"),
            getString(config, "github.eventType", "communokot_progress_snapshot"),
            getString(config, "github.targetPath", "public/stone-progress.json"),
            token,
            dispatchIntervalSeconds,
            config.getBoolean("github.payloadCompressionEnabled", true),
            maxPayloadBytes,
            maxRetries,
            getString(config, "github.commitMessage", "chore(data): update stone progress snapshot")
        );
    }

    private static String nullToEmpty(String value) {
        return value == null ? "" : value;
    }

    private static String getString(FileConfiguration config, String path, String fallback) {
        String value = config.getString(path, fallback);
        return value == null ? fallback : value;
    }
}
