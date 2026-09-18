package com.spotplugins.customscoreboard;

import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.Statistic;
import org.bukkit.entity.Player;
import org.bukkit.plugin.RegisteredServiceProvider;
import net.milkbowl.vault.economy.Economy;

import java.lang.reflect.Method;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;

/**
 * Resolve os placeholders internos do plugin e, se disponiveis,
 * delega para Vault (economia), PlaceholderAPI e integrações opcionais.
 */
public class PlaceholderUtil {

    private static final DateTimeFormatter DATE_FORMAT = DateTimeFormatter.ofPattern("dd/MM/yyyy");

    private final ScoreboardPlugin plugin;
    private Economy economy;
    private boolean vaultChecked = false;

    public PlaceholderUtil(ScoreboardPlugin plugin) {
        this.plugin = plugin;
    }

    /**
     * Aplica cor (&) + todos os placeholders internos + Vault + PlaceholderAPI.
     */
    public String apply(Player player, String rawLine) {
        String line = rawLine;

        line = line.replace("%player%", player.getName());
        line = line.replace("%world%", player.getWorld().getName());
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

        if (plugin.getConfig().getBoolean("use-vault", true)) {
            line = line.replace("%coins%", formatCoins(getBalance(player)));
        }

        // Integração nativa com ClanPlus:
        // %clans_name% passa a mostrar a TAG do clan.
        // Sem clan, o valor exibido é "Nenhum".
        line = line.replace("%clans_name%", getClanTag(player));

        if (plugin.getConfig().getBoolean("use-placeholderapi", true)
                && Bukkit.getPluginManager().isPluginEnabled("PlaceholderAPI")) {
            line = applyPlaceholderApi(player, line);
        }

        return ChatColor.translateAlternateColorCodes('&', line);
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
            // ClanPlus não está disponível ou sua API não pôde ser acessada.
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

    private double getBalance(Player player) {
        Economy eco = getEconomy();
        if (eco == null) {
            return 0.0;
        }
        try {
            return eco.getBalance(player);
        } catch (Exception e) {
            return 0.0;
        }
    }

    private String formatCoins(double amount) {
        if (amount >= 1_000_000_000d) {
            return String.format("%.1fB", amount / 1_000_000_000d);
        } else if (amount >= 1_000_000d) {
            return String.format("%.1fM", amount / 1_000_000d);
        } else if (amount >= 1_000d) {
            return String.format("%.1fK", amount / 1_000d);
        }
        return String.format("%.0f", amount);
    }

    private Economy getEconomy() {
        if (vaultChecked) {
            return economy;
        }
        vaultChecked = true;
        try {
            if (Bukkit.getPluginManager().getPlugin("Vault") == null) {
                return null;
            }
            RegisteredServiceProvider<Economy> provider =
                    Bukkit.getServicesManager().getRegistration(Economy.class);
            if (provider != null) {
                economy = provider.getProvider();
            }
        } catch (Throwable ignored) {
            // Vault não instalado / classe indisponível: seguimos sem economia
        }
        return economy;
    }

    private String applyPlaceholderApi(Player player, String line) {
        try {
            return me.clip.placeholderapi.PlaceholderAPI.setPlaceholders(player, line);
        } catch (Throwable ignored) {
            // PlaceholderAPI não instalado / classe indisponível: retorna a linha sem alteração
            return line;
        }
    }
}
