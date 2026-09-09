# Mamble

Minecraft Paper 用プラグイン。**スロット台**と**交換機**を追加する。

| モジュール | 中身 |
| --- | --- |
| `plugin/` | Paper プラグイン `Mamble` |
| `pack/` | リソースパック (シンボルとアイコン) |
| `tools/` | テクスチャの生成スクリプト |

開発環境は [`Modifier`](../Modifier) と同じ構成 (JDK 25 / Gradle 9 / `flake.nix` の Paper テストサーバー)。
クレジットの保存先に [`mstore`](../mstore) を使う。

## 遊び方

1. 交換機を右クリックし、持ち物の石炭・銅・鉄・金・ダイヤ・ネザライトの欠片・ネザライトインゴットを
   クリックして**クレジットに預ける** (1 / 10 / 30 / 50 / 100 / 6400 / 12800)。
2. スロット台の [−] [+] で **BET** を選ぶ (10〜1000 の 10 刻み。スニークしながら押すと 100 ずつ)。`/mb bet <額>` でもよい。
3. **レバーを引く**。5マスのシンボルが回り、左から順に止まる。同じシンボルが5マス中**どこかに3個以上**あれば当選。
4. 配当 = BET × 倍率。当選すると光と音と花火が出て、残高に加算される。
5. 交換機で品目をクリックすると、クレジットを**アイテムに払い出す** (単価は預け入れと同じ)。

| シンボル | 出現率 | ×3 | ×4 | ×5 |
| --- | ---: | ---: | ---: | ---: |
| チェリー | 24% | 2 | 6 | 20 |
| レモン | 18% | 4 | 12 | 40 |
| オレンジ / プラム | 14% | 6 | 16 | 60 |
| ブドウ | 10% | 8 | 20 | 80 |
| スイカ | 8% | 12 | 40 | 200 |
| ベル | 5% | 16 | 50 | 300 |
| BAR | 4% | 18 | 60 | 500 |
| 7 | 3% | 20 | 80 | 1000 |

この表で**還元率 94.1%、当選率 19.6%** (`SymbolTable#expectedReturn`、`RtpSimulationTest` が見張っている)。
5マスは独立に抽選し、ちょうど k 個が出る確率は二項分布 C(5,k) p^k (1−p)^(5−k)。5マスなので2種類が
同時に3個以上になることは無い。

BET の上限は 1000 なので、7 が5個揃っても払い出しは 100 万クレジットで止まる。

## コマンド

`/mamble` — 別名 `/mb`

| サブコマンド | 権限 | 動き |
| --- | --- | --- |
| (なし) | `mamble.use` (既定: 全員) | 自分の残高と BET |
| `bet <額>` | `mamble.use` | BET を変える (`bet.min`〜`bet.max` の `bet.step` 刻み) |
| `slot` | `mamble.admin` (既定: OP) | スロット台の設置用アイテムを渡す |
| `exchange` | `mamble.admin` | 交換機の設置用アイテムを渡す |
| `blackjack` | `mamble.admin` | ブラックジャック卓の設置用アイテムを渡す |
| `roulette` | `mamble.admin` | ルーレット卓の設置用アイテムを渡す |
| `remove` | `mamble.admin` | 視線の先 (5m) の台を撤去し、設置用アイテムを返す |
| `reward add <item_id> <価格>` / `remove <item_id>` / `list` | `mamble.admin` | 交換機の品目 |
| `credit <player> set\|add <額>` | `mamble.admin` | オンラインの人の残高を直す (補償・動作確認) |
| `status` | `mamble.admin` | 版・mstore 疎通・台の数・還元率・パック配信・シンボルの土台 |
| `reload` | `mamble.admin` | `config.yml` と `rewards.yml` を読み直す |

**台を置くこと自体は `mamble.place` (既定: 全員)。** 作業台で作った設置用アイテムを非 OP がそのまま置ける。
コマンドでアイテムを貰うことと撤去 (`/mb remove`) は `mamble.admin` のまま。

## クラフトレシピ

設置用アイテムはコマンドのほか、作業台でも作れる (参加時にレシピ本へ全部載る)。真ん中は必ずダイヤモンド。
「木」は板材ならどれでも (`minecraft:planks` タグ)、「緑の粘土」は緑色のテラコッタ。

