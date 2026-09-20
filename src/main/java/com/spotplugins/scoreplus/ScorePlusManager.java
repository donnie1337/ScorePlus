package com.spotplugins.scoreplus;

import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer;
import net.megavex.scoreboardlibrary.api.ScoreboardLibrary;
import net.megavex.scoreboardlibrary.api.exception.NoPacketAdapterAvailableException;
import net.megavex.scoreboardlibrary.api.sidebar.Sidebar;
import org.bukkit.entity.Player;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public class ScorePlusManager {
    private static final int MAX_LINES = 15;
    private final ScorePlus plugin;
    private final PlaceholderUtil placeholders;
    private final Set<UUID> disabled = new HashSet<>();
    private final Map<UUID, Sidebar> sidebars = new ConcurrentHashMap<>();
    private final Map<UUID, List<String>> renderedLines = new ConcurrentHashMap<>();
    private final Map<UUID, String> renderedTitles = new ConcurrentHashMap<>();
    private final LegacyComponentSerializer legacy = LegacyComponentSerializer.legacyAmpersand();
    private ScoreboardLibrary scoreboardLibrary;
    private int titleFrame;
    private int tickCounter;
    private long titleAnimationStartMillis;

    public ScorePlusManager(ScorePlus plugin) {
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
        if (titleAnimationStartMillis == 0L) {
            titleAnimationStartMillis = System.currentTimeMillis();
        }

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
        renderedLines.put(id, new ArrayList<>());
        renderedTitles.remove(id);
        updateBoardContents(player, sidebar);
        sidebar.addPlayer(player);
    }

    private void updateBoardContents(Player player, Sidebar sidebar) {
        UUID id = player.getUniqueId();

        String title = currentTitle();
        String previousTitle = renderedTitles.get(id);
        if (!title.equals(previousTitle)) {
            sidebar.title(legacy.deserialize(title));
            renderedTitles.put(id, title);
        }

        List<String> configuredLines = getConfiguredLines();
        List<String> previousLines = renderedLines.computeIfAbsent(id, ignored -> new ArrayList<>());

        for (int i = 0; i < MAX_LINES; i++) {
            String rendered = i < configuredLines.size()
                    ? renderLine(player, configuredLines.get(i))
                    : null;

            String previous = i < previousLines.size() ? previousLines.get(i) : null;
            if (java.util.Objects.equals(rendered, previous)) {
                continue;
            }

            sidebar.line(i, rendered == null ? null : legacy.deserialize(rendered));

            while (previousLines.size() <= i) {
                previousLines.add(null);
            }
            previousLines.set(i, rendered);
        }
    }

    private void removeSidebar(Player player) {
        UUID id = player.getUniqueId();
        Sidebar sidebar = sidebars.remove(id);
        renderedLines.remove(id);
        renderedTitles.remove(id);

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
        String title = frames.isEmpty() ? "&a&lSURVIVAL" : frames.get(titleFrame % frames.size());
        title = truncate(title, 128);

        if (!plugin.getConfig().getBoolean("title-animation-enabled", false)) {
            return title;
        }

        // Mesmo ciclo visual do DEV do CargoPlus: aguarda o intervalo e então
        // executa 3 piscadas completas em branco antes de voltar ao título normal.
        long interval = Math.max(1L, plugin.getConfig().getLong("title-animation-interval-seconds", 10L)) * 1000L;
        long white = Math.max(50L, plugin.getConfig().getLong("title-animation-white-ms", 250L));
        long normal = Math.max(50L, plugin.getConfig().getLong("title-animation-normal-ms", 250L));
        int blinks = Math.max(1, Math.min(10, plugin.getConfig().getInt("title-animation-blinks", 3)));
        long animationDuration = blinks * (white + normal);
        long cycle = interval + animationDuration;
        long phase = Math.floorMod(System.currentTimeMillis() - titleAnimationStartMillis, cycle);

        if (phase < interval) {
            return title;
        }

        long blinkPhase = phase - interval;
        long blinkSlot = white + normal;
        if ((blinkPhase / blinkSlot) < blinks && (blinkPhase % blinkSlot) < white) {
            return makeTitleWhite(title);
        }

        return title;
    }

    private String makeTitleWhite(String title) {
        StringBuilder white = new StringBuilder("&f");
        boolean formatting = false;
        for (int i = 0; i < title.length(); i++) {
            char c = title.charAt(i);
            if (c == '&' && i + 1 < title.length()) {
                char code = title.charAt(i + 1);
                if ("klmnorKLMNOR".indexOf(code) >= 0) {
                    white.append('&').append(code);
                }
                i++;
                continue;
            }
            white.append(c);
        }
        return white.toString();
    }

    private String truncate(String text, int max) {
        return text.length() <= max ? text : text.substring(0, max);
    }
}
