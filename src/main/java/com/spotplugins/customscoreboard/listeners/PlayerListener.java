package com.spotplugins.customscoreboard.listeners;

import com.spotplugins.customscoreboard.ScoreboardPlugin;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerMoveEvent;

public class PlayerListener implements Listener {

    private final ScoreboardPlugin plugin;

    public PlayerListener(ScoreboardPlugin plugin) {
        this.plugin = plugin;
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onJoin(PlayerJoinEvent event) {
        scheduleAssignment(event.getPlayer(), 5L);
        scheduleAssignment(event.getPlayer(), 40L);
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onMove(PlayerMoveEvent event) {
        Player player = event.getPlayer();
        if (!player.isOnline()) {
            return;
        }

        // PlayerMoveEvent também ocorre apenas por rotação da câmera.
        // Só reagimos quando outro componente realmente substituiu a scoreboard.
        if (player.getScoreboard() != plugin.getScoreboardManager().getAssignedBoard(player)) {
            scheduleAssignment(player, 1L);
        }
    }

    private void scheduleAssignment(Player player, long delay) {
        plugin.getServer().getScheduler().runTaskLater(plugin, () -> {
            if (player.isOnline()) {
                plugin.getScoreboardManager().ensureAssigned(player);
            }
        }, delay);
    }
}