| 台 | 1 段目 | 2 段目 | 3 段目 |
| --- | --- | --- | --- |
| スロット台 | 石 石 石 | リンゴ ダイヤ リンゴ | 鉄ブロック ×3 |
| ブラックジャック卓 | (空) 腐った肉 (空) | 紙 ダイヤ 紙 | 板材 ×3 |
| ルーレット卓 | 雪玉 赤の染料 黒の染料 | 金インゴット ダイヤ ダイヤ | 緑色のテラコッタ ×3 |
| 交換機 | 石 石 石 | 石 ダイヤ 石 | 石 石 石 |

形と材料は `MambleRecipes.SPECS` にあり、`MambleRecipesTest` が見張る。

## スロット台

設置用アイテム (`/mb slot`) を置くと、その場所を下段にして**石3段 + レバー**の台になる。
正面は置いた人の方を向き、レバーは正面から見て**右**の面に付く。上2段とレバーの位置、正面の3マスが
空いていないと置けない (設置用アイテムは消費しない)。

```
 正面から見る                上から見る (facing = SOUTH)
┌────────────┐                  N
│    ×3 ×4 ×5│ ← 上段: 配当表   ┌───┐
│🍒   2  6 20│   (9行)          │石 ├ レバー (東)
│ …          │                  └───┘
├────────────┤                    S ← プレイヤー
│🍒🍋🍊🍇 7  │ ← 中段: リール
├────────────┤
│  ▶ START   │ ← 待機中は START、遊んでいる間は操作者名
│ ◎ 残高 1,234│
│[−]  100 [+]│ ← BET とボタン
└────────────┘
```

### 表示は全部 Display エンティティ

| 部品 | 種類 | 内容 |
| --- | --- | --- |
| 配当表のアイコン ×9 | `ItemDisplay` (scale 0.08) | 上段。シンボル表の順に1行ずつ |
| 配当表の倍率 ×3列 | `TextDisplay` (右寄せ、10行) | 上段。見出し ×3 ×4 ×5 と、シンボルごとの倍率 |
| リール ×5 | `ItemDisplay` (FIXED, scale 0.18) | 中段。シンボルのアイテム (`item_model mamble:symbol/<id>`) |
| コイン | `ItemDisplay` (scale 0.14) | 残高の左に出すクレジットのアイコン (`item_model mamble:coin`、元絵は `../tx/m_coin.png`) |
| START / 操作者名 | `TextDisplay` | 待機中は緑の **▶ START**。押した人の名前に変わる |
| START の当たり判定 | `Interaction` (幅 0.6) | 右クリックで操作者になる |
| 残高 / BET | `TextDisplay` | 操作者の残高・BET |
| [−] [+] | `TextDisplay` (背景付き) | ボタンの見た目 |
| [−] [+] の当たり判定 | `Interaction` (0.26 角) | 右クリックで BET を 10 下げる / 上げる。スニークで 100 |

全26体の部品に PDC `mamble:machine` (台の id) と `mamble:part` (部品名) を刻み、これを索引にする
(`MachineLayout` が向きごとの座標を、`MachinePanel` が生成と更新を担う)。

残高はプレイヤーごとの値なので、台には**操作者** (START を押した人) のものを出す。待機中はレバーも
[−] [+] も「START を押してください」と断る。コインを入れる (下記) と START を押したのと同じ扱い。
`machine.operator-timeout-seconds` (既定 60) 触られなければ待機中に戻り、また START が出る。
本人にはアクションバーでも送る。

### 台での預け入れ

交換機の品目 (石炭など) を手に持って台の**石**を右クリックすると、その場で 1 個がクレジットになる。
スニークしながらなら持っているスタック全部。判定は交換機と同じ (`ExchangeService#depositStack`)。
レバーは持ち物に関係なくスピン。

### スピン

レバーの右クリック (`PlayerInteractEvent`) を**取り消して**自前で処理する。バニラのトグルを通さないので、
レッドストーンには信号が出ない。

1. 演出中なら無視。待機中 (START 前) なら断る。mstore に届いていなければ「休止中」。残高が BET 未満なら断る。
2. **この時点で確定**: 残高から BET を引き、5マスを抽選し、当選なら配当を足す。帳簿への書き込みは1件。
3. 演出 (`SpinAnimator`、config の `spin.*`):

