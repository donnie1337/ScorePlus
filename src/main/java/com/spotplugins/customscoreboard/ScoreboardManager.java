package com.spotplugins.customscoreboard;

import org.bukkit.ChatColor;
import org.bukkit.entity.Player;
import org.bukkit.scoreboard.DisplaySlot;
import org.bukkit.scoreboard.Objective;
import org.bukkit.scoreboard.Scoreboard;
import org.bukkit.scoreboard.Team;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;

/**
 * Cria e atualiza a scoreboard lateral de cada jogador com base no config.yml.
 *
 * Técnica usada: um Team por linha, com uma "entry" invisível e única
 * (combinação de códigos de cor) associada àquele Team. Isso permite ter
 * linhas repetidas/em branco sem conflito, e o texto real fica no prefixo
 * do Team (o que também evita o limite de 16 caracteres de nomes de jogador
 * fictícios usado em plugins antigos).
 */
public class ScoreboardManager {

    private static final String OBJECTIVE_ID = "csb_main";
    private static final int MAX_LINES = 15; // limite de cores únicas combináveis com segurança

    private final ScoreboardPlugin plugin;
    private final PlaceholderUtil placeholders;
    private final Set<UUID> disabled = new HashSet<>();

    private int titleFrame = 0;
    private int tickCounter = 0;

    public ScoreboardManager(ScoreboardPlugin plugin) {
        this.plugin = plugin;
        this.placeholders = new PlaceholderUtil(plugin);
    }

    public boolean isEnabledFor(Player player) {
        return !disabled.contains(player.getUniqueId());
    }

    public void toggle(Player player) {
        UUID id = player.getUniqueId();
        if (disabled.contains(id)) {
            disabled.remove(id);
            update(player);
        } else {
            disabled.add(id);
            player.setScoreboard(org.bukkit.Bukkit.getScoreboardManager().getMainScoreboard());
        }
    }

    /** Chamado periodicamente pela tarefa em ScoreboardPlugin. */
    public void tick() {
        tickCounter++;
        int speed = Math.max(1, plugin.getConfig().getInt("title-animation-speed", 4));
        List<String> frames = plugin.getConfig().getStringList("scoreboard.title-frames");
        if (tickCounter % speed == 0 && !frames.isEmpty()) {
            titleFrame = (titleFrame + 1) % frames.size();
        }

        for (Player player : plugin.getServer().getOnlinePlayers()) {
            if (isEnabledFor(player)) {
                update(player);
            }
        }
    }

    /** (Re)constrói a scoreboard de um jogador do zero. Usado no join e no /scoreboard reload. */
    public void update(Player player) {
        if (!isEnabledFor(player)) {
            return;
        }

        Scoreboard board = org.bukkit.Bukkit.getScoreboardManager().getNewScoreboard();
        Objective objective = board.registerNewObjective(OBJECTIVE_ID, "dummy", currentTitle());
        objective.setDisplaySlot(DisplaySlot.SIDEBAR);

        List<String> rawLines = plugin.getConfig().getStringList("scoreboard.lines");
        List<String> lines = new ArrayList<>(rawLines);
        if (lines.size() > MAX_LINES) {
            lines = lines.subList(0, MAX_LINES);
        }

        int size = lines.size();
        for (int i = 0; i < size; i++) {
            String rendered = placeholders.apply(player, lines.get(i));
            String entry = uniqueInvisibleEntry(i);

            Team team = board.getTeam("csb_line_" + i);
            if (team == null) {
                team = board.registerNewTeam("csb_line_" + i);
            }
            if (!team.hasEntry(entry)) {
                team.addEntry(entry);
            }
            team.setPrefix(truncate(rendered, 64));

            // pontuação maior = aparece mais acima na sidebar
            objective.getScore(entry).setScore(size - i);
        }

        player.setScoreboard(board);
    }

    private String currentTitle() {
        List<String> frames = plugin.getConfig().getStringList("scoreboard.title-frames");
        if (frames.isEmpty()) {
            return ChatColor.translateAlternateColorCodes('&', "&a&lSURVIVAL");
        }
        String frame = frames.get(titleFrame % frames.size());
        return truncate(ChatColor.translateAlternateColorCodes('&', frame), 128);
    }

    private String truncate(String text, int max) {
        return text.length() <= max ? text : text.substring(0, max);
    }

    /**
     * Gera uma "entry" invisível e única por índice de linha, usando
     * combinações de ChatColor + RESET. Suporta até 15 linhas distintas.
     */
    private String uniqueInvisibleEntry(int index) {
        ChatColor[] colors = ChatColor.values();
        ChatColor color = colors[index % colors.length];
        return color.toString() + ChatColor.RESET;
    }
}
