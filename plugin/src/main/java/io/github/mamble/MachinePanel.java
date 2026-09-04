package io.github.mamble;

import java.util.List;
import java.util.Optional;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.JoinConfiguration;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextColor;
import net.kyori.adventure.text.format.TextDecoration;
import org.bukkit.Color;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.entity.Display;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Interaction;
import org.bukkit.entity.ItemDisplay;
import org.bukkit.entity.TextDisplay;
import org.bukkit.entity.Villager;
import org.bukkit.inventory.ItemStack;
import org.bukkit.util.Transformation;
import org.joml.AxisAngle4f;
import org.joml.Vector3f;

/**
 * 台の正面の表示。Display / Interaction エンティティの生成と内容の更新を担う。
 *
 * <p>エンティティは UUID で引く ({@link Machine#part})。チャンクが読み込まれていなければ
 * 引けないので、そのときは何もしない (次に読み込まれたときに {@link #spawnMissing} が揃え直す)。
 */
public final class MachinePanel {

    /** 当たったマスを光らせる色。 */
    public static final Color WIN_GLOW = Color.fromRGB(255, 200, 40);

    private static final Color TEXT_BACKGROUND = Color.fromARGB(110, 0, 0, 0);
    private static final Color BUTTON_BACKGROUND = Color.fromARGB(220, 40, 40, 40);
    private static final Color START_BACKGROUND = Color.fromARGB(230, 20, 110, 40);
    private static final Color OFFLINE_BACKGROUND = Color.fromARGB(230, 110, 20, 20);
    private static final TextColor GOLD = NamedTextColor.GOLD;

    private final SymbolTable symbols;
    private final String dealerName;

    public MachinePanel(SymbolTable symbols) {
        this(symbols, "ディーラー");
    }

    public MachinePanel(SymbolTable symbols, String dealerName) {
        this.symbols = symbols;
        this.dealerName = dealerName;
    }

    public String dealerName() {
        return dealerName;
    }

    // ------------------------------------------------------------------ 生成と削除

    /**
     * 欠けている部品を作る。
     *
     * @return 1つでも作ったら true (UUID が変わったので保存が要る)
     */
    public boolean spawnMissing(Machine machine) {
        World world = machine.world();
        if (world == null) {
            return false;
        }
        boolean changed = false;
        for (MachineLayout.Part part : machine.layout()) {
            if (machine.part(part.key()).isPresent()) {
                continue;
            }
            Entity entity = spawn(machine, part, world);
            machine.setPart(part.key(), entity.getUniqueId());
            changed = true;
        }
        if (changed && machine instanceof SlotMachine slot) {
            resetPanel(slot);
        }
        return changed;
    }

    /**
     * 部品を今の配置へ寄せる。配置 (座標や縮尺) を変えた後に、既にある台へ反映するために呼ぶ。
     *
     * @return 動かしたら true
     */
    public boolean realign(Machine machine) {
        World world = machine.world();
        if (world == null) {
            return false;
        }
        boolean moved = false;
        for (MachineLayout.Part part : machine.layout()) {
            Optional<Entity> entity = machine.part(part.key());
            if (entity.isEmpty()) {
                continue;
            }
            Location wanted = location(machine, part);
            wanted.setYaw(MachineLayout.yawOf(MachineLayout.facingOf(machine.facing(), part.side())));
            wanted.setPitch(0f);
            if (entity.get().getLocation().distanceSquared(wanted) > 1e-6) {
                entity.get().teleport(wanted);
                moved = true;
            }
            if (entity.get() instanceof Display display && part.kind() != MachineLayout.Kind.HIT
                    && part.kind() != MachineLayout.Kind.PAD && part.kind() != MachineLayout.Kind.WHEEL
                    && part.kind() != MachineLayout.Kind.BALL) {
                Transformation wantedTransform = transformation(part);
                Transformation current = display.getTransformation();
                if (!current.getScale().equals(wantedTransform.getScale(), 1e-5f)
                        || !current.getLeftRotation().equals(wantedTransform.getLeftRotation(), 1e-5f)) {
                    display.setTransformation(wantedTransform);
                    moved = true;
                }
            }
        }
        return moved;
    }