| tick | 起きること |
| ---: | --- |
| 0 | レバーを倒す (物理更新なし)。カチッ + 始動音 |
| 0〜 | 2 tick ごとに回転中のリールを差し替え、カラカラ音 |
| 20 | リール1 停止 (以後 8 tick おきに 2〜5)。停止ごとに火花と音 |
| 52 | 決着。当選なら当たったマスと、配当表の該当シンボル・倍率が 60 tick 光る。外れなら低い音と煙 |
| 60 | レバーが戻り、次のスピンを受け付ける |

当選の派手さは3段階。小当たり (×3) は経験値音と花火パーティクル、打ち上げ花火 1発。
中当たり (×4 か倍率 20 以上) はベルと花火 3発。大当たり (×5 か倍率 100 以上) はチャレンジ達成音と
ドラゴンの咆哮、花火 6発、5個揃いは全員へ知らせる (`spin.broadcast-five-of-a-kind`)。
打ち上げ花火は `mamble:firework` の印が付いており、**誰にもダメージを与えない**。

結果は演出の前に残高へ入っているので、演出中に再起動・ログアウト・撤去が起きても払い戻しは要らない。

### 保護と復元

- 台の石・レバー・正面の空きマスは、破壊・設置・爆発・ピストン・延焼・流体・エンティティによる変化から守る。
  部品エンティティは殴れず、ダメージも受けない。撤去は `/mb remove` だけ。
- 台の位置は `plugins/Mamble/machines.yml`。**チャンクの読み込み時**に、欠けたブロックを置き直し、
  欠けた部品を作り直し、配置を変えた部品を今の座標・縮尺へ寄せ、台に属さない部品の残骸を消す
  (`MachineBuilder#restoreIn`)。`MachineLayout` の数値を直せば、既にある台にも次の読み込みで効く。
- 起動時に、今あるワールドに属さない行は捨てる (wiah がワールドを作り直した後の掃除)。

## ブラックジャック卓

設置用アイテム (`/mb blackjack`) を置くと、置いた場所を中央にして**横3ブロックの卓** (素材は
`blackjack.table-material`、既定はダークオークの板材) になる。席は3つ: **中央の席は正面**、
**左右の席は卓の両端の外側** (卓を三方から囲む形)。奥の中央に**ディーラーの村人**
(AI なし・無敵・無音・取引なし) が立つ。正面は置いた人の方を向く。

```
            [ディーラー]          奥 (空気、守る)
   [席0] [左][中央][右] [席2]    卓。カードはこの上に平置き。席0 / 席2 は端の外側
              [席1]             正面の席
               ↓ プレイヤー
```

各席のパネルとカードは、その席が向いている面を基準に置く (`MachineLayout.Part#side`)。

### 遊び方

1. 席の前の側面 (左右の席は卓の端の面) にある **▶ START** を右クリックして座る。同時に次のラウンドへの参加になる。
2. 誰かが START を押すと **10 秒の受付** (`blackjack.join-seconds`) が始まり、その間に他の席も参加できる。
   受付の残り秒数はディーラーの名札に出る。
3. 配られたら左の席から順に手番。卓の上の手前に平置きされた **HIT / STAND / DOUBLE** を右クリック
   (押した選択肢は金色で残る)。手番は 30 秒
   (`blackjack.turn-seconds`) で自動スタンド。
4. 全員の手番が終わるとディーラーが伏せ札を開き、17 になるまで引く (ソフト 17 で止まる)。
5. 精算して数秒 (`blackjack.result-seconds`) 結果を見せ、待機に戻る。座ったまま START を押さないと
   60 秒 (`blackjack.seat-timeout-seconds`) で席が空く。

### ルール

- 掛け金は席の BET (スロットと共通、`bet.*`)。**配る時点で差し引く**。払えない席はそのラウンドを飛ばす。
- ヒット / スタンド / ダブルダウンのみ。ダブルは最初の2枚のときだけ、追加の掛け金を払えるときだけで、1枚引いて終わり。
- ブラックジャック (最初の2枚で 21) は **3:2**。双方 BJ は引き分け。ディーラーだけ BJ なら 21 でも負け。
- バーストは即負け。引き分けは掛け金が戻る。勝ちは 2 倍が戻る。
- 1ラウンドごとに 52 枚を切り直す。ディーラーの伏せ札は全員の手番が終わるまで開かない。
- 退出した人はスタンド扱いで、その手はそのまま勝負する (残高は mstore にあるので書ける)。
- 台に bet アイテムを持って右クリックすると預け入れになる (スロット台と同じ)。

### 表示 (全 74 体の Display / Interaction / 村人)

