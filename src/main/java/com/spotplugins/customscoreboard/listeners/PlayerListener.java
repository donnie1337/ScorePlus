package com.spotplugins.customscoreboard.listeners;

import com.spotplugins.customscoreboard.ScoreboardPlugin;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerMoveEvent;

import java.util.HashSet;
import java.util.Set;
import java.util.UUID;

public class PlayerListener implements Listener {

    private final ScoreboardPlugin plugin;
    private final Set<UUID> pendingRestore = new HashSet<>();

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
        // A restauração é agendada para o próximo tick para executar depois de
        // outros plugins que possam trocar a scoreboard durante o mesmo evento.
        scheduleRestore(player);
    }

    private void scheduleRestore(Player player) {
        UUID id = player.getUniqueId();
        if (!pendingRestore.add(id)) {
            return;
        }

        plugin.getServer().getScheduler().runTask(plugin, () -> {
            pendingRestore.remove(id);
            if (player.isOnline()) {
                plugin.getScoreboardManager().ensureAssigned(player);
            }
        });
    }

    private void scheduleAssignment(Player player, long delay) {
        plugin.getServer().getScheduler().runTaskLater(plugin, () -> {
            if (player.isOnline()) {
                plugin.getScoreboardManager().ensureAssigned(player);
            }
        }, delay);
    }
}