    public void removeAll(Machine machine) {
        for (MachineLayout.Part part : machine.layout()) {
            machine.part(part.key()).ifPresent(Entity::remove);
        }
        machine.parts().clear();
    }

    /** 部品の位置。 */
    public Location location(Machine machine, MachineLayout.Part part) {
        World world = machine.world();
        Location center = MachineLayout.anchor(machine.base(), machine.facing(), part).center(world);
        return center.add(MachineLayout.offset(MachineLayout.facingOf(machine.facing(), part.side()), part));
    }

    private Entity spawn(Machine machine, MachineLayout.Part part, World world) {
        Location location = location(machine, part);
        float yaw = MachineLayout.yawOf(MachineLayout.facingOf(machine.facing(), part.side()));
        Entity entity = switch (part.kind()) {
            case REEL, ICON, PAY_ICON, CARD, WHEEL, BALL -> world.spawn(location, ItemDisplay.class, display -> {
                display.setItemDisplayTransform(ItemDisplay.ItemDisplayTransform.FIXED);
                display.setTransformation(transformation(part));
                display.setBillboard(Display.Billboard.FIXED);
                display.setRotation(yaw, 0f);
                display.setGlowColorOverride(WIN_GLOW);
                display.setItemStack(initialItem(part));
            });
            case TEXT, BUTTON -> world.spawn(location, TextDisplay.class, display -> {
                display.setBillboard(Display.Billboard.FIXED);
                display.setRotation(yaw, 0f);
                display.setTransformation(transformation(part));
                display.setShadowed(true);
                display.setSeeThrough(false);
                display.setAlignment(part.key().startsWith("pay_col")
                        ? TextDisplay.TextAlignment.RIGHT : TextDisplay.TextAlignment.CENTER);
                display.setBackgroundColor(part.kind() == MachineLayout.Kind.BUTTON
                        ? BUTTON_BACKGROUND : TEXT_BACKGROUND);
                display.text(initialText(part));
            });
            case HIT, PAD -> world.spawn(location, Interaction.class, interaction -> {
                interaction.setInteractionWidth(part.scale());
                interaction.setInteractionHeight(part.kind() == MachineLayout.Kind.PAD
                        ? MachineLayout.FLAT_HIT_HEIGHT : MachineLayout.HIT_HEIGHT);
                interaction.setResponsive(true);
            });
            case DEALER -> world.spawn(location, Villager.class, villager -> {
                villager.setAI(false);
                villager.setAware(false);
                villager.setInvulnerable(true);
                villager.setSilent(true);
                villager.setCollidable(false);
                villager.setGravity(false);
                villager.setRemoveWhenFarAway(false);
                villager.setCanPickupItems(false);
                villager.setProfession(Villager.Profession.NONE);
                villager.setRecipes(List.of());
                villager.setRotation(yaw, 0f);
                villager.customName(Component.text(dealerName, GOLD));
                villager.setCustomNameVisible(true);
            });
        };
        entity.setPersistent(true);
        machine.tag(entity, part.key());
        return entity;
    }

    private static Transformation scaled(float scale) {
        return new Transformation(new Vector3f(), new AxisAngle4f(), new Vector3f(scale, scale, scale),
                new AxisAngle4f());
    }

    /** 部品の縮尺と傾き。pitch は X 軸まわりの回転で、-90 なら上を向いて平置きになる。 */
    static Transformation transformation(MachineLayout.Part part) {
        return new Transformation(new Vector3f(),
                new AxisAngle4f((float) Math.toRadians(part.pitch()), 1f, 0f, 0f),
                new Vector3f(part.scale(), part.scale(), part.scale()), new AxisAngle4f());
    }

    private ItemStack initialItem(MachineLayout.Part part) {
        return switch (part.kind()) {
            case ICON -> MambleItems.coinItem(Component.text("クレジット"), List.of());
            case CARD -> ItemStack.empty();
            case WHEEL -> MambleItems.wheelItem();
            case BALL -> MambleItems.ballItem();
            case PAY_ICON -> paytableSymbol(part.key())
                    .map(MambleItems::symbolItem)
                    .orElse(ItemStack.of(Material.AIR));
            default -> MambleItems.symbolItem(symbols.all().get(0));
        };
    }

