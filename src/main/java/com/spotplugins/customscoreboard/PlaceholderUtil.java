package com.spotplugins.customscoreboard;

import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.Statistic;
import org.bukkit.entity.Player;

import java.lang.reflect.Method;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;

/**
 * Resolve placeholders internos do plugin, PlaceholderAPI e integrações opcionais.
 * O ScorePlus não depende de Vault.
 */
public class PlaceholderUtil {

    private static final DateTimeFormatter DATE_FORMAT = DateTimeFormatter.ofPattern("dd/MM/yyyy");

    private final ScoreboardPlugin plugin;

    public PlaceholderUtil(ScoreboardPlugin plugin) {
        this.plugin = plugin;
    }

    /**
     * Aplica cor (&) + placeholders internos + PlaceholderAPI.
     * Economia não é lida diretamente por Vault; %coins% pode ser fornecido
     * futuramente por PlaceholderAPI ou pela API própria do servidor.
     */
    public String apply(Player player, String rawLine) {
        String line = rawLine;

        line = line.replace("%player%", player.getName());
        line = line.replace("%world%", formatWorldName(player.getWorld().getName()));
        line = line.replace("%online%", String.valueOf(Bukkit.getOnlinePlayers().size()));
        line = line.replace("%maxonline%", String.valueOf(Bukkit.getMaxPlayers()));
        line = line.replace("%date%", LocalDate.now().format(DATE_FORMAT));

        int kills = safeStatistic(player, Statistic.PLAYER_KILLS);
        int deaths = safeStatistic(player, Statistic.DEATHS);
        line = line.replace("%kills%", String.valueOf(kills));
        line = line.replace("%deaths%", String.valueOf(deaths));

        double kdr = deaths == 0 ? kills : (double) kills / deaths;
        line = line.replace("%kdr%", String.format("%.2f", kdr));

        long playedTicks = safeStatistic(player, Statistic.PLAY_ONE_MINUTE);
        long hours = (playedTicks / 20L) / 3600L;
        line = line.replace("%playtime%", String.valueOf(hours));

        // Integração nativa com ClanPlus.
        line = line.replace("%clans_name%", getClanTag(player));

        if (plugin.getConfig().getBoolean("use-placeholderapi", true)
                && Bukkit.getPluginManager().isPluginEnabled("PlaceholderAPI")) {
            line = applyPlaceholderApi(player, line);
        }

        return ChatColor.translateAlternateColorCodes('&', line);
    }

    private String formatWorldName(String worldName) {
        if (worldName == null || worldName.isEmpty()) {
            return worldName;
        }

        return switch (worldName.toLowerCase(java.util.Locale.ROOT)) {
            case "world_nether" -> "Nether";
            case "world_the_end" -> "End";
            default -> Character.toUpperCase(worldName.charAt(0)) + worldName.substring(1);
        };
    }

    private String getClanTag(Player player) {
        try {
            if (!Bukkit.getPluginManager().isPluginEnabled("ClanPlus")) {
                return "Nenhum";
            }

            var clanPlugin = Bukkit.getPluginManager().getPlugin("ClanPlus");
            if (clanPlugin == null) {
                return "Nenhum";
            }

            Method method = clanPlugin.getClass().getMethod("getPlayerTag", Player.class);
            Object result = method.invoke(clanPlugin, player);

            if (result == null) {
                return "Nenhum";
            }

            String tag = String.valueOf(result).trim();
            return tag.isEmpty() ? "Nenhum" : tag;
        } catch (Throwable ignored) {
            return "Nenhum";
        }
    }

    private int safeStatistic(Player player, Statistic statistic) {
        try {
            return player.getStatistic(statistic);
        } catch (Exception e) {
            return 0;
        }
    }

    private String applyPlaceholderApi(Player player, String line) {
        try {
            return me.clip.placeholderapi.PlaceholderAPI.setPlaceholders(player, line);
        } catch (Throwable ignored) {
            return line;
        }
    }
}
