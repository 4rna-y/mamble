import java.awt.image.BufferedImage;
import java.io.File;
import java.util.LinkedHashMap;
import java.util.Map;
import javax.imageio.ImageIO;

/**
 * シンボルと設置用アイテムのテクスチャを 16x16 のドット絵から 128x128 に拡大して書き出す。
 *
 * <p>使い方: {@code java tools/TextureGen.java <出力ディレクトリ>}
 */
public final class TextureGen {

    private static final int SCALE = 8;

    private static final Map<Character, Integer> PALETTE = new LinkedHashMap<>();
    static {
        PALETTE.put('.', 0x00000000);
        PALETTE.put('k', 0xFF1A1A1A); // 輪郭
        PALETTE.put('r', 0xFFE23636); // 赤
        PALETTE.put('R', 0xFF9E1B1B); // 暗い赤
        PALETTE.put('l', 0xFFFF8A8A); // ハイライト (赤系)
        PALETTE.put('g', 0xFF4CB43C); // 緑
        PALETTE.put('G', 0xFF2A7A22); // 暗い緑
        PALETTE.put('y', 0xFFF7D93A); // 黄
        PALETTE.put('Y', 0xFFC9A400); // 暗い黄
        PALETTE.put('o', 0xFFF28C28); // 橙
        PALETTE.put('O', 0xFFB85E0C); // 暗い橙
        PALETTE.put('p', 0xFF8E44AD); // 紫
        PALETTE.put('P', 0xFF5B2C6F); // 暗い紫
        PALETTE.put('w', 0xFFFFFFFF); // 白
        PALETTE.put('b', 0xFF6B4423); // 茶
        PALETTE.put('d', 0xFF3C3C3C); // 暗い灰
        PALETTE.put('s', 0xFF9A9A9A); // 灰
        PALETTE.put('S', 0xFFDADADA); // 明るい灰
        PALETTE.put('n', 0xFF2E4A8E); // 濃い青
        PALETTE.put('c', 0xFFE8C34A); // 金 (コイン)
        PALETTE.put('C', 0xFFB08A1E); // 暗い金
    }

