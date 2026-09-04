package io.github.mamble;

import java.util.List;
import java.util.Optional;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.JoinConfiguration;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextColor;
import net.kyori.adventure.text.format.TextDecoration;
import org.bukkit.Color;
import org.bukkit.entity.Display;
import org.bukkit.entity.ItemDisplay;
import org.bukkit.util.Transformation;
import org.joml.AxisAngle4f;
import org.joml.Quaternionf;
import org.joml.Vector3f;

/**
 * ルーレット卓の表示。セルの文字と背景、掲示板、ホイールと玉の姿勢。
 */
final class RoulettePanel {

    private static final Color RED = Color.fromARGB(230, 0xB0, 0x10, 0x2A);
    private static final Color BLACK = Color.fromARGB(230, 0x10, 0x10, 0x10);
    private static final Color GREEN = Color.fromARGB(230, 0x0E, 0x7A, 0x2E);
    private static final Color OUTSIDE = Color.fromARGB(200, 0x0A, 0x50, 0x20);
    private static final Color WIN = Color.fromARGB(240, 0xE0, 0xA8, 0x20);
    private static final Color BOARD = Color.fromARGB(150, 0, 0, 0);

    private final MachinePanel base;

    RoulettePanel(MachinePanel base) {
        this.base = base;
    }

    /** 全部描き直す。 */
    void redraw(RouletteTable table, long now, boolean online) {
        for (String cell : RouletteRules.CELLS) {
            showCell(table, cell, -1);
        }
        showBoard(table, now, online);
        RouletteGame game = table.game();
        if (game != null && game.state() == RouletteGame.State.SPINNING) {
            int elapsed = game.spinElapsed(now);
            showFrame(table, spin(table, game).frame(elapsed), 0);
        } else {
            showFrame(table, new RouletteSpin.Frame(table.wheelDeg(), table.ballDeg(), RouletteSpin.POCKET_R), 0);
        }
    }

    RouletteSpin spin(RouletteTable table, RouletteGame game) {
        return new RouletteSpin(game.number(), table.wheelDeg(), table.ballDeg(), game.settings().spinTicks());
    }

    // ------------------------------------------------------------------ セル

    /**
     * セルを描く。
     *
     * @param winner 当たりの番号 (結果表示中)。それ以外は -1
     */
    void showCell(RouletteTable table, String cell, int winner) {
        RouletteGame game = table.game();
        int chips = game == null ? 0 : game.cellChips(cell);
        long total = game == null ? 0 : game.cellTotal(cell);
        boolean won = winner >= 0 && RouletteRules.covers(cell, winner);
        TextColor labelColor = won ? NamedTextColor.BLACK : NamedTextColor.WHITE;
        Component label = Component.text(RouletteRules.label(cell), labelColor).decorate(TextDecoration.BOLD);
        Component chipsLine = chips == 0
                ? Component.empty()
                : Component.text(MambleItems.amount(total), won ? NamedTextColor.DARK_GREEN : NamedTextColor.YELLOW);
        Component text = Component.join(JoinConfiguration.newlines(), List.of(Component.empty(), label, chipsLine));
        base.setText(table, cell, text, won ? WIN : background(cell));
    }

    private static Color background(String cell) {
        if (cell.startsWith("n")) {
            return switch (RouletteRules.color(Integer.parseInt(cell.substring(1)))) {
                case RED -> RED;
                case BLACK -> BLACK;
                case GREEN -> GREEN;
            };
        }
        return switch (cell) {
            case "red" -> RED;
            case "black" -> BLACK;
            default -> OUTSIDE;
        };
    }

    void showCells(RouletteTable table, int winner) {
        for (String cell : RouletteRules.CELLS) {
            showCell(table, cell, winner);
        }
    }

    // ------------------------------------------------------------------ 掲示板

    void showBoard(RouletteTable table, long now, boolean online) {
        RouletteGame game = table.game();
        Component title = Component.text("ROULETTE", NamedTextColor.GOLD).decorate(TextDecoration.BOLD);
        Component status;
        if (!online) {
            status = Component.text("休止中", NamedTextColor.RED);
        } else if (game == null) {
            status = Component.text("セルにチップを置いてください", NamedTextColor.GRAY);
        } else {
            status = switch (game.state()) {
                case IDLE -> Component.text("セルにチップを置いてください", NamedTextColor.GRAY);
                case BETTING -> Component.text("受付中 " + game.secondsLeft(now) + "s", NamedTextColor.GREEN);
                case SPINNING -> Component.text("回転中…", NamedTextColor.YELLOW);
                case RESULT -> numberText(game.number()).append(Component.text("  次まで " + game.secondsLeft(now) + "s",
                        NamedTextColor.GRAY));
            };
        }
        Component history = Component.empty();
        if (game != null && !game.history().isEmpty()) {
            history = Component.text("履歴 ", NamedTextColor.GRAY);
            for (int n : game.history()) {
                history = history.append(numberText(n)).append(Component.text(" "));
            }
        }
        base.setText(table, "board", Component.join(JoinConfiguration.newlines(), List.of(title, status, history)), BOARD);
    }

    static Component numberText(int number) {
        TextColor color = switch (RouletteRules.color(number)) {
            case RED -> NamedTextColor.RED;
            case BLACK -> NamedTextColor.WHITE;
            case GREEN -> NamedTextColor.GREEN;
        };
        return Component.text(String.valueOf(number), color).decorate(TextDecoration.BOLD);
    }

    // ------------------------------------------------------------------ ホイールと玉

    /**
     * ホイールと玉の姿勢を送る。
     *
     * @param interpolationTicks 補間の長さ。0 なら即座
     */
    void showFrame(RouletteTable table, RouletteSpin.Frame frame, int interpolationTicks) {
        Optional<ItemDisplay> wheel = base.itemDisplay(table, "wheel");
        Optional<ItemDisplay> ball = base.itemDisplay(table, "ball");
        wheel.ifPresent(display -> apply(display, wheelTransformation(frame), interpolationTicks));
        ball.ifPresent(display -> apply(display, ballTransformation(frame), interpolationTicks));
    }

    private static void apply(Display display, Transformation transformation, int interpolationTicks) {
        display.setInterpolationDelay(0);
        display.setInterpolationDuration(interpolationTicks);
        display.setTransformation(transformation);
    }

    /** ホイール: 傾き (leftRotation) の内側で、自分の法線 (Z) まわりに回す (rightRotation)。 */
    static Transformation wheelTransformation(RouletteSpin.Frame frame) {
        return new Transformation(new Vector3f(),
                new AxisAngle4f((float) Math.toRadians(BlackjackLayout.FLAT_PITCH), 1f, 0f, 0f),
                new Vector3f(RouletteLayout.WHEEL_SCALE, RouletteLayout.WHEEL_SCALE, RouletteLayout.WHEEL_SCALE),
                new AxisAngle4f((float) Math.toRadians(frame.wheelDeg()), 0f, 0f, 1f));
    }

    /** 玉: 平行移動で円周上を動かす。 */
    static Transformation ballTransformation(RouletteSpin.Frame frame) {
        return new Transformation(new Vector3f((float) frame.ballX(), 0f, (float) frame.ballZ()),
                new Quaternionf(new AxisAngle4f((float) Math.toRadians(BlackjackLayout.FLAT_PITCH), 1f, 0f, 0f)),
                new Vector3f(RouletteLayout.BALL_SCALE, RouletteLayout.BALL_SCALE, RouletteLayout.BALL_SCALE),
                new Quaternionf());
    }
}
