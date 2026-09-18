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

        if (disabled.remove(id)) {
            createAndAssignBoard(player);
            return;
        }

        disabled.add(id);
        boards.remove(id);
        player.setScoreboard(Bukkit.getScoreboardManager().getMainScoreboard());
    }

    public Scoreboard getAssignedBoard(Player player) {
        return boards.get(player.getUniqueId());
    }

    /**
     * Reatribui somente quando a scoreboard foi realmente substituída.
     * Não altera a scoreboard enquanto ela já estiver correta.
     */
    public void ensureAssigned(Player player) {
        if (!isEnabledFor(player) || !player.isOnline()) {
            return;
        }

        Scoreboard board = boards.get(player.getUniqueId());
        if (board == null) {
            createAndAssignBoard(player);
            return;
        }

        if (player.getScoreboard() != board) {
            player.setScoreboard(board);
        }
    }

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

    public void update(Player player) {
        if (!isEnabledFor(player) || !player.isOnline()) {
            return;
        }

        Scoreboard board = boards.get(player.getUniqueId());
        if (board == null) {
            createAndAssignBoard(player);
            return;
        }

        // Atualiza somente o conteúdo. Não chama setScoreboard().
        updateBoardContents(player, board);
    }

    public void remove(Player player) {
        UUID id = player.getUniqueId();
        boards.remove(id);
        disabled.remove(id);
    }

    private void createAndAssignBoard(Player player) {
        if (!isEnabledFor(player)) {
            return;
        }

        UUID id = player.getUniqueId();
        Scoreboard board = Bukkit.getScoreboardManager().getNewScoreboard();
        boards.put(id, board);

        buildBoard(player, board);
        player.setScoreboard(board);
    }

    private void buildBoard(Player player, Scoreboard board) {
        Objective objective = board.registerNewObjective(
                OBJECTIVE_ID,
                "dummy",
                currentTitle()
        );
        objective.setDisplaySlot(DisplaySlot.SIDEBAR);

        List<String> lines = getConfiguredLines();

        for (int i = 0; i < lines.size(); i++) {
            String entry = uniqueInvisibleEntry(i);
            Team team = board.registerNewTeam("csb_line_" + i);
            team.addEntry(entry);
            team.setPrefix(renderLine(player, lines.get(i)));
            objective.getScore(entry).setScore(lines.size() - i);
        }
    }

    private void updateBoardContents(Player player, Scoreboard board) {
        Objective objective = board.getObjective(OBJECTIVE_ID);
        if (objective == null) {
            boards.remove(player.getUniqueId());
            createAndAssignBoard(player);
            return;
        }

        String title = currentTitle();
        if (plugin.getConfig().getBoolean("title-animation-enabled", false)
                && !title.equals(objective.getDisplayName())) {
            objective.setDisplayName(title);
        }

        List<String> lines = getConfiguredLines();

        for (int i = 0; i < lines.size(); i++) {
            String entry = uniqueInvisibleEntry(i);
            Team team = board.getTeam("csb_line_" + i);

            if (team == null) {
                team = board.registerNewTeam("csb_line_" + i);
                team.addEntry(entry);
                team.setPrefix(renderLine(player, lines.get(i)));
                objective.getScore(entry).setScore(lines.size() - i);
                continue;
            }

            if (!team.hasEntry(entry)) {
                team.addEntry(entry);
            }

            String rendered = renderLine(player, lines.get(i));
            if (!rendered.equals(team.getPrefix())) {
                team.setPrefix(rendered);
            }
        }

        removeUnusedLines(board, lines.size());
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

    private List<String> getConfiguredLines() {
        List<String> lines = new ArrayList<>(
                plugin.getConfig().getStringList("scoreboard.lines")
        );

        if (lines.size() > MAX_LINES) {
            return new ArrayList<>(lines.subList(0, MAX_LINES));
        }

        return lines;
    }

    private String renderLine(Player player, String line) {
        return truncate(placeholders.apply(player, line), 64);
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

    private String uniqueInvisibleEntry(int index) {
        ChatColor[] colors = ChatColor.values();
        ChatColor color = colors[index % colors.length];
        return color.toString() + ChatColor.RESET;
    }
}