| 部品 | 種類 | 内容 |
| --- | --- | --- |
| 席ごとの側面 | `TextDisplay` ×5 + `Interaction` ×3 | START / 名前と状態、コイン、残高、[−] BET [+] |
| 席ごとの卓上のボタン | `TextDisplay` ×3 (平置き) + `Interaction` ×3 | HIT / STAND / DOUBLE。手前の帯に 0.33 間隔。手番なら白、押したものは金色、押せないものは暗い |
| 席ごとのカード ×6 と合計 | `ItemDisplay` (平置き、scale 0.28) + `TextDisplay` | ボタンの奥に重ねて並べ、合計は左端。7枚目以降は最後の枠に重なる |
| ディーラーのカード ×6 と合計 | 同上 | 中央の列の奥。伏せ札は裏面 |
| ディーラー | `Villager` | 名札にラウンドの状態と受付の残り秒数 |

`MachineLayout.Part` に `column` (横のブロック位置) と `pitch` (平置きは −90°) を足してある。
配置は `BlackjackLayout`、描画は `BlackjackPanel`、進行は `BlackjackGame` (純粋ロジック) と
`BlackjackService` (Event を表示・音・帳簿につなぐ)。

> **退出者の精算。** ラウンド中に抜けた人は掛け金を持ったままなので、その人の帳簿のキャッシュを
> ラウンドが終わるまで保持する (`MachineListener#onQuit` → `BlackjackService#playerLeft` が true なら
> `ledger.unload` しない)。途中で戻ってきた場合も読み直さない。停止時は掛け金を返してから帳簿を閉じる。

## ルーレット卓

設置用アイテム (`/mb roulette`) を置くと、置いた場所を手前の行の左から 2 番目にして **4 幅 × 3 奥行きの卓**
(素材は `roulette.table-material`、既定は緑のコンクリート) になる。手前 2 行が賭けの配置、奥の行が
ホイール。正面は置いた人の方を向き、プレイヤーは手前の 4 マスに立つ。

```
              [ ホイール ]                      ← 奥の行。上に掲示板 (状態・秒読み・履歴)
   [ ] [ 3][ 6][ 9] … [36] [2:1]
   [0] [ 2][ 5][ 8] … [35] [2:1]               ← 番号 (35:1) とコラム (2:1)
   [ ] [ 1][ 4][ 7] … [34] [2:1]
       [ 1st 12 ][ 2nd 12 ][ 3rd 12 ]           ← ダース (2:1)
       [1-18][偶数][ 赤 ][ 黒 ][奇数][19-36]     ← 1:1
           ↑ プレイヤー
```

### 遊び方

1. セルを右クリックすると、今の BET 額のチップを 1 枚置く (置いた時点で残高から引く)。
   スニークしながら右クリックで自分のチップを 1 枚戻す。1 スピンに置けるのは `roulette.max-chips` 枚 (既定 20)。
2. 最初のチップから **20 秒** (`roulette.bet-seconds`) の受付。残り秒数は掲示板に出る。
3. 時間切れでホイールが回り、玉が逆向きに回って当たりのポケットで止まる (`roulette.spin-ticks`、既定 120 tick)。
   回転中と結果表示中は置けない。
4. 精算: 当たったセルの掛け金 × (倍率 + 1) が戻る。単番号 35:1、ダース・コラム 2:1、1:1 の 6 種。
   **0 が出たら外賭けは全部負け** (ヨーロピアン、還元率 97.3%)。当たったセルは金色になり、5 秒
   (`roulette.result-seconds`) 見せてからチップが消える。
5. 自分のチップは `/mb` で見られる。セルには全員ぶんの掛け金の合計が出る。

### 表示 (全 101 体)

| 部品 | 種類 | 内容 |
| --- | --- | --- |
| セル ×49 | `TextDisplay` (平置き、赤/黒/緑の背景) | 名前と、置かれている掛け金の合計。当たりは金色 |
| 当たり判定 ×49 | `Interaction` (幅 0.22、**高さ 0.05**) | 卓の上面に敷く。低いのは、奥のセルを狙う視線を手前の判定が遮らないため |
| ホイール | `ItemDisplay` (平置き、scale 0.96) | `tools/RouletteWheelGen.java` が描く 512x512。回転は `rightRotation` (法線まわり) |
| 玉 | `ItemDisplay` (雪玉) | Transformation の平行移動で円周上を動く |
| 掲示板 | `TextDisplay` | ホイールの上の空間に正面向き |