    /** {@code pay_icon_<行>} に対応するシンボル。行がシンボル数を超えていれば空。 */
    private Optional<Symbol> paytableSymbol(String key) {
        try {
            int row = Integer.parseInt(key.substring("pay_icon_".length()));
            return row >= 1 && row <= symbols.all().size()
                    ? Optional.of(symbols.all().get(row - 1)) : Optional.empty();
        } catch (NumberFormatException e) {
            return Optional.empty();
        }
    }

    private Component initialText(MachineLayout.Part part) {
        if (part.key().endsWith("minus")) {
            return Component.text(" − ", NamedTextColor.WHITE);
        }
        if (part.key().endsWith("plus")) {
            return Component.text(" + ", NamedTextColor.WHITE);
        }
        return switch (part.key()) {
            case "label" -> Component.text("交換機", GOLD);
            case "pay_col3" -> paytableColumn(0);
            case "pay_col4" -> paytableColumn(1);
            case "pay_col5" -> paytableColumn(2);
            default -> Component.empty();
        };
    }

    /**
     * 配当表の1列 (×3 / ×4 / ×5 のどれか)。見出し + シンボルごとの倍率を1行ずつ。
     *
     * <p>行数はシンボル数に関係なく {@link MachineLayout#PAYTABLE_ROWS} + 1 に揃え、
     * アイコンの行と高さが合うようにする。
     */
    Component paytableColumn(int column) {
        return paytableColumn(column, -1);
    }

    /**
     * @param highlightRow 目立たせる行 (0 始まり)。負なら無し。列側の判定は呼び出し側が行う
     */
    Component paytableColumn(int column, int highlightRow) {
        List<Component> lines = new java.util.ArrayList<>();
        lines.add(Component.text("×" + (column + SymbolTable.MIN_MATCH), NamedTextColor.GRAY)
                .decorate(TextDecoration.BOLD));
        for (int row = 0; row < MachineLayout.PAYTABLE_ROWS; row++) {
            if (row < symbols.all().size()) {
                int multiplier = symbols.all().get(row).multiplier(column + SymbolTable.MIN_MATCH);
                lines.add(row == highlightRow
                        ? Component.text("▶" + MambleItems.amount(multiplier), GOLD).decorate(TextDecoration.BOLD)
                        : Component.text(MambleItems.amount(multiplier), NamedTextColor.WHITE));
            } else {
                lines.add(Component.empty());
            }
        }
        return Component.join(JoinConfiguration.newlines(), lines);
    }

    // ------------------------------------------------------------------ スロット台の内容

    /** 案内文だけの初期状態に戻す。 */
    public void resetPanel(SlotMachine machine) {
        showIdle(machine);
        List<Symbol> all = symbols.all();
        for (int i = 0; i < MachineLayout.REEL_KEYS.size(); i++) {
            setReel(machine, i, all.get(i % all.size()));
        }
        showPaytable(machine);
    }

    /** 配当表を描き直す (リロードで表が変わったときにも呼ぶ)。 */
    public void showPaytable(SlotMachine machine) {
        setText(machine, "pay_col3", paytableColumn(0));
        setText(machine, "pay_col4", paytableColumn(1));
        setText(machine, "pay_col5", paytableColumn(2));
        for (int row = 1; row <= MachineLayout.PAYTABLE_ROWS; row++) {
            String key = "pay_icon_" + row;
            ItemStack item = paytableSymbol(key).map(MambleItems::symbolItem).orElse(ItemStack.of(Material.AIR));
            itemDisplay(machine, key).ifPresent(display -> display.setItemStack(item));
        }
    }

