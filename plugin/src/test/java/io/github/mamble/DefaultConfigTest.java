package io.github.mamble;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;

import org.bukkit.configuration.file.YamlConfiguration;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/** 同梱の config.yml の既定値が、コードの既定と食い違っていないことを見る。 */
class DefaultConfigTest {

    private final YamlConfiguration config = load();

    private static YamlConfiguration load() {
        var stream = DefaultConfigTest.class.getResourceAsStream("/config.yml");
        assertNotNull(stream, "config.yml が resources に無い");
        return YamlConfiguration.loadConfiguration(new InputStreamReader(stream, StandardCharsets.UTF_8));
    }

    @Test
    @DisplayName("シンボル表は SymbolTable.defaults() と一致する")
    void symbolsMatchDefaults() {
        SymbolTable parsed = SymbolTable.parse(config);
        SymbolTable defaults = SymbolTable.defaults();
        assertEquals(defaults.all().size(), parsed.all().size());
        for (int i = 0; i < defaults.all().size(); i++) {
            assertEquals(defaults.all().get(i), parsed.all().get(i));
        }
    }

    @Test
    @DisplayName("bet の段は BetSteps.defaults() と一致する")
    void betMatchesDefaults() {
        assertEquals(BetSteps.defaults(), BetSteps.parse(config));
    }

    @Test
    @DisplayName("接頭辞・mstore・パックの既定")
    void scalars() {
        assertEquals(MamblePlugin.DEFAULT_MESSAGE_PREFIX, config.getString("message-prefix"));
        assertEquals(MamblePlugin.DEFAULT_MSTORE_URL, config.getString("mstore.base-url"));
        assertEquals(CreditLedger.DEFAULT_KEY_PREFIX, config.getString("key-prefix"));
        assertEquals(CreditLedger.DEFAULT_ATTEMPTS, config.getInt("mstore.attempts"));
        assertEquals(CreditLedger.DEFAULT_FLUSH_TIMEOUT_SECONDS, config.getLong("flush-timeout-seconds"));
        assertEquals(8124, config.getInt("resource-pack.port"), "Modifier の 8123 と分ける");
        assertTrue(config.getBoolean("enabled"));
        assertEquals(ResourcePackService.DEFAULT_PROMPT, config.getString("resource-pack.prompt"));
    }

    @Test
    @DisplayName("ブラックジャックの時間割は Timing.defaults() と一致する")
    void blackjackMatchesDefaults() {
        assertEquals(BlackjackGame.Timing.defaults(), BlackjackGame.Timing.parse(config));
        assertEquals("ディーラー", config.getString("blackjack.dealer-name"));
        assertEquals(BlackjackTable.DEFAULT_MATERIAL.name(), config.getString("blackjack.table-material"));
    }

    @Test
    @DisplayName("ルーレットの設定は Settings.defaults() と一致する")
    void rouletteMatchesDefaults() {
        assertEquals(RouletteGame.Settings.defaults(), RouletteGame.Settings.parse(config));
        assertEquals(RouletteTable.DEFAULT_MATERIAL.name(), config.getString("roulette.table-material"));
    }

    @Test
    @DisplayName("演出の時間割は矛盾していない")
    void spinTimingIsValid() {
        SpinAnimator.Timing timing = new SpinAnimator.Timing(
                config.getInt("spin.reel-tick-interval"),
                config.getInt("spin.first-stop-tick"),
                config.getInt("spin.stop-interval-ticks"),
                config.getInt("spin.lever-reset-tick"),
                config.getInt("spin.glow-ticks"),
                config.getBoolean("spin.fireworks"));
        assertEquals(52, timing.lastStop());
        assertTrue(timing.leverReset() > timing.lastStop());
    }
}
