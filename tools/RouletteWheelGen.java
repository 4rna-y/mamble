import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.geom.AffineTransform;
import java.awt.geom.Arc2D;
import java.awt.geom.Ellipse2D;
import java.awt.image.BufferedImage;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import javax.imageio.ImageIO;

/**
 * ルーレットのホイールの絵とモデルを作る。
 *
 * <p>512x512。外周のリム、37 の扇形 (0 が 12 時、時計回りに標準の並び)、金の仕切り、番号 (内蔵の 5x7 字形)、
 * 中央のハブ。0 のポケットの外側に白い印を付け、実機で向きを確かめられるようにする。
 * モデルは薄い板 1 枚 (item/generated だと 512px の縁取りを押し出してしまう)。
 *
 * <p>使い方: {@code java tools/RouletteWheelGen.java pack}
 */
public final class RouletteWheelGen {

    private static final int SIZE = 512;
    private static final int[] WHEEL = {
        0, 32, 15, 19, 4, 21, 2, 25, 17, 34, 6, 27, 13, 36, 11, 30, 8, 23, 10,
        5, 24, 16, 33, 1, 20, 14, 31, 9, 22, 18, 29, 7, 28, 12, 35, 3, 26,
    };
    private static final java.util.Set<Integer> RED = java.util.Set.of(
            1, 3, 5, 7, 9, 12, 14, 16, 18, 19, 21, 23, 25, 27, 30, 32, 34, 36);

    /** 5x7 の数字。1 が塗り。 */
    private static final String[][] DIGITS = {
        {"01110", "10001", "10011", "10101", "11001", "10001", "01110"},
        {"00100", "01100", "00100", "00100", "00100", "00100", "01110"},
        {"01110", "10001", "00001", "00010", "00100", "01000", "11111"},
        {"11111", "00010", "00100", "00010", "00001", "10001", "01110"},
        {"00010", "00110", "01010", "10010", "11111", "00010", "00010"},
        {"11111", "10000", "11110", "00001", "00001", "10001", "01110"},
        {"00110", "01000", "10000", "11110", "10001", "10001", "01110"},
        {"11111", "00001", "00010", "00100", "01000", "01000", "01000"},
        {"01110", "10001", "10001", "01110", "10001", "10001", "01110"},
        {"01110", "10001", "10001", "01111", "00001", "00010", "01100"},
    };

