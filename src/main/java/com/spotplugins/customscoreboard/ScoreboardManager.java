package com.spotplugins.customscoreboard;

import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer;
import net.megavex.scoreboardlibrary.api.NoPacketAdapterAvailableException;
import net.megavex.scoreboardlibrary.api.ScoreboardLibrary;
import net.megavex.scoreboardlibrary.api.sidebar.Sidebar;
import org.bukkit.entity.Player;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public class ScoreboardManager {
    private static final int MAX_LINES = 15;
    private final ScoreboardPlugin plugin;
    private final PlaceholderUtil placeholders;
    private final Set<UUID> disabled = new HashSet<>();
    private final Map<UUID, Sidebar> sidebars = new ConcurrentHashMap<>();
    private final LegacyComponentSerializer legacy = LegacyComponentSerializer.legacyAmpersand();
    private ScoreboardLibrary scoreboardLibrary;
    private int titleFrame;
    private int tickCounter;

    public ScoreboardManager(ScoreboardPlugin plugin) {
        this.plugin = plugin;
        this.placeholders = new PlaceholderUtil(plugin);
    }

    public void initialize() {
        try {
            scoreboardLibrary = ScoreboardLibrary.loadScoreboardLibrary(plugin);
        } catch (NoPacketAdapterAvailableException exception) {
            throw new IllegalStateException("A versão do servidor não possui suporte de scoreboard por pacotes.", exception);
        }
    }

    public boolean isEnabledFor(Player player) {
        return !disabled.contains(player.getUniqueId());
    }

    public void toggle(Player player) {
        UUID id = player.getUniqueId();
        if (disabled.remove(id)) {
            createAndAssignBoard(player);
        } else {
            disabled.add(id);
            removeSidebar(player);
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

        Sidebar sidebar = sidebars.get(player.getUniqueId());
        if (sidebar == null || sidebar.closed()) {
            createAndAssignBoard(player);
            return;
        }

        updateBoardContents(player, sidebar);
    }

    public void remove(Player player) {
        UUID id = player.getUniqueId();
        removeSidebar(player);
        disabled.remove(id);
    }

    public void close() {
        for (Sidebar sidebar : sidebars.values()) {
            if (!sidebar.closed()) {
                sidebar.close();
            }
        }
        sidebars.clear();

        if (scoreboardLibrary != null) {
            scoreboardLibrary.close();
            scoreboardLibrary = null;
        }
    }

    private void createAndAssignBoard(Player player) {
        if (!isEnabledFor(player) || scoreboardLibrary == null) {
            return;
        }

        UUID id = player.getUniqueId();
        Sidebar old = sidebars.remove(id);
        if (old != null && !old.closed()) {
            old.removePlayer(player);
            old.close();
        }

        Sidebar sidebar = scoreboardLibrary.createSidebar();
        sidebars.put(id, sidebar);
        updateBoardContents(player, sidebar);
        sidebar.addPlayer(player);
    }

    private void updateBoardContents(Player player, Sidebar sidebar) {
        sidebar.title(legacy.deserialize(currentTitle()));

        List<String> lines = getConfiguredLines();
        for (int i = 0; i < MAX_LINES; i++) {
            if (i < lines.size()) {
                sidebar.line(i, legacy.deserialize(renderLine(player, lines.get(i))));
            } else {
                sidebar.line(i, null);
            }
        }
    }

    private void removeSidebar(Player player) {
        Sidebar sidebar = sidebars.remove(player.getUniqueId());
        if (sidebar == null || sidebar.closed()) {
            return;
        }

        sidebar.removePlayer(player);
        sidebar.close();
    }

    private List<String> getConfiguredLines() {
        List<String> lines = new ArrayList<>(plugin.getConfig().getStringList("scoreboard.lines"));
        if (lines.size() > MAX_LINES) {
            return new ArrayList<>(lines.subList(0, MAX_LINES));
        }
        return lines;
    }

    private String renderLine(Player player, String line) {
        return truncate(placeholders.apply(player, line), 128);
    }

    private String currentTitle() {
        List<String> frames = plugin.getConfig().getStringList("scoreboard.title-frames");
        if (frames.isEmpty()) {
            return "&a&lSURVIVAL";
        }
        return truncate(frames.get(titleFrame % frames.size()), 128);
    }

    private String truncate(String text, int max) {
        return text.length() <= max ? text : text.substring(0, max);
    }
}
