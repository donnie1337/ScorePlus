package com.spotplugins.customscoreboard;

import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.entity.Player;
import org.bukkit.scoreboard.DisplaySlot;
import org.bukkit.scoreboard.Objective;
import org.bukkit.scoreboard.Scoreboard;
import org.bukkit.scoreboard.Team;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * Cria e atualiza a scoreboard lateral de cada jogador com base no config.yml.
 *
 * A scoreboard de cada jogador é mantida e somente os elementos que realmente
 * mudaram são enviados ao cliente. Isso evita flicker e excesso de pacotes.
 */
public class ScoreboardManager {

    private static final String OBJECTIVE_ID = "csb_main";
    private static final int MAX_LINES = 15;

    private final ScoreboardPlugin plugin;
    private final PlaceholderUtil placeholders;
    private final Set<UUID> disabled = new HashSet<>();
    private final Map<UUID, Scoreboard> boards = new HashMap<>();

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
            boards.remove(id);
            player.setScoreboard(Bukkit.getScoreboardManager().getMainScoreboard());
        }
    }

    /** Atualiza o conteúdo quando o modo dinâmico está habilitado. */
    public void tick() {
        tickCounter++;

        if (plugin.getConfig().getBoolean("title-animation-enabled", false)) {
            int speed = Math.max(1, plugin.getConfig().getInt("title-animation-speed", 4));
            List<String> frames = plugin.getConfig().getStringList("scoreboard.title-frames");
            if (tickCounter % speed == 0 && !frames.isEmpty()) {
                titleFrame = (titleFrame + 1) % frames.size();
            }
        }

        for (Player player : plugin.getServer().getOnlinePlayers()) {
            if (isEnabledFor(player)) {
                update(player);
            }
        }
    }

    /**
     * Reaplica somente a referência da scoreboard caso outro plugin a substitua.
     * Nenhuma linha ou objetivo é recriado, então isso não causa flicker.
     */
    public void ensureAssigned(Player player) {
        if (!isEnabledFor(player)) {
            return;
        }

        Scoreboard board = boards.get(player.getUniqueId());
        if (board != null && player.getScoreboard() != board) {
            player.setScoreboard(board);
        }
    }

    /** Atualiza a scoreboard existente ou cria uma apenas na primeira vez. */
    public void update(Player player) {
        if (!isEnabledFor(player)) {
            return;
        }

        UUID id = player.getUniqueId();
        Scoreboard board = boards.get(id);
        if (board == null) {
            board = Bukkit.getScoreboardManager().getNewScoreboard();
            boards.put(id, board);
        }

        Objective objective = board.getObjective(OBJECTIVE_ID);
        String title = currentTitle();
        if (objective == null) {
            objective = board.registerNewObjective(OBJECTIVE_ID, "dummy", title);
            objective.setDisplaySlot(DisplaySlot.SIDEBAR);
        } else if (plugin.getConfig().getBoolean("title-animation-enabled", false)
                && !title.equals(objective.getDisplayName())) {
            objective.setDisplayName(title);
        }

        List<String> rawLines = plugin.getConfig().getStringList("scoreboard.lines");
        List<String> lines = new ArrayList<>(rawLines);
        if (lines.size() > MAX_LINES) {
            lines = lines.subList(0, MAX_LINES);
        }

        removeUnusedLines(board, lines.size());

        int size = lines.size();
        for (int i = 0; i < size; i++) {
            String rendered = truncate(placeholders.apply(player, lines.get(i)), 64);
            String entry = uniqueInvisibleEntry(i);

            Team team = board.getTeam("csb_line_" + i);
            if (team == null) {
                team = board.registerNewTeam("csb_line_" + i);
                team.addEntry(entry);
                team.setPrefix(rendered);
            } else {
                if (!team.hasEntry(entry)) {
                    team.addEntry(entry);
                }
                if (!rendered.equals(team.getPrefix())) {
                    team.setPrefix(rendered);
                }
            }

            int scoreValue = size - i;
            if (!objective.getScore(entry).isScoreSet() || objective.getScore(entry).getScore() != scoreValue) {
                objective.getScore(entry).setScore(scoreValue);
            }
        }

        if (player.getScoreboard() != board) {
            player.setScoreboard(board);
        }
    }

    private void removeUnusedLines(Scoreboard board, int lineCount) {
        for (int i = lineCount; i < MAX_LINES; i++) {
            Team team = board.getTeam("csb_line_" + i);
            if (team == null) {
                continue;
            }

            for (String entry : new HashSet<>(team.getEntries())) {
                board.resetScores(entry);
                team.removeEntry(entry);
            }
            team.unregister();
        }
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

    /** Gera uma entry invisível e única por índice de linha. */
    private String uniqueInvisibleEntry(int index) {
        ChatColor[] colors = ChatColor.values();
        ChatColor color = colors[index % colors.length];
        return color.toString() + ChatColor.RESET;
    }
}
