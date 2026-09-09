#!/usr/bin/env bash
#
# 通し確認: 使い捨ての 26.1 サーバー + mstore (KV のみ) を立て、ヘッドレスクライアントで
# 台の設置 → BET 変更 → スピン → 保護 → 交換機 を一周する。観測は bot.cjs が JSON で流す。
#
# 前提:
#   - ../plugin/build/libs/Mamble-*.jar (gradle :plugin:build 済み)
#   - ../../Modifier/e2e/build/paper/paper-26.1.2-*.jar (Modifier の :e2e:e2eTest を一度回すと落ちてくる)
#   - ../../Modifier/e2e/bot/node_modules (同上、mineflayer)
#   - ../../mstore/build/install/mstore/bin/mstore (gradle installDist 済み)
#   - JAVA_HOME (nix develop の中で実行するか、環境変数で渡す)
#
# ヘッドレスクライアントは 26.1 までしか喋れないので、サーバーだけ 26.1 を使う。
# プラグインの jar は本番と同じ 26.2 ビルド。Display エンティティの見え方までは確認できない。
#
set -euo pipefail
HERE="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
ROOT="$(cd "$HERE/../.." && pwd)"
SERVER="$HERE/server"
PORT="${MAMBLE_SMOKE_PORT:-25599}"
KV_PORT="${MAMBLE_SMOKE_KV_PORT:-8080}"
FIFO="/tmp/mamble-smoke-$$.fifo"

say() { printf '\033[36m==>\033[0m %s\n' "$*" >&2; }
die() { printf '\033[31mエラー:\033[0m %s\n' "$*" >&2; exit 1; }

JAR="$(ls -1 "$HERE"/../plugin/build/libs/Mamble-*.jar 2>/dev/null | grep -v sources | sort -V | tail -1 || true)"
[ -n "$JAR" ] || die "Mamble の jar が無い。gradle :plugin:build を先に"
PAPER="$(ls -1 "$ROOT"/Modifier/e2e/build/paper/paper-26.1*.jar 2>/dev/null | head -1 || true)"
[ -n "$PAPER" ] || die "26.1 の Paper が無い ($ROOT/Modifier/e2e/build/paper/)"
[ -d "$ROOT/Modifier/e2e/bot/node_modules" ] || die "mineflayer が無い ($ROOT/Modifier/e2e/bot/node_modules)"
MSTORE="$ROOT/mstore/build/install/mstore/bin/mstore"
[ -x "$MSTORE" ] || die "mstore が未ビルド: $MSTORE"
[ -n "${JAVA_HOME:-}" ] || die "JAVA_HOME が無い。nix develop の中で実行すること"
export PATH="$JAVA_HOME/bin:$PATH"

# ---------------------------------------------------------------- サーバーを組む
rm -rf "$SERVER"
mkdir -p "$SERVER/plugins"
ln -s "$PAPER" "$SERVER/paper.jar"
for d in cache libraries versions; do
    [ -e "$ROOT/Modifier/e2e/build/server/$d" ] && ln -s "$ROOT/Modifier/e2e/build/server/$d" "$SERVER/$d"
done
echo "eula=true" > "$SERVER/eula.txt"
cat > "$SERVER/server.properties" <<PROPS
server-port=$PORT
online-mode=false
level-type=minecraft:flat
generate-structures=false
spawn-protection=0
view-distance=4
simulation-distance=4
spawn-monsters=false
enforce-secure-profile=false
motd=mamble-smoke
PROPS
cp "$JAR" "$SERVER/plugins/"
# config.yml は同梱の既定をそのまま使う (部分的に書くと symbols が無くて起動しない)。
# KV のポートを変えるなら、既定の config.yml を丸ごと置いて mstore.base-url だけ直すこと。
[ "$KV_PORT" = 8080 ] || die "KV のポートは既定の 8080 のみ対応 (config.yml を丸ごと用意するなら外してよい)"

# ---------------------------------------------------------------- 起動
cleanup() {
    [ -n "${PAPER_PID:-}" ] && kill -0 "$PAPER_PID" 2>/dev/null && { echo stop > "$FIFO"; sleep 5; kill "$PAPER_PID" 2>/dev/null || true; }
    [ -n "${HOLDER_PID:-}" ] && kill "$HOLDER_PID" 2>/dev/null || true
    [ -n "${MSTORE_PID:-}" ] && kill "$MSTORE_PID" 2>/dev/null || true
    rm -f "$FIFO"
}
trap cleanup EXIT

say "mstore (KV のみ) を $KV_PORT 番で起動"
"$MSTORE" --kv-only --kv-port "$KV_PORT" --server-dir "$SERVER" --kv-db mstore.db > "$SERVER/mstore.log" 2>&1 &
MSTORE_PID=$!
for _ in $(seq 1 30); do
    curl -fs "http://127.0.0.1:$KV_PORT/health" >/dev/null 2>&1 && break
    sleep 1
done
curl -fs "http://127.0.0.1:$KV_PORT/health" >/dev/null || die "mstore が上がらない ($SERVER/mstore.log)"

mkfifo "$FIFO"
sleep 100000 > "$FIFO" &
HOLDER_PID=$!
say "Paper 26.1 を $PORT 番で起動"
( cd "$SERVER" && java -Xms1G -Xmx2G -jar paper.jar nogui < "$FIFO" > server.log 2>&1 ) &
PAPER_PID=$!
for _ in $(seq 1 60); do
    grep -q "Done (" "$SERVER/server.log" 2>/dev/null && break
    kill -0 "$PAPER_PID" 2>/dev/null || die "Paper が落ちた。$SERVER/server.log を見ること"
    sleep 3
done
grep -q "Done (" "$SERVER/server.log" || die "Paper が起動しない"
grep -q "mstore に接続しました" "$SERVER/server.log" || die "Mamble が mstore に届いていない"

# ---------------------------------------------------------------- ボット
say "ヘッドレスクライアントを参加させる"
export NODE_PATH="$ROOT/Modifier/e2e/bot/node_modules"
node "$HERE/bot.cjs" "$PORT" > "$SERVER/bot.out" 2>&1 &
BOT_PID=$!
for _ in $(seq 1 60); do
    grep -q "Tester joined the game" "$SERVER/server.log" && break
    sleep 0.5
done
echo "op Tester" > "$FIFO"
sleep 0.5
echo "mb credit Tester set 50000" > "$FIFO"
wait "$BOT_PID" || true

# ---------------------------------------------------------------- 結果
echo
echo "== 観測 (bot.out)"
grep -v '"event":"chat"' "$SERVER/bot.out"
echo
echo "== サーバーの例外"
if grep -q "Exception" "$SERVER/server.log"; then
    grep -n -A3 "Exception" "$SERVER/server.log" | head -40
else
    echo "(なし)"
fi
echo
say "サーバーのログ: $SERVER/server.log"