    /**
     * 当選したシンボルの行と倍率の欄を目立たせる。アイコンは光らせ、倍率は金色にする。
     *
     * @param count 揃った個数 (3〜5)。その列だけ目立たせる
     * @param on    false で元に戻す
     */
    public void highlightPaytable(SlotMachine machine, Symbol symbol, int count, boolean on) {
        int row = -1;
        for (int i = 0; i < symbols.all().size(); i++) {
            if (symbols.all().get(i).id().equals(symbol.id())) {
                row = i;
            }
        }
        if (row < 0 || row >= MachineLayout.PAYTABLE_ROWS) {
            return;
        }
        int column = Math.min(count, SymbolTable.REELS) - SymbolTable.MIN_MATCH;
        int highlight = on ? row : -1;
        setText(machine, "pay_col3", paytableColumn(0, column == 0 ? highlight : -1));
        setText(machine, "pay_col4", paytableColumn(1, column == 1 ? highlight : -1));
        setText(machine, "pay_col5", paytableColumn(2, column == 2 ? highlight : -1));
        itemDisplay(machine, "pay_icon_" + (row + 1)).ifPresent(display -> display.setGlowing(on));
    }

    public void setReel(SlotMachine machine, int index, Symbol symbol) {
        itemDisplay(machine, MachineLayout.REEL_KEYS.get(index))
                .ifPresent(display -> display.setItemStack(MambleItems.symbolItem(symbol)));
    }

    public void setReels(SlotMachine machine, List<Symbol> reels) {
        for (int i = 0; i < reels.size(); i++) {
            setReel(machine, i, reels.get(i));
        }
    }

    public void glow(SlotMachine machine, List<Integer> indices, boolean on) {
        for (int index : indices) {
            itemDisplay(machine, MachineLayout.REEL_KEYS.get(index))
                    .ifPresent(display -> display.setGlowing(on));
        }
    }

    /** リールの位置 (パーティクル用)。 */
    public Optional<Location> reelLocation(SlotMachine machine, int index) {
        return machine.part(MachineLayout.REEL_KEYS.get(index)).map(Entity::getLocation);
    }

    /** 操作者がいない状態。名前欄が START ボタンになる。 */
    public void showIdle(SlotMachine machine) {
        setText(machine, "name", Component.text("▶ START", NamedTextColor.WHITE).decorate(TextDecoration.BOLD),
                START_BACKGROUND);
        setText(machine, "balance", Component.text("残高 ---", NamedTextColor.GRAY));
        setText(machine, "bet", Component.text("BET ---", NamedTextColor.GRAY));
    }

    /** 休止中 (mstore に届かない)。 */
    public void showOffline(SlotMachine machine) {
        setText(machine, "name", Component.text("休止中", NamedTextColor.WHITE), OFFLINE_BACKGROUND);
        setText(machine, "balance", Component.text("残高 ---", NamedTextColor.GRAY));
        setText(machine, "bet", Component.text("BET ---", NamedTextColor.GRAY));
    }

    public void showOperator(SlotMachine machine, String name, Optional<Long> balance, long bet) {
        setText(machine, "name", Component.text(name, GOLD), TEXT_BACKGROUND);
        showBalance(machine, balance);
        showBet(machine, bet);
    }

    public void showBalance(SlotMachine machine, Optional<Long> balance) {
        Component value = balance
                .map(b -> Component.text(MambleItems.amount(b), NamedTextColor.WHITE))
                .orElse(Component.text("読み込み中", NamedTextColor.GRAY));
        setText(machine, "balance", Component.text("残高 ", NamedTextColor.GRAY).append(value));
    }

    public void showBet(SlotMachine machine, long bet) {
        setText(machine, "bet", Component.text("BET ", NamedTextColor.GRAY)
                .append(Component.text(MambleItems.amount(bet), NamedTextColor.YELLOW)));
    }

    void setText(Machine machine, String key, Component text) {
        textDisplay(machine, key).ifPresent(display -> display.text(text));
    }

    void setText(Machine machine, String key, Component text, Color background) {
        textDisplay(machine, key).ifPresent(display -> {
            display.text(text);
            display.setBackgroundColor(background);
        });
    }

    Optional<TextDisplay> textDisplay(Machine machine, String key) {
        return machine.part(key).filter(TextDisplay.class::isInstance).map(TextDisplay.class::cast);
    }

    Optional<ItemDisplay> itemDisplay(Machine machine, String key) {
        return machine.part(key).filter(ItemDisplay.class::isInstance).map(ItemDisplay.class::cast);
    }
}
