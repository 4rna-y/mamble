package io.github.mamble;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

import java.awt.image.BufferedImage;
import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import javax.imageio.ImageIO;

import net.kyori.adventure.key.Key;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.DynamicTest;
import org.junit.jupiter.api.TestFactory;

/**
 * コードが指す {@code item_model} の一式が同梱リソースパックに揃っていることを見る。
 *
 * <p>定義 ({@code items/<key>.json}) の {@code "model"} を辿ってモデルとテクスチャを探す。
 * バニラのモデルを指すもの (鐘・スイカ) は定義だけあればよい。
 */
class PackAssetsTest {

    private static final Pattern MODEL = Pattern.compile("\"model\"\\s*:\\s*\"([a-z_]+):([a-z0-9_/]+)\"");
    private static final Pattern LAYER0 = Pattern.compile("\"layer0\"\\s*:\\s*\"([a-z_]+):([a-z0-9_/]+)\"");

    @TestFactory
    @DisplayName("シンボル・アイコン・カードの一式がパックに揃っている")
    List<DynamicTest> everyModelHasItsAssets() {
        File packDir = packDir();
        List<Key> models = new ArrayList<>();
        SymbolTable.defaults().all().forEach(symbol -> models.add(MambleItems.symbolModel(symbol.id())));
        models.add(MambleItems.COIN_MODEL);
        models.add(MambleItems.SLOT_MACHINE_MODEL);
        models.add(MambleItems.EXCHANGE_MODEL);
        models.add(MambleItems.BLACKJACK_MODEL);
        Card.all().forEach(card -> models.add(MambleItems.cardModel(card)));
        models.add(MambleItems.CARD_BACK_MODEL);
        models.add(MambleItems.ROULETTE_MODEL);
        models.add(MambleItems.ROULETTE_WHEEL_MODEL);

        List<DynamicTest> tests = new ArrayList<>();
        for (Key model : models) {
            tests.add(DynamicTest.dynamicTest(model.asString(), () -> check(packDir, model)));
        }
        return tests;
    }

    private static void check(File packDir, Key key) throws IOException {
        File definition = new File(packDir, "assets/" + key.namespace() + "/items/" + key.value() + ".json");
        assertTrue(definition.isFile(), "アイテムモデル定義が無い: " + definition);
        Matcher model = MODEL.matcher(Files.readString(definition.toPath()));
        assertTrue(model.find(), "定義に model が無い: " + definition);
        if (model.group(1).equals("minecraft")) {
            return;
        }
        File modelFile = new File(packDir, "assets/" + model.group(1) + "/models/" + model.group(2) + ".json");
        assertTrue(modelFile.isFile(), "モデルが無い: " + modelFile);
        Matcher layer = LAYER0.matcher(Files.readString(modelFile.toPath()));
        assertTrue(layer.find(), "モデルに layer0 が無い: " + modelFile);
        File texture = new File(packDir, "assets/" + layer.group(1) + "/textures/" + layer.group(2) + ".png");
        assertTrue(texture.isFile(), "テクスチャが無い: " + texture);

        BufferedImage image = ImageIO.read(texture);
        assertEquals(image.getWidth(), image.getHeight(), "正方形でなければならない: " + texture);
        assertTrue(isPowerOfTwo(image.getWidth()),
                "2の冪でないとミップマップが落ちる: " + texture + " が " + image.getWidth() + "px");
    }

    private static File packDir() {
        String property = System.getProperty("mamble.packDir");
        File dir = property == null ? new File("../pack") : new File(property);
        assumeTrue(new File(dir, "pack.mcmeta").isFile(), "pack/ が見つからない: " + dir);
        return dir;
    }

    private static boolean isPowerOfTwo(int n) {
        return n > 0 && (n & (n - 1)) == 0;
    }
}
