package com.spotplugins.customscoreboard.listeners;

import com.spotplugins.customscoreboard.ScoreboardPlugin;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;

public class PlayerListener implements Listener {

    private final ScoreboardPlugin plugin;

    public PlayerListener(ScoreboardPlugin plugin) {
        this.plugin = plugin;
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onJoin(PlayerJoinEvent event) {
        // Atribui após o join e faz uma segunda verificação depois que os demais
        // plugins terminarem suas rotinas de entrada. Não existe reatribuição por movimento.
        scheduleAssignment(event, 5L);
        scheduleAssignment(event, 40L);
    }

    private void scheduleAssignment(PlayerJoinEvent event, long delay) {
        plugin.getServer().getScheduler().runTaskLater(plugin, () -> {
            if (event.getPlayer().isOnline()) {
                plugin.getScoreboardManager().update(event.getPlayer());
            }
        }, delay);
    }
}
