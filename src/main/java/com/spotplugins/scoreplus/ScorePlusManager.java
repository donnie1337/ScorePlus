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

    public void reload() {
        titleFrame = 0;
        tickCounter = 0;
        titleAnimationStartMillis = System.currentTimeMillis();
        renderedTitles.clear();
        renderedLines.clear();

        for (Player player : plugin.getServer().getOnlinePlayers()) {
            if (isEnabledFor(player)) {
                update(player);
            }
        }
    }

    public void tickTitleAnimation() {
        if (!plugin.getConfig().getBoolean("title-animation-enabled", false)) return;
        if (titleAnimationStartMillis == 0L) titleAnimationStartMillis = System.currentTimeMillis();
        for (Player player : plugin.getServer().getOnlinePlayers()) {
            if (isEnabledFor(player)) updateTitle(player);
        }
    }

    private void updateTitle(Player player) {
        Sidebar sidebar = sidebars.get(player.getUniqueId());
        if (sidebar == null || sidebar.closed()) return;
        String title = currentTitle();
        String previousTitle = renderedTitles.get(player.getUniqueId());
        if (!title.equals(previousTitle)) {
            sidebar.title(legacy.deserialize(title));
            renderedTitles.put(player.getUniqueId(), title);
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

        // Animação suave: cada letra faz uma transição gradual verde -> branco -> verde.
        // Sequência: S U R V I V A L A V I V R U S.
        // Depois, o título inteiro faz 2 pulsos suaves em branco.
        long interval = Math.max(1L, plugin.getConfig().getLong("title-animation-interval-seconds", 10L)) * 1000L;
        long pulse = Math.max(200L, plugin.getConfig().getLong("title-animation-white-ms", 500L));
        long normal = Math.max(100L, plugin.getConfig().getLong("title-animation-normal-ms", 500L));
        int fullBlinks = 2;

        int[] sequence = {0, 1, 2, 3, 4, 5, 6, 7, 6, 5, 4, 3, 2, 1, 0};
        long letterDuration = pulse + normal;
        long lettersDuration = sequence.length * letterDuration;
        long fullPulseDuration = fullBlinks * letterDuration;
        long cycle = interval + lettersDuration + fullPulseDuration;

        long phase = Math.floorMod(System.currentTimeMillis() - titleAnimationStartMillis, cycle);
        if (phase < interval) {
            return title;
        }

        long animationPhase = phase - interval;
        if (animationPhase < lettersDuration) {
            int sequenceIndex = (int) (animationPhase / letterDuration);
            int characterIndex = sequence[sequenceIndex];
            long offset = animationPhase % letterDuration;

            // Smoothstep para evitar qualquer troca brusca de cor.
            double progress;
            if (offset < pulse) {
                progress = smoothStep((double) offset / pulse);
            } else {
                progress = 1.0 - smoothStep((double) (offset - pulse) / normal);
            }

            return colorSingleTitleCharacterSmooth(title, characterIndex, progress);
        }

        long fullPhase = animationPhase - lettersDuration;
        long fullSlot = letterDuration;
        long fullIndex = fullPhase / fullSlot;
        long fullOffset = fullPhase % fullSlot;

        if (fullIndex < fullBlinks) {
            double progress;
            if (fullOffset < pulse) {
                progress = smoothStep((double) fullOffset / pulse);
            } else {
                progress = 1.0 - smoothStep((double) (fullOffset - pulse) / normal);
            }
            return colorWholeTitleSmooth(title, progress);
        }

        return title;
    }

    private double smoothStep(double value) {
        value = Math.max(0.0, Math.min(1.0, value));
        return value * value * (3.0 - 2.0 * value);
    }

    private String colorSingleTitleCharacterSmooth(String title, int characterIndex, double progress) {
        String targetWord = "SURVIVAL";
        String upperTitle = title.toUpperCase(java.util.Locale.ROOT);
        int wordStart = upperTitle.indexOf(targetWord);
        if (wordStart < 0) {
            return title;
        }

        // O efeito é aplicado somente às letras de SURVIVAL.
        // Tudo que vem antes (inclusive o ❄) mantém exatamente a cor configurada.
        StringBuilder rendered = new StringBuilder(title.length() + 32);
        int survivalIndex = -1;
        boolean insideSurvival = false;
        boolean bold = false;

        for (int i = 0; i < title.length(); i++) {
            char c = title.charAt(i);

            if (c == '&' && i + 1 < title.length()) {
                char code = title.charAt(i + 1);
                if ("klmnorKLMNOR".indexOf(code) >= 0) {
                    if (code == 'l' || code == 'L') {
                        bold = true;
                    } else if (code == 'r' || code == 'R') {
                        bold = false;
                    }
                    rendered.append('&').append(code);
                } else {
                    rendered.append('&').append(code);
                }
                i++;
                continue;
            }

            boolean isSurvivalCharacter = i >= wordStart && i < wordStart + targetWord.length();
            if (isSurvivalCharacter) {
                survivalIndex++;
                double letterProgress = survivalIndex == characterIndex ? progress : 0.0;
                rendered.append(toHexColor(interpolateColor(
                        85, 255, 85,
                        255, 255, 255,
                        letterProgress
                )));
                if (bold) {
                    rendered.append("&l");
                }
            }

            rendered.append(c);

            if (isSurvivalCharacter && bold) {
                // O &l acima já foi aplicado antes da letra.
            }

            if (isSurvivalCharacter && survivalIndex == targetWord.length() - 1) {
                insideSurvival = false;
            } else if (isSurvivalCharacter) {
                insideSurvival = true;
            }
        }

        return rendered.toString();
    }

    private String colorWholeTitleSmooth(String title, double progress) {
        String targetWord = "SURVIVAL";
        String upperTitle = title.toUpperCase(java.util.Locale.ROOT);
        int wordStart = upperTitle.indexOf(targetWord);
        if (wordStart < 0) {
            return title;
        }

        // Somente SURVIVAL recebe o efeito. O ❄ e os espaços permanecem intactos.
        StringBuilder rendered = new StringBuilder(title.length() + 32);
        boolean bold = false;

        for (int i = 0; i < title.length(); i++) {
            char c = title.charAt(i);

            if (c == '&' && i + 1 < title.length()) {
                char code = title.charAt(i + 1);
                if ("klmnorKLMNOR".indexOf(code) >= 0) {
                    if (code == 'l' || code == 'L') {
                        bold = true;
                    } else if (code == 'r' || code == 'R') {
                        bold = false;
                    }
                    rendered.append('&').append(code);
                } else {
                    rendered.append('&').append(code);
                }
                i++;
                continue;
            }

            boolean isSurvivalCharacter = i >= wordStart && i < wordStart + targetWord.length();
            if (isSurvivalCharacter) {
                rendered.append(toHexColor(interpolateColor(
                        85, 255, 85,
                        255, 255, 255,
                        progress
                )));
                if (bold) {
                    rendered.append("&l");
                }
            }

            rendered.append(c);
        }

        return rendered.toString();
    }

    private int[] interpolateColor(int r1, int g1, int b1, int r2, int g2, int b2, double progress) {
        return new int[] {
                (int) Math.round(r1 + (r2 - r1) * progress),
                (int) Math.round(g1 + (g2 - g1) * progress),
                (int) Math.round(b1 + (b2 - b1) * progress)
        };
    }

    private String toHexColor(int[] rgb) {
        return String.format("&x&%x&%x&%x&%x&%x&%x",
                (rgb[0] >> 4) & 0xF, rgb[0] & 0xF,
                (rgb[1] >> 4) & 0xF, rgb[1] & 0xF,
                (rgb[2] >> 4) & 0xF, rgb[2] & 0xF);
    }

    private String truncate(String text, int max) {
        return text.length() <= max ? text : text.substring(0, max);
    }
}
