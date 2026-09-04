import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.image.BufferedImage;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.List;

import javax.imageio.ImageIO;

/**
 * トランプのパック素材を作る。{@code tx/trump} の 52 枚 + 裏面を加工して
 * {@code pack/assets/mamble/{items,models/item,textures/item}/card/} へ展開する。ジョーカーは使わない。
 *
 * <p>加工: 画像の縁から続く背景 (木目の茶色) を透過にし、正方形に詰めて {@link #SIZE} 角へ縮める。
 * 元絵は縦長 (2:3) なので、左右に透明の余白が付く。
 *
 * <p>使い方: {@code java tools/CardPackGen.java ../tx/trump pack}
 */
public final class CardPackGen {

    /** 出力の一辺。2 の冪でないとミップマップが落ちる。 */
    private static final int SIZE = 256;

    /** 背景とみなす色の近さ (RGB の距離)。枠の金や暗い赤の線は拾わない程度。 */
    private static final int TOLERANCE = 48;

    private static final String[] RANKS = {"a", "2", "3", "4", "5", "6", "7", "8", "9", "10", "j", "q", "k"};
    private static final String[] SUITS = {"clubs", "diamonds", "hearts", "spades"};

    public static void main(String[] args) throws IOException {
        Path source = Path.of(args.length > 0 ? args[0] : "../tx/trump");
        Path pack = Path.of(args.length > 1 ? args[1] : "pack");
        Path assets = pack.resolve("assets/mamble");
        Path items = assets.resolve("items/card");
        Path models = assets.resolve("models/item/card");
        Path textures = assets.resolve("textures/item/card");
        Files.createDirectories(items);
        Files.createDirectories(models);
        Files.createDirectories(textures);

        List<String[]> cards = new ArrayList<>();
        for (String suit : SUITS) {
            for (String rank : RANKS) {
                cards.add(new String[] {rank + "_" + suit, rank + "_" + suit + ".png"});
            }
        }
        cards.add(new String[] {"back", "card_back.png"});

        for (String[] card : cards) {
            String name = card[0];
            Path texture = source.resolve(card[1]);
            if (!Files.isRegularFile(texture)) {
                throw new IOException("テクスチャが無い: " + texture);
            }
            Files.writeString(items.resolve(name + ".json"), """
                    {
                      "model": {
                        "type": "minecraft:model",
                        "model": "mamble:item/card/%s"
                      }
                    }
                    """.formatted(name));
            Files.writeString(models.resolve(name + ".json"), """
                    {
                      "parent": "minecraft:item/generated",
                      "textures": {
                        "layer0": "mamble:item/card/%s"
                      }
                    }
                    """.formatted(name));
            BufferedImage image = ImageIO.read(texture.toFile());
            BufferedImage out = fit(clearBackground(image));
            ImageIO.write(out, "png", textures.resolve(name + ".png").toFile());
        }
        System.out.println("wrote " + cards.size() + " cards (" + SIZE + "x" + SIZE + ")");
    }

    /**
     * 縁から続く背景を透過にする。
     *
     * <p>四辺の画素を種にして、隣り合う似た色を塗りつぶす。枠の内側は縁とつながっていないので残る。
     */
    static BufferedImage clearBackground(BufferedImage source) {
        int w = source.getWidth();
        int h = source.getHeight();
        BufferedImage image = new BufferedImage(w, h, BufferedImage.TYPE_INT_ARGB);
        image.getGraphics().drawImage(source, 0, 0, null);
        boolean[] cleared = new boolean[w * h];
        Deque<Integer> queue = new ArrayDeque<>();
        for (int x = 0; x < w; x++) {
            queue.add(x);
            queue.add((h - 1) * w + x);
        }
        for (int y = 0; y < h; y++) {
            queue.add(y * w);
            queue.add(y * w + (w - 1));
        }
        // 種の色は縁の平均。木目の濃淡はこれに近い
        long r = 0;
        long g = 0;
        long b = 0;
        int n = 0;
        for (int index : queue) {
            int rgb = image.getRGB(index % w, index / w);
            r += (rgb >> 16) & 0xFF;
            g += (rgb >> 8) & 0xFF;
            b += rgb & 0xFF;
            n++;
        }
        int seedR = (int) (r / n);
        int seedG = (int) (g / n);
        int seedB = (int) (b / n);
        while (!queue.isEmpty()) {
            int index = queue.poll();
            if (cleared[index]) {
                continue;
            }
            int x = index % w;
            int y = index / w;
            int rgb = image.getRGB(x, y);
            if (((rgb >>> 24) & 0xFF) == 0 || !near(rgb, seedR, seedG, seedB)) {
                continue;
            }
            cleared[index] = true;
            image.setRGB(x, y, 0);
            if (x > 0) {
                queue.add(index - 1);
            }
            if (x < w - 1) {
                queue.add(index + 1);
            }
            if (y > 0) {
                queue.add(index - w);
            }
            if (y < h - 1) {
                queue.add(index + w);
            }
        }
        return image;
    }

    private static boolean near(int rgb, int r, int g, int b) {
        int dr = ((rgb >> 16) & 0xFF) - r;
        int dg = ((rgb >> 8) & 0xFF) - g;
        int db = (rgb & 0xFF) - b;
        return dr * dr + dg * dg + db * db <= TOLERANCE * TOLERANCE;
    }

    /** 正方形の透明な台紙の中央に置き、SIZE 角へ縮める。 */
    static BufferedImage fit(BufferedImage image) {
        int side = Math.max(image.getWidth(), image.getHeight());
        BufferedImage square = new BufferedImage(side, side, BufferedImage.TYPE_INT_ARGB);
        Graphics2D g = square.createGraphics();
        g.drawImage(image, (side - image.getWidth()) / 2, (side - image.getHeight()) / 2, null);
        g.dispose();
        // 一気に縮めるとぼやけるので、半分ずつ縮めてから最後に合わせる
        BufferedImage current = square;
        while (current.getWidth() / 2 >= SIZE) {
            current = scale(current, current.getWidth() / 2);
        }
        return current.getWidth() == SIZE ? current : scale(current, SIZE);
    }

    private static BufferedImage scale(BufferedImage image, int size) {
        BufferedImage out = new BufferedImage(size, size, BufferedImage.TYPE_INT_ARGB);
        Graphics2D g = out.createGraphics();
        g.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BILINEAR);
        g.setRenderingHint(RenderingHints.KEY_RENDERING, RenderingHints.VALUE_RENDER_QUALITY);
        g.drawImage(image, 0, 0, size, size, null);
        g.dispose();
        return out;
    }
}
