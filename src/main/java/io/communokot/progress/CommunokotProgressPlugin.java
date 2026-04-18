package io.communokot.progress;

import io.communokot.progress.command.CommunokotCommand;
import io.communokot.progress.config.PluginSettings;
import io.communokot.progress.config.SettingsLoader;
import io.communokot.progress.db.DatabaseService;
import io.communokot.progress.export.ExportService;
import io.communokot.progress.github.DispatchResult;
import io.communokot.progress.github.GitHubSyncService;
import io.communokot.progress.listener.StoneBreakListener;
import io.communokot.progress.service.ProgressService;
import java.nio.file.Path;
import java.time.Instant;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import java.util.concurrent.locks.ReentrantLock;
import java.util.logging.Level;
import org.bukkit.command.CommandSender;
import org.bukkit.scheduler.BukkitTask;
import org.bukkit.plugin.java.JavaPlugin;

public final class CommunokotProgressPlugin extends JavaPlugin {
    private final ReentrantLock cycleLock = new ReentrantLock();

    private ProgressService progressService;
    private DatabaseService databaseService;
    private ExportService exportService;
    private GitHubSyncService gitHubSyncService;
    private PluginSettings settings;

    private BukkitTask snapshotTask;
    private BukkitTask dispatchTask;

    @Override
    public void onEnable() {
        try {
            saveDefaultConfig();
            settings = SettingsLoader.load(this);

            progressService = new ProgressService();
            databaseService = new DatabaseService(this, settings.databasePath());
            databaseService.start();

            Map<UUID, Long> initialProgress = databaseService.loadStoneProgress();
            progressService.loadInitialProgress(initialProgress);

            Path dataFolder = getDataFolder().toPath();
            exportService = new ExportService(dataFolder, settings.snapshotPath());
            gitHubSyncService = new GitHubSyncService(this, settings);

            getServer().getPluginManager().registerEvents(new StoneBreakListener(progressService), this);

            CommunokotCommand commandExecutor = new CommunokotCommand(this);
            Objects.requireNonNull(getCommand("communokot"), "communokot command missing in plugin.yml")
                .setExecutor(commandExecutor);
            Objects.requireNonNull(getCommand("communokot"), "communokot command missing in plugin.yml")
                .setTabCompleter(commandExecutor);

            scheduleTasks();

            getLogger().info("CommunokotProgress enabled with " + initialProgress.size() + " player records loaded.");
        } catch (Exception exception) {
            getLogger().log(Level.SEVERE, "Failed to enable CommunokotProgress", exception);
            getServer().getPluginManager().disablePlugin(this);
        }
    }

    @Override
    public void onDisable() {
        cancelTasks();
        flushOnShutdown();
        if (databaseService != null) {
            databaseService.close();
        }
    }

    public void triggerManualExport(CommandSender sender) {
        sender.sendMessage("[Communokot] Manual export started...");
        getServer().getScheduler().runTaskAsynchronously(this, () -> {
            String outcome = runCycle(true, true, "manual-command");
            getServer().getScheduler().runTask(this, () -> sender.sendMessage("[Communokot] " + outcome));
        });
    }

    public void reloadRuntimeSettings(CommandSender sender) {
        try {
            PluginSettings oldSettings = this.settings;

            reloadConfig();
            this.settings = SettingsLoader.load(this);

            if (!oldSettings.databasePath().equals(this.settings.databasePath())) {
                sender.sendMessage("[Communokot] database.path changed but requires server restart to take effect.");
                getLogger().warning("database.path changed on reload; restart required to apply new DB location.");
            }

            exportService.updateSnapshotPath(this.settings.snapshotPath());
            gitHubSyncService = new GitHubSyncService(this, this.settings);
            scheduleTasks();

            sender.sendMessage("[Communokot] Configuration reloaded successfully.");
        } catch (Exception exception) {
            getLogger().log(Level.SEVERE, "Configuration reload failed", exception);
            sender.sendMessage("[Communokot] Reload failed: " + exception.getMessage());
        }
    }

    private void scheduleTasks() {
        cancelTasks();

        long snapshotIntervalTicks = settings.snapshotIntervalSeconds() * 20L;
        long dispatchIntervalTicks = settings.githubDispatchIntervalSeconds() * 20L;

        snapshotTask = getServer().getScheduler().runTaskTimerAsynchronously(
            this,
            () -> runCycle(false, false, "snapshot-scheduler"),
            snapshotIntervalTicks,
            snapshotIntervalTicks
        );

        dispatchTask = getServer().getScheduler().runTaskTimerAsynchronously(
            this,
            () -> runCycle(true, true, "dispatch-scheduler"),
            dispatchIntervalTicks,
            dispatchIntervalTicks
        );
    }

    private void cancelTasks() {
        if (snapshotTask != null) {
            snapshotTask.cancel();
            snapshotTask = null;
        }
        if (dispatchTask != null) {
            dispatchTask.cancel();
            dispatchTask = null;
        }
    }

    private void flushOnShutdown() {
        if (progressService == null || databaseService == null || exportService == null) {
            return;
        }

        cycleLock.lock();
        try {
            String outcome = executeCycle(true, settings != null && settings.githubEnabled(), "shutdown");
            getLogger().info("Shutdown flush result: " + outcome);
        } catch (Exception exception) {
            getLogger().log(Level.WARNING, "Shutdown flush failed", exception);
        } finally {
            cycleLock.unlock();
        }
    }

    private String runCycle(boolean persistToDatabase, boolean dispatchToGitHub, String reason) {
        if (!cycleLock.tryLock()) {
            return "Cycle skipped because another cycle is already running (" + reason + ")";
        }

        try {
            return executeCycle(persistToDatabase, dispatchToGitHub, reason);
        } catch (Exception exception) {
            getLogger().log(Level.WARNING, "Cycle failed (" + reason + ")", exception);
            return "Cycle failed (" + reason + "): " + exception.getMessage();
        } finally {
            cycleLock.unlock();
        }
    }

    private String executeCycle(boolean persistToDatabase, boolean dispatchToGitHub, String reason) throws Exception {
        Instant generatedAt = Instant.now();
        Map<UUID, Long> snapshot = progressService.snapshotStoneBroken();
        String json = exportService.buildSnapshotJson(generatedAt, snapshot);

        Path snapshotPath = exportService.writeSnapshot(json);
        StringBuilder status = new StringBuilder();
        status.append("Snapshot written to ").append(snapshotPath);

        if (persistToDatabase) {
            databaseService.saveStoneProgress(snapshot, generatedAt);
            status.append(" | SQLite flushed");
        }

        if (dispatchToGitHub) {
            DispatchResult dispatch = gitHubSyncService.dispatchSnapshot(json, generatedAt);
            status.append(" | GitHub: ").append(dispatch.message());
            if (!dispatch.success() && dispatch.attempted()) {
                getLogger().warning("GitHub dispatch failed (" + reason + "): " + dispatch.message());
            }
            if (dispatch.success()) {
                getLogger().info("GitHub dispatch succeeded with status " + dispatch.statusCode());
            }
        }

        if ("snapshot-scheduler".equals(reason)) {
            getLogger().fine(status.toString());
        } else {
            getLogger().info(status.toString());
        }

        return status.toString();
    }
}
