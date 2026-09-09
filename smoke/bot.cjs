'use strict';
// Mamble の通し確認: 参加 → 台を置く → 残高を貰う → ボタンで BET を変える → レバーを引く → 残高を確かめる。
// 観測を1行1件の JSON で流す。判断はしない。
const mineflayer = require('mineflayer');
const { Vec3 } = require('vec3');

const PORT = Number(process.argv[2] || 25599);
const emit = (event, fields = {}) => process.stdout.write(JSON.stringify({ event, ...fields }) + '\n');
const sleep = (ms) => new Promise((r) => setTimeout(r, ms));

const bot = mineflayer.createBot({ host: '127.0.0.1', port: PORT, username: 'Tester', auth: 'offline', version: '26.1' });
const chat = [];
bot.on('message', (m) => { const s = m.toString(); chat.push(s); emit('chat', { text: s }); });
bot.on('resourcePack', () => { emit('resource_pack_offered'); bot.acceptResourcePack(); });
bot.on('kicked', (r) => { emit('kicked', { reason: JSON.stringify(r).slice(0, 300) }); process.exit(2); });
bot.on('error', (e) => { emit('error', { message: String(e) }); });

const waitChat = async (fragment, timeoutMs = 10000) => {
  const start = Date.now();
  while (Date.now() - start < timeoutMs) {
    const hit = chat.find((l) => l.includes(fragment));
    if (hit) return hit;
    await sleep(100);
  }
  return null;
};

