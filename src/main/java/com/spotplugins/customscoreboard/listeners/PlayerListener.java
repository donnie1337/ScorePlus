package com.spotplugins.customscoreboard.listeners;

import com.spotplugins.customscoreboard.ScoreboardPlugin;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;

public class PlayerListener implements Listener {

    private final ScoreboardPlugin plugin;

    public PlayerListener(ScoreboardPlugin plugin) {
        this.plugin = plugin;
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onJoin(PlayerJoinEvent event) {
        // Atribui a scoreboard logo após o login, evitando o intervalo
        // visível que existia com o atraso anterior de 5 ticks.
        scheduleUpdate(event.getPlayer(), 1L);
    }

    private void scheduleUpdate(Player player, long delay) {
        plugin.getServer().getScheduler().runTaskLater(plugin, () -> {
            if (player.isOnline()) {
                plugin.getScoreboardManager().update(player);
            }
        }, delay);
    }
}
