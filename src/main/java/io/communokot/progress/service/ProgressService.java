package io.communokot.progress.service;

import io.communokot.progress.model.PlayerProgress;
import java.util.Collections;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.Statistic;
import org.bukkit.advancement.Advancement;
import org.bukkit.entity.Player;

/**
 * In-memory cache of every tracked player's progress.
 *
 * <p>Data flows in from three sources:
 * <ol>
 *   <li>Database hydration at startup ({@link #loadFromDatabase}).</li>
 *   <li>Live Bukkit stat reads ({@link #syncOnlinePlayer}).</li>
 *   <li>Advancement events ({@link #recordAdvancement}).</li>
 * </ol>
 *
 * <p>The snapshot cycle calls {@link #syncAllOnlinePlayers} then reads
 * the cache via {@link #snapshot} to build the export JSON.
 */
public final class ProgressService {
    /**
     * The block types we track under "mined". The key is the short name
     * used in the JSON; the value is an array of Materials whose counts
     * are summed together (e.g. stone + deepslate variants).
     */
    private static final Map<String, Material[]> TRACKED_BLOCKS = Map.of(
        "stone",        new Material[]{ Material.STONE },
        "coal_ore",     new Material[]{ Material.COAL_ORE, Material.DEEPSLATE_COAL_ORE },
        "iron_ore",     new Material[]{ Material.IRON_ORE, Material.DEEPSLATE_IRON_ORE },
        "gold_ore",     new Material[]{ Material.GOLD_ORE, Material.DEEPSLATE_GOLD_ORE },
        "diamond_ore",  new Material[]{ Material.DIAMOND_ORE, Material.DEEPSLATE_DIAMOND_ORE },
        "copper_ore",   new Material[]{ Material.COPPER_ORE, Material.DEEPSLATE_COPPER_ORE },
        "lapis_ore",    new Material[]{ Material.LAPIS_ORE, Material.DEEPSLATE_LAPIS_ORE },
        "redstone_ore", new Material[]{ Material.REDSTONE_ORE, Material.DEEPSLATE_REDSTONE_ORE },
        "emerald_ore",  new Material[]{ Material.EMERALD_ORE, Material.DEEPSLATE_EMERALD_ORE }
    );

    private final ConcurrentHashMap<UUID, PlayerProgress> progressByPlayer = new ConcurrentHashMap<>();

    // ── Database hydration ──────────────────────────────────────────────

    /**
     * Load previously persisted progress records into the cache.
     * Called once at plugin enable.
     */
    public void loadFromDatabase(Map<UUID, PlayerProgress> records) {
        progressByPlayer.putAll(records);
    }

    // ── Live Bukkit stat sync ───────────────────────────────────────────

    /**
     * Refresh an online player's cached stats from native Bukkit statistics.
     * Must be called from the main thread (Bukkit stats access is not thread-safe).
     */
    public void syncOnlinePlayer(Player player) {
        PlayerProgress progress = getOrCreate(player.getUniqueId(), player.getName());
        progress.updateName(player.getName());

        // Distance and play time
        progress.setDistanceWalkedCm(player.getStatistic(Statistic.WALK_ONE_CM));
        progress.setPlayTicks(player.getStatistic(Statistic.PLAY_ONE_MINUTE));

        // Mined blocks
        for (Map.Entry<String, Material[]> entry : TRACKED_BLOCKS.entrySet()) {
            long total = 0;
            for (Material mat : entry.getValue()) {
                total += player.getStatistic(Statistic.MINE_BLOCK, mat);
            }
            progress.setMinedCount(entry.getKey(), total);
        }
    }

    /**
     * Sync every online player in bulk. Should be called on the main thread
     * right before a snapshot is taken.
     */
    public void syncAllOnlinePlayers(Iterable<? extends Player> onlinePlayers) {
        for (Player player : onlinePlayers) {
            syncOnlinePlayer(player);
        }
    }

    // ── Advancement recording ───────────────────────────────────────────

    /**
     * Record a completed advancement for a player.
     */
    public void recordAdvancement(UUID playerId, String playerName, String advancementKey) {
        PlayerProgress progress = getOrCreate(playerId, playerName);
        progress.markAdvancement(advancementKey);
    }

    /**
     * Hydrate a player's already completed advancements from Bukkit stats, filtering out recipes.
     */
    public void hydratePlayerAdvancements(Player player) {
        PlayerProgress progress = getOrCreate(player.getUniqueId(), player.getName());
        Iterator<Advancement> it = Bukkit.advancementIterator();
        while (it.hasNext()) {
            Advancement adv = it.next();
            String key = adv.getKey().toString();
            if (key.startsWith("minecraft:recipes/")) {
                continue;
            }
            if (player.getAdvancementProgress(adv).isDone()) {
                progress.markAdvancement(key);
            }
        }
    }

    // ── Snapshot ─────────────────────────────────────────────────────────

    /**
     * Return an immutable view of the current cache for export.
     */
    public Map<UUID, PlayerProgress> snapshot() {
        return Collections.unmodifiableMap(progressByPlayer);
    }

    public int trackedPlayers() {
        return progressByPlayer.size();
    }

    // ── Tracked blocks accessor (for Database layer) ────────────────────

    public static List<String> trackedBlockKeys() {
        return TRACKED_BLOCKS.keySet().stream().sorted().toList();
    }

    // ── Internal ────────────────────────────────────────────────────────

    private PlayerProgress getOrCreate(UUID playerId, String name) {
        return progressByPlayer.computeIfAbsent(playerId, ignored -> new PlayerProgress(name));
    }
}