    private static final Map<String, String[]> ART = new LinkedHashMap<>();
    static {
        ART.put("cherry", new String[] {
            "................",
            "........kk......",
            ".......kggk.....",
            "......kg..gk....",
            ".....kg...gk....",
            ".....k....gk....",
            "....kk....kk....",
            "...krrk..krrk...",
            "..krlrrkkrlrrk..",
            "..krrrrkkrrrrk..",
            "..krrrrkkrrrrk..",
            "..kRrrRkkRrrRk..",
            "...kRRk..kRRk...",
            "....kk....kk....",
            "................",
            "................",
        });
        ART.put("lemon", new String[] {
            "................",
            "................",
            "...........kk...",
            "..........kyk...",
            ".......kkkkyk...",
            ".....kkyyyyyk...",
            "....kyywyyyyyk..",
            "...kyywyyyyyyk..",
            "...kyyyyyyyyyk..",
            "...kyyyyyyyyyk..",
            "...kYyyyyyyYk...",
            "....kYYyyyYk....",
            ".....kkYYkk.....",
            ".......kk.......",
            "................",
            "................",
        });
        ART.put("orange", new String[] {
            "................",
            ".......kk.......",
            "......kGgk......",
            ".......kkk......",
            ".....kkookk.....",
            "....koowooook...",
            "...koowoooooook.",
            "...kooooooooook.",
            "...kooooooooook.",
            "...kOooooooooOk.",
            "....kOooooooOk..",
            ".....kOOooOOk...",
            "......kkOOkk....",
            "........kk......",
            "................",
            "................",
        });
        ART.put("plum", new String[] {
            "................",
            "........kk......",
            ".......kgGk.....",
            "........kk......",
            "......kkppkk....",
            ".....kppwpppk...",
            "....kppwpppppk..",
            "....kpppppppppk.",
            "....kpppppppppk.",
            "....kPppppppPpk.",
            ".....kPpppppPk..",
            "......kPPppPk...",
            ".......kkPPk....",
            ".........kk.....",
            "................",
            "................",
        });
        ART.put("grape", new String[] {
            "................",
            "........kkk.....",
            ".......kggGk....",
            "........kkk.....",
            "......kk.kk.....",
            ".....kppkppk....",
            "....kkppkppkk...",
            "...kppkkpkkppk..",
            "...kppkppkkppk..",
            "....kkppkppkk...",
            ".....kppkppk....",
            "......kkppkk....",
            ".......kppk.....",
            "........kk......",
            "................",
            "................",
        });
        ART.put("melon", new String[] {
            "................",
            "................",
            "..kkkkkkkkkkkk..",
            ".kGgGgGgGgGgGgk.",
            ".kgwrrrrrrrrwgk.",
            ".kGrrkrrrrkrrGk.",
            "..krrrrkrrrrrk..",
            "..krrkrrrrkrrk..",
            "...krrrrrrrrk...",
            "...kRrrkkrrRk...",
            "....kRrrrrRk....",
            ".....kRRRRk.....",
            "......kkkk......",
            "................",
            "................",
            "................",
        });
        ART.put("bell", new String[] {
            "................",
            ".......kk.......",
            "......kbbk......",
            "......kyyk......",
            ".....kyywyk.....",
            ".....kyywyk.....",
            "....kyyywyyk....",
            "....kyyyyyyk....",
            "....kyyyyyyk....",
            "...kyyyyyyyyk...",
            "..kYyyyyyyyyYk..",
            "..kkkkkkkkkkkk..",
            "......kbbk......",
            ".......kk.......",
            "................",
            "................",
        });
        ART.put("bar", new String[] {
            "................",
            "................",
            "................",
            ".kkkkkkkkkkkkkk.",
            ".kddddddddddddk.",
            ".kdwwwdwwdwwwdk.",
            ".kdwdwdwdwdwdwk.",
            ".kdwwwdwwwdwwdk.",
            ".kdwdwdwdwdwdwk.",
            ".kdwwwdwdwdwdwk.",
            ".kddddddddddddk.",
            ".kkkkkkkkkkkkkk.",
            "................",
            "................",
            "................",
            "................",
        });
        ART.put("seven", new String[] {
            "................",
            "................",
            "...kkkkkkkkkk...",
            "..krrrrrrrrrrk..",
            "..krllrrrrrrrk..",
            "..kkkkkkkkrrrk..",
            "........krrrk...",
            ".......krrrk....",
            "......krrrk.....",
            ".....krrrk......",
            ".....krrrk......",
            "....krrrk.......",
            "....kRRRk.......",
            "....kkkkk.......",
            "................",
            "................",
        });
        ART.put("slot_machine", new String[] {
            "................",
            ".kkkkkkkkkkkk...",
            ".ksSSSSSSSSsk...",
            ".kskkkkkkkksk.k.",
            ".kskwkwkwkkskkck",
            ".kskrkykpkksk.k.",
            ".kskkkkkkkksk.k.",
            ".ksSSSSSSSSskkk.",
            ".kdddddddddddk..",
            ".kdkkkkkkkkkdk..",
            ".kdkcccccccsdk..",
            ".kdkkkkkkkkkdk..",
            ".kdddddddddddk..",
            ".kdddddddddddk..",
            ".kkkkkkkkkkkkk..",
            "................",
        });
        ART.put("blackjack", new String[] {
            "................",
            "................",
            "...kkkk..kkkk...",
            "...kwwk..kwwk...",
            "...kwrk..kwkk...",
            "...kwwk..kwwk...",
            "...kkkk..kkkk...",
            "..kkkkkkkkkkkk..",
            ".kGGGGGGGGGGGGk.",
            ".kGgGGGGGGGGgGk.",
            ".kGGGGGGGGGGGGk.",
            ".kbbbbbbbbbbbbk.",
            ".kbbbbbbbbbbbbk.",
            "..kkkkkkkkkkkk..",
            "................",
            "................",
        });
        ART.put("roulette", new String[] {
            "................",
            ".....kkkkkk.....",
            "...kkccccccck...",
            "..kcrkkkkkkrck..",
            ".kcrkddkkddkrck.",
            ".kckdrkkkkrdkck.",
            "kcrkkkkccckkkrck",
            "kckdkkcwckkkdkck",
            "kckdkkccckkkdkck",
            "kcrkkkkkkkkkkrck",
            ".kckdrkkkkrdkck.",
            ".kcrkddkkddkrck.",
            "..kcrkkkkkkrck..",
            "...kkccccccck...",
            ".....kkkkkk.....",
            "................",
        });
        ART.put("exchange", new String[] {
            "................",
            ".kkkkkkkkkkkkk..",
            ".kdddddddddddk..",
            ".kdkkkkkkkkkdk..",
            ".kdkSSSSSSSkdk..",
            ".kdkkkkkkkkkdk..",
            ".kddddkkkkdddk..",
            ".kdddkccccckdk..",
            ".kddkccCcccckk..",
            ".kddkcCwCccckk..",
            ".kddkccCcccckk..",
            ".kdddkccccckdk..",
            ".kddddkkkkdddk..",
            ".kdddddddddddk..",
            ".kkkkkkkkkkkkk..",
            "................",
        });
    }

    public static void main(String[] args) throws Exception {
        File out = new File(args.length > 0 ? args[0] : ".");
        out.mkdirs();
        for (var e : ART.entrySet()) {
            String[] rows = e.getValue();
            if (rows.length != 16) {
                throw new IllegalStateException(e.getKey() + ": 行数が " + rows.length);
            }
            BufferedImage image = new BufferedImage(16 * SCALE, 16 * SCALE, BufferedImage.TYPE_INT_ARGB);
            for (int y = 0; y < 16; y++) {
                if (rows[y].length() != 16) {
                    throw new IllegalStateException(e.getKey() + ": " + (y + 1) + "行目の長さが " + rows[y].length());
                }
                for (int x = 0; x < 16; x++) {
                    Integer argb = PALETTE.get(rows[y].charAt(x));
                    if (argb == null) {
                        throw new IllegalStateException(e.getKey() + ": 知らない色 '" + rows[y].charAt(x) + "'");
                    }
                    for (int dy = 0; dy < SCALE; dy++) {
                        for (int dx = 0; dx < SCALE; dx++) {
                            image.setRGB(x * SCALE + dx, y * SCALE + dy, argb);
                        }
                    }
                }
            }
            ImageIO.write(image, "png", new File(out, e.getKey() + ".png"));
            System.out.println("wrote " + e.getKey() + ".png");
        }
    }
}
