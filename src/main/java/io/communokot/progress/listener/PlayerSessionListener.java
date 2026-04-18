package io.communokot.progress.listener;

import io.communokot.progress.service.ProgressService;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerAdvancementDoneEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;

/**
 * Listens to player session events (join / quit) and advancement completions.
 *
 * <ul>
 *   <li><strong>Join</strong>: hydrates the player's cache entry from live Bukkit stats.</li>
 *   <li><strong>Quit</strong>: final stat sync so the snapshot stays accurate while offline.</li>
 *   <li><strong>Advancement</strong>: records newly completed advancements immediately.</li>
 * </ul>
 */
public final class PlayerSessionListener implements Listener {
    private final ProgressService progressService;

    public PlayerSessionListener(ProgressService progressService) {
        this.progressService = progressService;
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onPlayerJoin(PlayerJoinEvent event) {
        progressService.syncOnlinePlayer(event.getPlayer());
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onPlayerQuit(PlayerQuitEvent event) {
        progressService.syncOnlinePlayer(event.getPlayer());
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onAdvancementDone(PlayerAdvancementDoneEvent event) {
        String key = event.getAdvancement().getKey().toString();
        progressService.recordAdvancement(
            event.getPlayer().getUniqueId(),
            event.getPlayer().getName(),
            key
        );
    }
}