bot.once('spawn', async () => {
  try {
    emit('spawned', { pos: bot.entity.position });
    await sleep(2000);
    // 足元の1つ先に台を置く (基準ブロック = そのマスの下の地面)
    const feet = bot.entity.position.floored();
    const target = feet.offset(2, 0, 3);
    const ground = bot.blockAt(target.offset(0, -1, 0));
    emit('ground', { name: ground && ground.name, at: target });
    bot.chat('/mb slot');
    await sleep(1500);
    const item = bot.inventory.items().find((i) => i.name === 'stone');
    emit('slot_item', { found: !!item, count: item && item.count });
    if (!item) throw new Error('設置用アイテムが来ない');
    await bot.equip(item, 'hand');
    await bot.lookAt(target.offset(0.5, 0.5, 0.5), true);
    await bot.placeBlock(ground, new Vec3(0, 1, 0)).catch((e) => emit('place_error', { message: String(e) }));
    await sleep(1500);
    const base = bot.blockAt(target);
    const head = bot.blockAt(target.offset(0, 1, 0));
    emit('placed', { base: base && base.name, head: head && head.name });
    // レバーの位置は正面の右。台は自分の方 (西) を向くので、右は北 (z-1)
    const candidates = [target.offset(0, 1, -1), target.offset(0, 1, 1), target.offset(1, 1, 0), target.offset(-1, 1, 0)];
    const lever = candidates.map((p) => bot.blockAt(p)).find((b) => b && b.name === 'lever');
    emit('lever', { found: !!lever, at: lever && lever.position });
    // 部品エンティティ
    await sleep(500);
    const parts = Object.values(bot.entities).filter((e) => e !== bot.entity && e.position.distanceTo(target) < 3);
    emit('entities_near', { count: parts.length, names: [...new Set(parts.map((e) => e.name))] });

    bot.chat('/mb');
    emit('balance_before', { line: await waitChat('残高') });
    // ボタン (Interaction) を右クリック
    const interactions = parts.filter((e) => e.name === 'interaction');
    emit('interactions', { count: interactions.length });
    if (interactions.length > 0) {
      // START は一番高い位置の当たり判定。押す前のレバーは断られるはず
      const start = interactions.reduce((a, b) => (a.position.y > b.position.y ? a : b));
      if (lever) {
        chat.length = 0;
        await bot.lookAt(lever.position.offset(0.5, 0.5, 0.5), true);
        await bot.activateBlock(lever);
        await sleep(1500);
        bot.chat('/mb');
        emit('balance_before_start', { line: await waitChat('残高') });
      }
      await bot.lookAt(start.position.offset(0, 0.1, 0), true);
      await sleep(300);
      bot.activateEntity(start);
      await sleep(800);
      // [+] は正面から見て右 = レバーと同じ側。START 以外でレバーに近い方を選ぶ
      const buttons = interactions.filter((e) => e !== start);
      const plus = buttons.reduce((a, b) => (a.position.distanceTo(lever.position) < b.position.distanceTo(lever.position) ? a : b));
      chat.length = 0;
      await bot.lookAt(plus.position.offset(0, 0.1, 0), true);
      await sleep(300);
      bot.activateEntity(plus);
      await sleep(800);
      bot.chat('/mb');
      emit('bet_after_plus', { line: await waitChat('BET') });
    }
    // レバー
    if (lever) {
      chat.length = 0;
      await bot.lookAt(lever.position.offset(0.5, 0.5, 0.5), true);
      await bot.activateBlock(lever);
      await sleep(4000);
      bot.chat('/mb');
      emit('balance_after_spin', { line: await waitChat('残高') });
      // 演出中の再度のレバーは無視されるはず: 連打
      chat.length = 0;
      await bot.activateBlock(lever);
      await sleep(200);
      await bot.activateBlock(lever);
      await sleep(4000);
      bot.chat('/mb');
      emit('balance_after_double_pull', { line: await waitChat('残高') });
    }
    // 壊せないこと
    if (base) {
      await bot.lookAt(target.offset(0.5, 0.5, 0.5), true);
      await bot.dig(base, true).catch((e) => emit('dig_error', { message: String(e) }));
      await sleep(1000);
      emit('after_dig', { base: bot.blockAt(target) && bot.blockAt(target).name });
    }
    // 交換機: 置いて開き、払い出しと預け入れ
    const exTarget = feet.offset(0, 0, 2);
    bot.chat('/mb exchange');
    await sleep(1500);
    const exItem = bot.inventory.items().find((i) => i.name === 'stone' && i.customName && String(i.customName).includes('交換機')) || bot.inventory.items().filter((i) => i.name === 'stone').pop();
    emit('exchange_item', { found: !!exItem });
    if (exItem) {
      await bot.equip(exItem, 'hand');
      await bot.lookAt(exTarget.offset(0.5, 0.5, 0.5), true);
      await bot.placeBlock(bot.blockAt(exTarget.offset(0, -1, 0)), new Vec3(0, 1, 0)).catch((e) => emit('ex_place_error', { message: String(e) }));
      await sleep(1500);
      emit('exchange_placed', { base: bot.blockAt(exTarget) && bot.blockAt(exTarget).name });
      chat.length = 0;
      const opened = new Promise((resolve) => bot.once('windowOpen', resolve));
      await bot.lookAt(exTarget.offset(0.5, 0.5, 0.5), true);
      await bot.activateBlock(bot.blockAt(exTarget));
      const win = await Promise.race([opened, sleep(5000).then(() => null)]);
      emit('exchange_window', { opened: !!win, slots: win && win.slots.filter(Boolean).slice(0, 12).map((s) => s.name) });
      if (win) {
        await bot.clickWindow(9, 0, 0); // 1つ目の品目 (石炭 50) を1個
        await sleep(800);
        await bot.clickWindow(11, 0, 1); // 3つ目 (鉄 1500) をシフトで 16個
        await sleep(800);
        const balanceSlot = win.slots[4];
        emit('exchange_balance_slot', { name: balanceSlot && balanceSlot.name, customName: balanceSlot && String(balanceSlot.customName) });
        const iron = win.slots.findIndex((s, i) => s && i >= 54 && s.name === 'iron_ingot');
        emit('iron_in_inventory', { slot: iron, count: iron >= 0 && win.slots[iron].count });
        if (iron >= 0) {
          await bot.clickWindow(iron, 0, 0); // 1個預ける
          await sleep(800);
        }
        bot.closeWindow(win);
        await sleep(500);
        bot.chat('/mb');
        emit('balance_after_exchange', { line: await waitChat('残高') });
        emit('iron_after', { count: bot.inventory.items().filter((i) => i.name === 'iron_ingot').reduce((n, i) => n + i.count, 0), coal: bot.inventory.items().filter((i) => i.name === 'coal').reduce((n, i) => n + i.count, 0) });
      }
    }
    // ブラックジャック卓: 置いて席に着き、受付を待って配られたらスタンドし、残高が動くこと
    const bjBase = feet.offset(-3, 0, 2);
    bot.chat('/mb blackjack');
    await sleep(1500);
    const bjItem = bot.inventory.items().filter((i) => i.name === 'stone').pop();
    emit('blackjack_item', { found: !!bjItem });
    if (bjItem) {
      await bot.equip(bjItem, 'hand');
      await bot.lookAt(bjBase.offset(0.5, 0.5, 0.5), true);
      await bot.placeBlock(bot.blockAt(bjBase.offset(0, -1, 0)), new Vec3(0, 1, 0)).catch((e) => emit('bj_place_error', { message: String(e) }));
      await sleep(1500);
      const row = [bjBase.offset(-1, 0, 0), bjBase, bjBase.offset(1, 0, 0), bjBase.offset(0, 0, -1), bjBase.offset(0, 0, 1)]
        .map((p) => bot.blockAt(p) && bot.blockAt(p).name);
      emit('blackjack_placed', { blocks: row });
      const near = Object.values(bot.entities).filter((e) => e !== bot.entity && e.position.distanceTo(bjBase) < 4);
      const names = {};
      near.forEach((e) => { names[e.name] = (names[e.name] || 0) + 1; });
      emit('blackjack_entities', names);
      const hits = near.filter((e) => e.name === 'interaction');
      // 当たり判定の高さ: 卓の上 (一番高い) > START (側面の上段) > [−] [+]。START は上から2段目
      const levels = [...new Set(hits.map((h) => Math.round(h.position.y * 20) / 20))].sort((a, b) => b - a);
      const startY = levels[1];
      const starts = hits.filter((e) => Math.abs(e.position.y - startY) < 0.05);
      const center = bjBase.offset(0.5, 0, 0.5);
      const start = starts.reduce((a, b) => (a.position.distanceTo(center) < b.position.distanceTo(center) ? a : b), starts[0]);
      emit('blackjack_start', { starts: starts.length, at: start && start.position });
      if (start) {
        chat.length = 0;
        bot.chat('/mb');
        const before = await waitChat('残高');
        await bot.lookAt(start.position.offset(0, 0.1, 0), true);
        await sleep(300);
        bot.activateEntity(start);
        await sleep(1000);
        // 受付 (既定 10 秒) + 配り (4 枚 × 8 tick) を待つ
        await sleep(10000 + 2500);
        // STAND は卓の上 (一番高い当たり判定) のうち、START の列で真ん中のもの
        const topHitY = Math.max(...hits.map((h) => h.position.y));
        const onTable = hits.filter((e) => Math.abs(e.position.y - topHitY) < 0.05 && e.position.distanceTo(start.position) < 1.2);
        const stand = onTable.reduce((a, b) => (a.position.distanceTo(start.position) < b.position.distanceTo(start.position) ? a : b), onTable[0]);
        emit('blackjack_stand', { candidates: onTable.length });
        if (stand) {
          await bot.lookAt(stand.position.offset(0, 0.1, 0), true);
          await sleep(300);
          bot.activateEntity(stand);
        }
        // ディーラーの手番と結果表示を待つ
        await sleep(6000);
        chat.length = 0;
        bot.chat('/mb');
        emit('blackjack_balance', { before, after: await waitChat('残高') });
      }
    }
    // ルーレット卓: 置いて手前のセルにチップを置き、受付 (既定 20 秒) と回転 (6 秒) を待って残高の変化を見る
    const rlBase = feet.offset(3, 0, -3);
    bot.chat('/mb roulette');
    await sleep(1500);
    const rlItem = bot.inventory.items().filter((i) => i.name === 'stone').pop();
    emit('roulette_item', { found: !!rlItem });
    if (rlItem) {
      await bot.equip(rlItem, 'hand');
      await bot.lookAt(rlBase.offset(0.5, 0.5, 0.5), true);
      await bot.placeBlock(bot.blockAt(rlBase.offset(0, -1, 0)), new Vec3(0, 1, 0)).catch((e) => emit('rl_place_error', { message: String(e) }));
      await sleep(1500);
      emit('roulette_placed', { base: bot.blockAt(rlBase) && bot.blockAt(rlBase).name });
      const near = Object.values(bot.entities).filter((e) => e !== bot.entity && e.position.distanceTo(rlBase) < 5);
      const names = {};
      near.forEach((e) => { names[e.name] = (names[e.name] || 0) + 1; });
      emit('roulette_entities', names);
      const pads = near.filter((e) => e.name === 'interaction');
      // 自分に一番近い当たり判定 (手前の 1:1 のセル) を押す
      const pad = pads.reduce((a, b) => (a.position.distanceTo(bot.entity.position) < b.position.distanceTo(bot.entity.position) ? a : b), pads[0]);
      emit('roulette_pad', { pads: pads.length, at: pad && pad.position });
      if (pad) {
        chat.length = 0;
        bot.chat('/mb');
        const before = await waitChat('残高');
        await bot.lookAt(pad.position.offset(0, 0.05, 0), true);
        await sleep(300);
        bot.activateEntity(pad);
        await sleep(1000);
        chat.length = 0;
        bot.chat('/mb');
        emit('roulette_after_chip', { line: await waitChat('残高'), bets: chat.find((l) => l.includes('ルーレット')) || '' });
        // 回転中の撤去は断られる
        await sleep(20000);
        chat.length = 0;
        await bot.lookAt(rlBase.offset(0.5, 0.5, 0.5), true);
        bot.chat('/mb remove');
        emit('roulette_remove_while_spinning', { line: await waitChat('撤去', 5000) });
        await sleep(12000);
        chat.length = 0;
        bot.chat('/mb');
        emit('roulette_balance', { before, after: await waitChat('残高') });
      }
    }
    emit('done');
  } catch (e) {
    emit('failed', { message: String(e && e.stack || e) });
  }
  bot.quit();
  setTimeout(() => process.exit(0), 500);
});