    public static void main(String[] args) throws IOException {
        Path pack = Path.of(args.length > 0 ? args[0] : "pack");
        Path assets = pack.resolve("assets/mamble");
        Files.createDirectories(assets.resolve("textures/item"));
        Files.createDirectories(assets.resolve("items"));
        Files.createDirectories(assets.resolve("models/item"));

        BufferedImage image = new BufferedImage(SIZE, SIZE, BufferedImage.TYPE_INT_ARGB);
        Graphics2D g = image.createGraphics();
        g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        double c = SIZE / 2.0;
        double outer = SIZE / 2.0 - 2;
        double rim = outer * 0.93;
        double pocketOuter = rim;
        double pocketInner = outer * 0.62;
        double hub = outer * 0.60;

        // リム
        g.setColor(new Color(0x5a, 0x3a, 0x1e));
        g.fill(new Ellipse2D.Double(c - outer, c - outer, outer * 2, outer * 2));
        g.setColor(new Color(0xd4, 0xa8, 0x3a));
        g.setStroke(new BasicStroke(4f));
        g.draw(new Ellipse2D.Double(c - outer + 2, c - outer + 2, outer * 2 - 4, outer * 2 - 4));

        // 扇形。Arc2D は 3 時が 0 度で反時計回りが正なので、12 時から時計回りへ変換する
        double step = 360.0 / WHEEL.length;
        for (int i = 0; i < WHEEL.length; i++) {
            int number = WHEEL[i];
            double startClockwise = i * step - step / 2;      // 12 時から時計回り
            double arcStart = 90 - startClockwise - step;     // Arc2D の角へ
            Color fill = number == 0 ? new Color(0x0e, 0x7a, 0x2e)
                    : RED.contains(number) ? new Color(0xb0, 0x10, 0x2a) : new Color(0x14, 0x14, 0x14);
            g.setColor(fill);
            g.fill(new Arc2D.Double(c - pocketOuter, c - pocketOuter, pocketOuter * 2, pocketOuter * 2,
                    arcStart, step, Arc2D.PIE));
        }
        // ハブで扇形の内側を塗りつぶす
        g.setColor(new Color(0x2a, 0x1a, 0x0c));
        g.fill(new Ellipse2D.Double(c - pocketInner, c - pocketInner, pocketInner * 2, pocketInner * 2));
        g.setColor(new Color(0xd4, 0xa8, 0x3a));
        g.setStroke(new BasicStroke(3f));
        g.draw(new Ellipse2D.Double(c - pocketInner, c - pocketInner, pocketInner * 2, pocketInner * 2));
        g.draw(new Ellipse2D.Double(c - rim, c - rim, rim * 2, rim * 2));
        // 仕切り
        g.setStroke(new BasicStroke(2f));
        for (int i = 0; i < WHEEL.length; i++) {
            double a = Math.toRadians(i * step - step / 2);
            g.drawLine((int) Math.round(c + Math.sin(a) * pocketInner), (int) Math.round(c - Math.cos(a) * pocketInner),
                    (int) Math.round(c + Math.sin(a) * rim), (int) Math.round(c - Math.cos(a) * rim));
        }
        // 番号 (扇形の中ほどに、外側を上にして)
        double textR = (pocketOuter + pocketInner) / 2;
        for (int i = 0; i < WHEEL.length; i++) {
            double a = Math.toRadians(i * step);
            AffineTransform saved = g.getTransform();
            g.translate(c + Math.sin(a) * textR, c - Math.cos(a) * textR);
            g.rotate(a);
            drawNumber(g, WHEEL[i], 3);
            g.setTransform(saved);
        }
        // ハブの飾り
        g.setColor(new Color(0xd4, 0xa8, 0x3a));
        g.setStroke(new BasicStroke(6f));
        g.drawLine((int) (c - hub * 0.6), (int) c, (int) (c + hub * 0.6), (int) c);
        g.drawLine((int) c, (int) (c - hub * 0.6), (int) c, (int) (c + hub * 0.6));
        g.fill(new Ellipse2D.Double(c - 14, c - 14, 28, 28));
        // 0 の外側の印
        g.setColor(Color.WHITE);
        g.fill(new Ellipse2D.Double(c - 5, c - outer + 6, 10, 10));
        g.dispose();

        ImageIO.write(image, "png", assets.resolve("textures/item/roulette_wheel.png").toFile());
        Files.writeString(assets.resolve("items/roulette_wheel.json"), """
                {
                  "model": {
                    "type": "minecraft:model",
                    "model": "mamble:item/roulette_wheel"
                  }
                }
                """);
        Files.writeString(assets.resolve("models/item/roulette_wheel.json"), """
                {
                  "textures": {
                    "layer0": "mamble:item/roulette_wheel",
                    "particle": "mamble:item/roulette_wheel"
                  },
                  "elements": [
                    {
                      "from": [0, 0, 7.75],
                      "to": [16, 16, 8.25],
                      "shade": false,
                      "faces": {
                        "north": { "uv": [16, 0, 0, 16], "texture": "#layer0" },
                        "south": { "uv": [0, 0, 16, 16], "texture": "#layer0" }
                      }
                    }
                  ],
                  "display": {
                    "fixed": { "rotation": [0, 180, 0], "scale": [1, 1, 1] }
                  }
                }
                """);
        System.out.println("wrote roulette_wheel (" + SIZE + "x" + SIZE + ")");
    }

    /** 数字を中心に描く。{@code px} は 1 ドットの大きさ。 */
    private static void drawNumber(Graphics2D g, int number, int px) {
        String text = Integer.toString(number);
        int width = text.length() * 6 * px - px;
        int x0 = -width / 2;
        int y0 = -7 * px / 2;
        g.setColor(Color.WHITE);
        for (int k = 0; k < text.length(); k++) {
            String[] glyph = DIGITS[text.charAt(k) - '0'];
            for (int row = 0; row < 7; row++) {
                for (int col = 0; col < 5; col++) {
                    if (glyph[row].charAt(col) == '1') {
                        g.fillRect(x0 + (k * 6 + col) * px, y0 + row * px, px, px);
                    }
                }
            }
        }
    }
}
