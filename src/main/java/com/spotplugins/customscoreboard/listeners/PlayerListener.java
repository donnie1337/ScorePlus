package com.spotplugins.customscoreboard.listeners;

import com.spotplugins.customscoreboard.ScoreboardPlugin;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerMoveEvent;
import org.bukkit.event.player.PlayerQuitEvent;

public class PlayerListener implements Listener {

    private final ScoreboardPlugin plugin;

    public PlayerListener(ScoreboardPlugin plugin) {
        this.plugin = plugin;
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onJoin(PlayerJoinEvent event) {
        // Atribui uma única vez depois que os demais handlers de entrada terminarem.
        scheduleUpdate(event.getPlayer(), 1L);
    }

    /**
     * Alguns sistemas de câmera/movimento podem substituir a scoreboard durante
     * atualizações do jogador. Revalida em qualquer movimento ou rotação.
     * O ScorePlus só chama setScoreboard() se a referência realmente mudou.
     */
    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = false)
    public void onMove(PlayerMoveEvent event) {
        plugin.getScoreboardManager().ensureAssigned(event.getPlayer());
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent event) {
        // Remove a referência da scoreboard privada ao sair.
        plugin.getScoreboardManager().remove(event.getPlayer());
    }

    private void scheduleUpdate(Player player, long delay) {
        plugin.getServer().getScheduler().runTaskLater(plugin, () -> {
            if (player.isOnline()) {
                plugin.getScoreboardManager().update(player);
            }
        }, delay);
    }
}
