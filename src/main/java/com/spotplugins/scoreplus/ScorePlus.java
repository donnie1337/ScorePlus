package com.spotplugins.scoreplus;

import com.spotplugins.scoreplus.listeners.PlayerListener;
import org.bukkit.ChatColor;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.scheduler.BukkitTask;

public class ScorePlus extends JavaPlugin {
    private ScorePlusManager scoreboardManager;
    private BukkitTask updateTask;

    @Override
    public void onEnable() {
        saveDefaultConfig();

        scoreboardManager = new ScorePlusManager(this);
        scoreboardManager.initialize();

        getServer().getPluginManager().registerEvents(new PlayerListener(this), this);
        startTask();

        getLogger().info("ScorePlus ativado.");
    }

    @Override
    public void onDisable() {
        if (updateTask != null) {
            updateTask.cancel();
        }
        if (scoreboardManager != null) {
            scoreboardManager.close();
        }
    }

    private void startTask() {
        if (!getConfig().getBoolean("dynamic-update-enabled", false)) {
            updateTask = null;
            return;
        }

        long interval = Math.max(1L, getConfig().getLong("update-interval-ticks", 20L));
        updateTask = getServer().getScheduler().runTaskTimer(
                this, scoreboardManager::tick, interval, interval
        );
    }

    public ScorePlusManager getScorePlusManager() {
        return scoreboardManager;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!command.getName().equalsIgnoreCase("scoreboard")) {
            return false;
        }

        if (args.length == 0) {
            sender.sendMessage(ChatColor.YELLOW + "Uso: /" + label + " <reload|on|off>");
            return true;
        }

        switch (args[0].toLowerCase()) {
            case "reload" -> {
                if (!sender.hasPermission("scoreplus.admin")) {
                    sender.sendMessage(ChatColor.RED + "Você não tem permissão para isso.");
                    return true;
                }
                reloadConfig();
                if (updateTask != null) {
                    updateTask.cancel();
                }
                startTask();
                sender.sendMessage(ChatColor.GREEN + "Configuração da scoreboard recarregada.");
            }
            case "on", "off" -> {
                if (!(sender instanceof Player player)) {
                    sender.sendMessage(ChatColor.RED + "Apenas jogadores podem usar este comando.");
                    return true;
                }
                if (!sender.hasPermission("scoreplus.use")) {
                    sender.sendMessage(ChatColor.RED + "Você não tem permissão para isso.");
                    return true;
                }

                boolean wantsOn = args[0].equalsIgnoreCase("on");
                boolean currentlyOn = scoreboardManager.isEnabledFor(player);
                if (wantsOn != currentlyOn) {
                    scoreboardManager.toggle(player);
                }

                sender.sendMessage(ChatColor.GREEN + "Scoreboard " + (wantsOn ? "ativada." : "desativada."));
            }
            default -> sender.sendMessage(ChatColor.YELLOW + "Uso: /" + label + " <reload|on|off>");
        }

        return true;
    }
}