当たり判定を外して卓を直接右クリックしても、クリックした位置からセルを引く (`RouletteService#clickAt`)。

演出 (`RouletteSpin`): ホイールは 3 周、玉は逆向きにリムを 5 周以上まわり、回転の 75% で当たりのポケットの
真上に来て、以後はホイールと一緒に回りながら内側へ落ちる。減速は `1 − (1−u)²`。2 tick ごとに補間付きで送る。
結果は回転の始めに決まっており、演出はそれを見せるだけ。

> ホイールの絵の向き (鏡像・0 の位置) は実機で確かめる。ずれていれば `RouletteSpin.TEXTURE_CLOCKWISE` と
> `ZERO_OFFSET_DEG` で直す。0 のポケットの外側に白い印を付けてある。

退出した人のチップも精算する (ブラックジャックと同じく `LedgerHolds` で帳簿のキャッシュを保持)。
停止時は精算前のチップを返してから帳簿を閉じる。

## クレジット (`CreditLedger`)

mstore のキー `mamble/credit/<uuid>` (残高) と `mamble/name/<uuid>` (最後に見た名前)。接頭辞は `key-prefix`。

**メモリのキャッシュが正。** 参加時に読み込み、増減は main スレッドでキャッシュに適用して即答し、
mstore への書き込みは専用の1スレッドへ順に流す。書き込みは投げた時点の値を持って行くので、
古い値が新しい値を上書きすることは無い。停止時は `flush-timeout-seconds` まで書きかけを待つ。

mstore に届かない間は、全台が**休止中**になり交換機も開かない。30秒ごとに挑み直し、届いたら戻る。

```console
$ curl localhost:8080/kv/mamble/credit/<uuid>
$ sqlite3 run/mstore.db 'select key, cast(value as text) from kv where key like "mamble/%";'
```

## 交換機

設置用アイテム (`/mb exchange`) を置くと石1つの台になり、上に「交換機」、正面にコインが出る。
右クリック (石でもコインでも) で 54 スロットの画面が開く。

- 上段: 残高 (コインのアイコン) と使い方。2行目以降: 品目。
- **預け入れ**: 自分の持ち物の対象アイテムをクリック (1個) / シフトクリック (そのスタック全部)。
  名前やエンチャントの付いたものは受けない。
- **払い出し**: 品目をクリック (1個) / シフトクリック (`exchange.bulk-amount` 個、既定 16)。
  足りなければ買える分だけ。持ち物に入りきらない分は足元に落とす。
- 品目は `plugins/Mamble/rewards.yml`。初回起動で既定7品目を書き出す。既定品目も `reward remove` で外せる。

## プラグイン設定 (`plugins/Mamble/config.yml`)

| キー | 既定値 | 説明 |
| --- | --- | --- |
| `enabled` | `true` | `false` なら何もしない |
| `message-prefix` | `[Mamble] ` | 返答の接頭辞 (MiniMessage) |
| `mstore.base-url` / `token` / `timeout-seconds` / `attempts` | `http://127.0.0.1:8080` / `""` / `3` / `3` | 記録先 |
| `key-prefix` | `mamble` | キーの接頭辞 |
| `flush-timeout-seconds` | `5` | 停止時に書き込みを待つ時間 |
| `bet.min` / `bet.max` / `bet.step` / `bet.default` | `10` / `1000` / `10` / `10` | BET の範囲と刻み |
| `symbols.<id>.name` / `weight` / `pays` | 上の表 | weight の合計が 100 でなければ起動しない |
| `spin.reel-tick-interval` / `first-stop-tick` / `stop-interval-ticks` / `lever-reset-tick` / `glow-ticks` | `2` / `20` / `8` / `60` / `60` | 演出の時間割 |
| `spin.fireworks` / `broadcast-five-of-a-kind` | `true` / `true` | 打ち上げ花火 / 5個揃いの周知 |
| `machine.operator-timeout-seconds` | `60` | 操作者の表示を消すまで |
| `blackjack.dealer-name` / `table-material` | `ディーラー` / `DARK_OAK_PLANKS` | ディーラーの名前、新しい卓の素材 |
| `blackjack.join-seconds` / `turn-seconds` / `seat-timeout-seconds` / `result-seconds` | `10` / `30` / `60` / `5` | 受付、手番、席の時間切れ、結果表示 |
| `blackjack.deal-interval-ticks` / `dealer-draw-interval-ticks` | `8` / `12` | 配る間隔、ディーラーが引く間隔 |
| `roulette.table-material` / `bet-seconds` / `spin-ticks` / `result-seconds` / `max-chips` | `GREEN_CONCRETE` / `20` / `120` / `5` / `20` | ルーレット卓 |
| `exchange.title` / `bulk-amount` | `交換機` / `16` | 交換機の画面 |
| `resource-pack.*` | Modifier と同じ形、`port: 8124` | パックの配信 |

