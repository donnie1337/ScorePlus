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
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class ScorePlusManager {
    private static final int MAX_LINES = 15;
    private final ScorePlus plugin;
    private final PlaceholderUtil placeholders;
    private final Set<UUID> disabled = new HashSet<>();
    private final Map<UUID, Sidebar> sidebars = new ConcurrentHashMap<>();
    private final Map<UUID, List<String>> renderedLines = new ConcurrentHashMap<>();
    private final Map<UUID, String> renderedTitles = new ConcurrentHashMap<>();
    private final LegacyComponentSerializer legacy = LegacyComponentSerializer.legacySection();
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
            if (isEnabledFor(player)) update(player);
        }
    }

    private void updateTitle(Player player) {
        Sidebar sidebar = sidebars.get(player.getUniqueId());
        if (sidebar == null || sidebar.closed()) return;
        String title = currentTitle();
        String previousTitle = renderedTitles.get(player.getUniqueId());
        if (!title.equals(previousTitle)) {
            sidebar.title(legacy.deserialize(toSectionCodes(title)));
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
            sidebar.title(legacy.deserialize(toSectionCodes(title)));
            renderedTitles.put(id, title);
        }

        List<String> configuredLines = getConfiguredLines();
        List<String> previousLines = renderedLines.computeIfAbsent(id, ignored -> new ArrayList<>());

        for (int i = 0; i < MAX_LINES; i++) {
            String rendered = i < configuredLines.size()
                    ? renderLine(player, configuredLines.get(i), i)
                    : null;

            String previous = i < previousLines.size() ? previousLines.get(i) : null;
            if (java.util.Objects.equals(rendered, previous)) {
                continue;
            }

            sidebar.line(i, rendered == null ? null : legacy.deserialize(toSectionCodes(rendered)));

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

    private String renderLine(Player player, String line, int lineIndex) {
        String rendered = applyGradients(placeholders.apply(player, line));
        if (stripLegacyFormatting(rendered).toUpperCase(java.util.Locale.ROOT).contains("SURVIVAL")) {
            rendered = applyConfiguredSurvivalEffect(rendered);
        }
        return rendered;
    }

    private String currentTitle() {
        List<String> frames = plugin.getConfig().getStringList("scoreboard.title-frames");
        String title = frames.isEmpty() ? "" : frames.get(0);
        return applyGradients(title);
    }

    private String applyConfiguredSurvivalEffect(String title) {
        if (!plugin.getConfig().getBoolean("title-animation-enabled", false)) {
            return title;
        }

        String configuredEffect = plugin.getConfig().getString("efeito-score", "alternar");
        if (configuredEffect == null || configuredEffect.isBlank()) {
            configuredEffect = "alternar";
        }

        String effect = configuredEffect.trim().toLowerCase(java.util.Locale.ROOT);
        String[] effects = {
                "onda-brilho",
                "pulso-central",
                "varredura",
                "respiracao",
                "arco-luz",
                "duas-ondas",
                "centro-explodindo",
                "eco",
                "neve"
        };

        long now = System.currentTimeMillis();
        long effectDuration = Math.max(1L,
                plugin.getConfig().getLong("efeito-score-duracao-segundos", 4L)) * 1000L;

        int effectIndex;
        if (effect.equals("alternar")) {
            long total = effectDuration * effects.length;
            long phase = Math.floorMod(now - titleAnimationStartMillis, total);
            effectIndex = (int) (phase / effectDuration);
            effect = effects[effectIndex];
        } else {
            effectIndex = 0;
            for (int i = 0; i < effects.length; i++) {
                if (effects[i].equals(effect)) {
                    effectIndex = i;
                    break;
                }
            }
        }

        long effectStart = titleAnimationStartMillis;
        if (configuredEffect.trim().equalsIgnoreCase("alternar")) {
            effectStart += (long) effectIndex * effectDuration;
        }

        long localPhase = Math.floorMod(now - effectStart, effectDuration);
        double progress = (double) localPhase / (double) effectDuration;

        double[] weights = new double[8];

        switch (effect) {
            case "onda-brilho" -> {
                double position = progress * 9.0 - 0.5;
                for (int i = 0; i < weights.length; i++) {
                    weights[i] = smoothStep(Math.max(0.0, 1.0 - Math.abs(i - position) / 1.9));
                }
            }
            case "pulso-central" -> {
                double position = 3.5;
                double radius = progress * 5.0;
                for (int i = 0; i < weights.length; i++) {
                    double distance = Math.abs(i - position);
                    weights[i] = smoothStep(Math.max(0.0, 1.0 - Math.abs(distance - radius) / 1.25));
                }
            }
            case "varredura" -> {
                double position = progress * 9.0 - 0.5;
                for (int i = 0; i < weights.length; i++) {
                    double distance = Math.abs(i - position);
                    weights[i] = Math.exp(-(distance * distance) / 1.05);
                }
            }
            case "respiracao" -> {
                double pulse = (Math.sin(progress * Math.PI * 2.0) + 1.0) / 2.0;
                for (int i = 0; i < weights.length; i++) {
                    weights[i] = pulse;
                }
            }
            case "arco-luz" -> {
                double position = progress * 10.0 - 1.0;
                for (int i = 0; i < weights.length; i++) {
                    double distance = Math.abs(i - position);
                    double head = Math.exp(-(distance * distance) / 0.8);
                    double tail = i < position ? Math.exp(-(position - i) / 2.2) * 0.35 : 0.0;
                    weights[i] = Math.max(head, tail);
                }
            }
            case "duas-ondas" -> {
                double left = progress * 8.0;
                double right = 7.0 - progress * 8.0;
                for (int i = 0; i < weights.length; i++) {
                    double leftWave = smoothStep(Math.max(0.0, 1.0 - Math.abs(i - left) / 1.6));
                    double rightWave = smoothStep(Math.max(0.0, 1.0 - Math.abs(i - right) / 1.6));
                    weights[i] = Math.max(leftWave, rightWave);
                }
            }
            case "centro-explodindo" -> {
                double radius = progress * 6.0;
                for (int i = 0; i < weights.length; i++) {
                    double distance = Math.abs(i - 3.5);
                    weights[i] = smoothStep(Math.max(0.0, 1.0 - Math.abs(distance - radius) / 1.45));
                }
            }
            case "eco" -> {
                double position = progress * 10.0 - 1.0;
                for (int i = 0; i < weights.length; i++) {
                    if (i <= position) {
                        weights[i] = Math.exp(-(position - i) / 2.1);
                    }
                }
            }
            case "neve" -> {
                for (int i = 0; i < weights.length; i++) {
                    double sparkle = (Math.sin(progress * Math.PI * 4.0 + i * 1.7) + 1.0) / 2.0;
                    weights[i] = Math.pow(sparkle, 3.5) * 0.65;
                }
            }
            default -> {
                return title;
            }
        }

        return colorSurvivalEffect(title, weights);
    }

    private String colorSurvivalEffect(String title, double[] weights) {
        String visibleTitle = stripLegacyFormatting(title);
        String target = "SURVIVAL";
        int start = visibleTitle.toUpperCase(java.util.Locale.ROOT).indexOf(target);
        if (start < 0) {
            return title;
        }

        StringBuilder rendered = new StringBuilder(title.length() + 64);
        int visibleIndex = 0;
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
                }
                rendered.append('&').append(code);
                i++;
                continue;
            }

            boolean survival = visibleIndex >= start && visibleIndex < start + target.length();
            if (survival) {
                int letter = visibleIndex - start;
                double weight = Math.max(0.0, Math.min(1.0, weights[letter]));
                rendered.append(toHexColor(interpolateColor(
                        85, 255, 85,
                        255, 255, 255,
                        weight
                )));
                if (bold) {
                    rendered.append("&l");
                }
            }

            rendered.append(c);
            visibleIndex++;
        }

        return rendered.toString();
    }

    private String stripLegacyFormatting(String text) {
        StringBuilder visible = new StringBuilder(text.length());
        for (int i = 0; i < text.length(); i++) {
            char c = text.charAt(i);
            if (c == '&' && i + 1 < text.length()) {
                i++;
                continue;
            }
            visible.append(c);
        }
        return visible.toString();
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

    private String applyGradients(String text) {
        if (text == null || text.isEmpty()) {
            return text;
        }

        Pattern pattern = Pattern.compile("<gradient:(#[0-9a-fA-F]{6}):(#[0-9a-fA-F]{6})>(.*?)</gradient>", Pattern.DOTALL);
        Matcher matcher = pattern.matcher(text);
        StringBuffer result = new StringBuffer();

        while (matcher.find()) {
            int[] start = parseHexColor(matcher.group(1));
            int[] end = parseHexColor(matcher.group(2));
            matcher.appendReplacement(result, Matcher.quoteReplacement(colorGradientText(matcher.group(3), start, end)));
        }

        matcher.appendTail(result);
        return result.toString();
    }

    private String colorGradientText(String text, int[] start, int[] end) {
        int visibleLength = stripLegacyFormatting(text).codePointCount(0, stripLegacyFormatting(text).length());
        if (visibleLength == 0) {
            return "";
        }

        StringBuilder result = new StringBuilder(text.length() + visibleLength * 14);
        int visibleIndex = 0;
        String activeFormatting = "";

        for (int i = 0; i < text.length();) {
            int codePoint = text.codePointAt(i);
            int charCount = Character.charCount(codePoint);

            if (codePoint == '&' && i + 1 < text.length()) {
                char code = text.charAt(i + 1);
                if ("klmnoKLMNO".indexOf(code) >= 0) {
                    activeFormatting = addFormatting(activeFormatting, Character.toLowerCase(code));
                    result.append('&').append(code);
                    i += 2;
                    continue;
                }
                if (code == 'r' || code == 'R') {
                    activeFormatting = "";
                    result.append('&').append(code);
                    i += 2;
                    continue;
                }
                result.append('&').append(code);
                i += 2;
                continue;
            }

            double progress = visibleLength <= 1
                    ? 0.0
                    : (double) visibleIndex / (double) (visibleLength - 1);

            result.append(toHexColor(interpolateColor(
                    start[0], start[1], start[2],
                    end[0], end[1], end[2],
                    progress
            )));
            if (!activeFormatting.isEmpty()) {
                result.append(activeFormatting);
            }
            result.appendCodePoint(codePoint);

            visibleIndex++;
            i += charCount;
        }

        return result.toString();
    }

    private String addFormatting(String current, char code) {
        String normalized = String.valueOf(code);
        if (current.indexOf(normalized) >= 0) {
            return current;
        }
        return current + normalized;
    }

    private int[] parseHexColor(String value) {
        String hex = value.substring(1);
        return new int[] {
                Integer.parseInt(hex.substring(0, 2), 16),
                Integer.parseInt(hex.substring(2, 4), 16),
                Integer.parseInt(hex.substring(4, 6), 16)
        };
    }

    private String toSectionCodes(String text) {
        if (text == null || text.isEmpty()) {
            return text;
        }
        return text.replace('&', '§');
    }

    private String truncate(String text, int max) {
        return text.length() <= max ? text : text.substring(0, max);
    }
}
