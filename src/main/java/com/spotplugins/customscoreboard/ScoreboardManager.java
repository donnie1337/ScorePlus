package com.spotplugins.customscoreboard;

import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.entity.Player;
import org.bukkit.event.player.PlayerQuitEvent;
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
import java.lang.reflect.Constructor;
import java.lang.reflect.Method;

/**
 * Cria uma scoreboard privada por jogador e atualiza somente o conteúdo.
 *
 * A regra principal desta classe é simples:
 * - a scoreboard é atribuída ao jogador somente quando ela é criada ou ligada;
 * - as atualizações de 1 segundo nunca chamam setScoreboard();
 * - objetivo, equipes e entradas são reutilizados para impedir flicker.
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

        if (disabled.remove(id)) {
            // Liga: cria e atribui uma única scoreboard.
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
     * Atualiza todas as informações sem trocar a scoreboard do jogador.
     * Este método é chamado a cada 20 ticks por padrão.
     */
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
     * Atualiza uma scoreboard já atribuída. Se ainda não existir, cria-a uma vez.
     */
    public void update(Player player) {
        if (!isEnabledFor(player) || !player.isOnline()) {
            return;
        }

        Scoreboard board = boards.get(player.getUniqueId());
        if (board == null) {
            createAndAssignBoard(player);
            return;
        }

        updateBoardContents(player, board);
    }

    /**
     * Remove referências do jogador quando ele sai para evitar retenção de memória.
     */
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

        // IMPORTANTE: esta é a única atribuição normal da scoreboard.
        player.setScoreboard(board);
    }

    private void buildBoard(Player player, Scoreboard board) {
        Objective objective = board.registerNewObjective(
                OBJECTIVE_ID,
                "dummy",
                currentTitle()
        );
        objective.setDisplaySlot(DisplaySlot.SIDEBAR);
        hideScoreNumbers(objective);

        List<String> lines = getConfiguredLines();

        for (int i = 0; i < lines.size(); i++) {
            String entry = uniqueInvisibleEntry(i);
            Team team = board.registerNewTeam("csb_line_" + i);
            team.addEntry(entry);
            team.setPrefix(renderLine(player, lines.get(i)));

            // O valor do score existe apenas para ordenar as linhas; ele não deve
            // aparecer visualmente no cliente. O objetivo usa entradas invisíveis
            // e a equipe fornece todo o texto exibido na linha.
        }
    }

    private void updateBoardContents(Player player, Scoreboard board) {
        Objective objective = board.getObjective(OBJECTIVE_ID);
        if (objective == null) {
            // Recuperação estrutural: isto não ocorre no fluxo normal.
            rebuildBoard(player, board);
            return;
        }

        hideScoreNumbers(objective);

        String title = currentTitle();
        if (plugin.getConfig().getBoolean("title-animation-enabled", false)
                && !title.equals(objective.getDisplayName())) {
            objective.setDisplayName(title);
        }

        List<String> lines = getConfiguredLines();

        // A estrutura só é corrigida quando realmente necessário.
        // Em condições normais, o número de linhas não muda durante o tick.
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

            // A pontuação já foi definida na criação da linha. Não a reescrevemos
            // durante o update para evitar alterações visuais desnecessárias.
        }

        removeUnusedLines(board, lines.size());
    }

    /**
     * Remove os números de pontuação exibidos à direita das linhas.
     *
     * O Spigot API usado pelo projeto ainda não expõe NumberFormat, enquanto
     * o servidor 26.2 já possui o BlankFormat do Minecraft. Usamos reflexão
     * apenas neste ponto para manter o plugin compilável com Spigot API e
     * esconder os números no cliente sem alterar o texto/layout das linhas.
     */
    private void hideScoreNumbers(Objective objective) {
        try {
            Method getHandle = objective.getClass().getDeclaredMethod("getHandle");
            getHandle.setAccessible(true);
            Object nmsObjective = getHandle.invoke(objective);

            Class<?> blankFormatClass = Class.forName(
                    "net.minecraft.network.chat.numbers.BlankFormat");
            Constructor<?> constructor = blankFormatClass.getDeclaredConstructor();
            constructor.setAccessible(true);
            Object blankFormat = constructor.newInstance();

            Method setter = null;
            for (Method method : nmsObjective.getClass().getMethods()) {
                if (!method.getName().equals("setNumberFormat") || method.getParameterCount() != 1) {
                    continue;
                }
                if (method.getParameterTypes()[0].isAssignableFrom(blankFormatClass)) {
                    setter = method;
                    break;
                }
            }

            if (setter == null) {
                throw new NoSuchMethodException("setNumberFormat(NumberFormat)");
            }

            setter.setAccessible(true);
            setter.invoke(nmsObjective, blankFormat);
        } catch (Throwable throwable) {
            if (plugin.getConfig().getBoolean("debug", false)) {
                plugin.getLogger().warning(
                        "Não foi possível ocultar os números da scoreboard: "
                                + throwable.getClass().getSimpleName() + ": " + throwable.getMessage());
            }
        }
    }

    private void rebuildBoard(Player player, Scoreboard board) {
        // Somente usado se a estrutura interna da scoreboard for removida por outro código.
        for (String teamName : new HashSet<>(board.getTeams().stream().map(Team::getName).toList())) {
            if (teamName.startsWith("csb_line_")) {
                Team team = board.getTeam(teamName);
                if (team != null) {
                    for (String entry : new HashSet<>(team.getEntries())) {
                        board.resetScores(entry);
                    }
                    team.unregister();
                }
            }
        }

        Objective old = board.getObjective(OBJECTIVE_ID);
        if (old != null) {
            old.unregister();
        }

        buildBoard(player, board);
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