## リソースパック

`pack/` がそのままリソースパックになる。`gradle :plugin:build` が zip にして jar へ同梱し、
起動時にプラグインが HTTP で配る (Modifier と同じ仕組み。`ResourcePackHost`)。

```
pack/assets/mamble/
├── items/symbol/<id>.json   # item_model の指す先 (9種)
├── items/{coin,slot_machine,exchange,blackjack,roulette,roulette_wheel}.json
├── items/card/<rank>_<suit>.json, items/card/back.json   # 53 枚 (tools/CardPackGen.java が生成・加工)
├── models/item/<id>.json, models/item/card/<name>.json
└── textures/item/<id>.png, textures/item/card/<name>.png   # 32x32 か 128x128 (正方形で2の冪なら混ざってよい)
```

**Modifier が居るサーバーでは、Mamble は自分ではパックを送らない。** 2 つのパックを別々に push すると、
クライアントは先のパックの確認を破棄 (DISCARDED) してしまう (1 回の要求に束ねても同じ)。そこで
**Modifier のビルドが隣の `mamble/pack` を自分の zip に取り込み**、Modifier が 1 つのパックとして送る
(`Modifier/plugin/build.gradle.kts` の `resourcePackZip`)。Modifier を更新するときは Mamble の素材も一緒に入る。
Modifier が居なければ Mamble が自分で送る。HTTP の配信は起動時に始めるので、単独で使うときは
公開サーバーで `resource-pack.host` を変え、8124 番を開けること。

パック無しのクライアントには土台アイテムの見た目で出る。シンボルごとに違う土台にしてあるので
区別はつく (`/mb status` に一覧が出る)。

元絵の置き場は `../tx/`。チェリー・レモン・オレンジ・プラム・ブドウ・BAR・7 とコイン (`m_coin.png`) は
そこにある 32x32 のドット絵をそのまま使う。**ベルとスイカはバニラのモデル** (`minecraft:item/bell`、
`minecraft:block/melon`) を `items/symbol/<id>.json` から指すだけで、自前のテクスチャは持たない。
設置用2種はまだ絵が無いので、`tools/TextureGen.java` の 16x16 ドット絵を 8倍に拡大した仮の絵
(`../tx/mamble/`) を置いてある。
絵ができたら `../tx/` に置き、`pack/assets/mamble/textures/item/<id>.png` へコピーする。

```console
$ java tools/TextureGen.java ../tx/mamble    # 仮の絵を作り直す (slot_machine, exchange, blackjack)
$ java tools/CardPackGen.java ../tx/trump pack   # トランプ 53 枚を加工して pack へ展開
$ java tools/RouletteWheelGen.java pack           # ルーレットのホイールの絵とモデル
```

トランプの元絵 (`../tx/trump/`、1024x1536 で枠の外が木目) は `CardPackGen` が加工する: 画像の縁から続く
背景を塗りつぶして透過にし (RGB 距離 48 以内)、正方形の透明な台紙の中央に置いて 256x256 へ縮める。
縦長のカードなので左右が透明の余白になり、`ItemDisplay` では 2:3 のカードとして見える。
元絵を差し替えたら上のコマンドを実行し直す。

## 開発

```console
$ cd .. && ./start.sh        # 5つのプラグイン + mstore で本番相当を起動
$ nix develop
$ gradle :plugin:build       # テスト込み
$ mamble-dev                 # Mamble だけを載せた Paper を ./run で起動 (mstore は別に立てる)
```

`mamble-dev` は素の Paper なので mstore が無く、台は休止中になる。残高まで試すなら
`../mstore/build/install/mstore/bin/mstore --kv-only` を隣で動かすか、親の `start.sh` を使う。

### テスト

