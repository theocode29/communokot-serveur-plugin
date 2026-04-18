package io.communokot.progress.config;

import java.nio.file.Path;

public record PluginSettings(
    Path databasePath,
    Path snapshotPath,
    int snapshotIntervalSeconds,
    boolean githubEnabled,
    String githubOwner,
    String githubRepo,
    String githubBranch,
    String githubEventType,
    String githubTargetPath,
    String githubToken,
    int githubDispatchIntervalSeconds,
    boolean githubPayloadCompressionEnabled,
    int githubMaxPayloadBytes,
    int githubMaxRetries,
    String githubCommitMessage
) {
}
