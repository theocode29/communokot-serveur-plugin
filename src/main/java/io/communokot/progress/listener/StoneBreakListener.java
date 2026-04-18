package io.communokot.progress.listener;

import io.communokot.progress.service.ProgressService;
import org.bukkit.Material;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockBreakEvent;

public final class StoneBreakListener implements Listener {
    private final ProgressService progressService;

    public StoneBreakListener(ProgressService progressService) {
        this.progressService = progressService;
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onBlockBreak(BlockBreakEvent event) {
        if (event.getBlock().getType() != Material.STONE) {
            return;
        }

        progressService.incrementStoneBreak(event.getPlayer().getUniqueId());
    }
}