| ファイル | 見るもの |
| --- | --- |
| `SymbolTableTest` | 還元率・当選率の計算、抽選の偏り、表の検証、序列 |
| `SlotLogicTest` | 3/4/5 個の判定、2個では外れ、3個が2個に勝つ、種の固定 |
| `RtpSimulationTest` | 100万スピンで計算どおりの還元率になる |
| `BetStepsTest` | 刻みの上下と端、スニークの 10 段、範囲外の値 |
| `MachineLayoutTest` | 4方位のレバー位置、部品座標、yaw、部品一式 |
| `CreditLedgerTest` | 読み込み・増減・直列化・再試行・疎通 (`FakeKvClient`) |
| `RewardTableTest` | 既定7品目、追加削除、ファイル往復 |
| `DefaultConfigTest` | 同梱 config.yml の既定がコードと一致 |
| `CardsTest` | 52 枚、山札の切り方、手札の点数 (ソフト / ハード)、ディーラーの引き方、勝敗と配当 |
| `BlackjackGameTest` | 受付 → 配り → 手番 → 精算の一周、バースト即負け、BJ 3:2、ダブル、時間切れ、断り、退出、中断、3人の順番 |
| `BlackjackLayoutTest` | 席ごとの部品、当たり判定の名前、卓のブロックと席・ディーラーの位置 |
| `RouletteRulesTest` | ホイールの並びと色、セルの含む番号の数、0 は外賭けに含まれない、配当と期待値 36/37 |
| `RouletteSpinTest` | 37 番号 × 開始角で落ちた後は玉が当たりの真上、3 周と 5 周、1 tick の動きが 180° 未満 |
| `RouletteGameTest` | 受付 → 回転 → 精算 → 待機の一周、0 の外賭け全敗、回転中の拒否、戻すと返金、上限、複数人、退出、中断 |
| `RouletteLayoutTest` | セル 49 と当たり判定 49、番号の配置、クリック位置からセル、卓のブロックと守る空気 |
| `PackAssetsTest` | 定義の `model` を辿り、シンボル・アイコン・カード 53 枚のモデルとテクスチャが揃い、正方形で2の冪 |

> `Material#isAir` はレジストリ越しなので、テストでは本物の値を返さない。`RewardTable` は名前で空気を判定している。

### 通し確認 (`smoke/run.sh`)

```console
$ nix develop
$ smoke/run.sh
```

使い捨ての 26.1 サーバーと mstore (KV のみ) を立て、ヘッドレスクライアント (mineflayer) が一周する。
観測は JSON で流れ、最後にサーバーの例外の有無を出す。26.1 の jar と mineflayer は Modifier の
`:e2e:e2eTest` が落としてきたものを借りる。

確認済み (2026-09-04):

- 設置用アイテムを置くと石3段 + レバーになり、正面は置いた人の方を向く。部品 26 体が出る
- START 前のレバーは断られ、START を押すと遊べる
- [+] の `Interaction` を右クリックすると BET が上がる (10 → 30)
- レバーで残高が BET ぶん動く。演出中の連打は無視される (2回引いても 1 スピン)
- 台の石は掘れない
- 交換機の画面が開き、品目が表の順に並ぶ。クリックで 1 個、シフトで 16 個の払い出し、持ち物のクリックで預け入れ
- 残高が mstore に書かれる (`mamble/credit/<uuid>`)
- 再起動後、欠けたブロックが置き直され、部品が揃う
- ブラックジャック卓を置くと村人 1 体と部品 73 体が出て、START → 受付 → 配り → STAND → 精算で残高が動く
- サーバー側に例外が出ない

> 台の下段は**バニラの設置に任せている**。`BlockPlaceEvent` を取り消すとイベント後に元の状態へ戻され、
> こちらが置いた石まで消えるため。上段・レバー・部品は次の tick に組む。

### まだ実機で確かめていないこと

Display エンティティの見え方はヘッドレスクライアントでは検証できないので、クライアントで入って確かめる。

1. `ItemDisplay` (FIXED, scale 0.18) で5マスが1ブロック幅に収まり、絵が正面を向くか (鏡像なら yaw を 180 足す)。
2. `Interaction` の当たり判定がクライアントの視線でもブロックより先に取れるか (サーバー側の右クリックは通っている)。
3. `TextDisplay` の3行が下段に収まるか (`MachineLayout.SLOT_PARTS` の v と scale で調整)。
4. レバーを `setBlockData(…, false)` で倒したとき、隣のレッドストーンが反応しないか。
5. Modifier と Mamble のパックが両方適用されるか。
