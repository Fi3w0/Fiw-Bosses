#!/usr/bin/env bash
# Boots a real dedicated server for one loader target with the production jar and the QA
# pack, runs the console smoke commands, restarts once, and fails on any problem.
#
#   .github/scripts/boot-test.sh <target> <mod-jar> [work-dir]
#   e.g. .github/scripts/boot-test.sh fabric-1.21.8 fabric-1.21.8/build/libs/fiw-bosses-fabric-1.21.8-1.3.1.jar
#
# Java: JAVA_17 / JAVA_21 (paths to java executables) or JAVA_HOME_17_X64 / JAVA_HOME_21_X64
# (GitHub runners). 1.20.1 runs on Java 17, everything newer on Java 21.
set -euo pipefail

TARGET="${1:?target, e.g. fabric-1.21.8}"
MOD_JAR="$(cd "$(dirname "${2:?mod jar}")" && pwd)/$(basename "$2")"
REPO="$(cd "$(dirname "$0")/../.." && pwd)"
WORK="${3:-$REPO/build/boot-test/$TARGET}"
COMMANDS="$REPO/.github/boot/commands.txt"
RESTART_COMMANDS="$REPO/.github/boot/restart-commands.txt"
BOOT_TIMEOUT="${BOOT_TIMEOUT:-600}"

prop() { awk -F= -v k="$1" '$1 == k { print $2 }' "$REPO/$TARGET/gradle.properties"; }
fail() { echo "BOOT TEST FAILED ($TARGET): $*" >&2; exit 1; }

LOADER="${TARGET%%-*}"
case "$LOADER" in
  fabric|forge) MC="$(prop minecraft_version)" ;;
  neoforge)     NEO="$(prop neoforge_version)"; MC="1.$(echo "$NEO" | cut -d. -f1).$(echo "$NEO" | cut -d. -f2)"
                MC="${MC%.0}" ;;
  *) fail "unknown loader in target $TARGET" ;;
esac

if [ "$MC" = "1.20.1" ]; then
  JAVA="${JAVA_17:-${JAVA_HOME_17_X64:+$JAVA_HOME_17_X64/bin/java}}"
else
  JAVA="${JAVA_21:-${JAVA_HOME_21_X64:+$JAVA_HOME_21_X64/bin/java}}"
fi
[ -n "$JAVA" ] && [ -x "$JAVA" ] || fail "no Java for Minecraft $MC (set JAVA_17/JAVA_21)"
"$JAVA" -version 2>&1 | head -1

rm -rf "$WORK"
mkdir -p "$WORK/mods" "$WORK/config/fiw_bosses/bosses" "$WORK/config/fiw_bosses/minions" "$WORK/config/fiw_bosses/skins"
cd "$WORK"

download() { curl -fsSL --retry 3 -o "$2" "$1" || fail "download failed: $1"; }

# ── Install the loader's server ─────────────────────────────────────────────
case "$LOADER" in
  fabric)
    INSTALLER="$(curl -fsSL https://meta.fabricmc.net/v2/versions/installer | grep -o '"version": *"[^"]*"' | head -1 | cut -d'"' -f4)"
    download "https://meta.fabricmc.net/v2/versions/loader/$MC/$(prop loader_version)/$INSTALLER/server/jar" server.jar
    FAPI="$(prop fabric_version)"
    download "https://maven.fabricmc.net/net/fabricmc/fabric-api/fabric-api/$FAPI/fabric-api-$FAPI.jar" "mods/fabric-api-$FAPI.jar"
    RUN=("$JAVA" -Xmx2G -jar server.jar nogui)
    ;;
  neoforge)
    download "https://maven.neoforged.net/releases/net/neoforged/neoforge/$NEO/neoforge-$NEO-installer.jar" installer.jar
    "$JAVA" -jar installer.jar --installServer . > installer.log 2>&1 || { tail -20 installer.log; fail "NeoForge installer"; }
    RUN=("$JAVA" -Xmx2G @libraries/net/neoforged/neoforge/$NEO/unix_args.txt nogui)
    ;;
  forge)
    FV="$MC-$(prop forge_version)"
    download "https://maven.minecraftforge.net/net/minecraftforge/forge/$FV/forge-$FV-installer.jar" installer.jar
    "$JAVA" -jar installer.jar --installServer . > installer.log 2>&1 || { tail -20 installer.log; fail "Forge installer"; }
    RUN=("$JAVA" -Xmx2G @libraries/net/minecraftforge/forge/$FV/unix_args.txt nogui)
    ;;
esac

cp "$MOD_JAR" mods/
cp "$REPO"/examples/qa/bosses/*.json config/fiw_bosses/bosses/
cp "$REPO"/examples/qa/minions/*.json config/fiw_bosses/minions/
cp "$REPO"/examples/qa/skins/*.png config/fiw_bosses/skins/
cp "$REPO"/examples/qa/settings.json config/fiw_bosses/settings.json
echo "eula=true" > eula.txt
cat > server.properties <<EOF
online-mode=false
generate-structures=false
spawn-protection=0
view-distance=4
simulation-distance=4
server-port=$((25600 + RANDOM % 1000))
pause-when-empty-seconds=-1
max-tick-time=-1
EOF

# ── Run one server session: wait for Done, feed commands, stop ──────────────
run_session() {
  local log="$1" commands="$2"
  rm -f console.fifo; mkfifo console.fifo
  "${RUN[@]}" < console.fifo > "$log" 2>&1 &
  local pid=$!
  exec 3> console.fifo
  local waited=0
  until grep -q 'Done (' "$log"; do
    kill -0 "$pid" 2>/dev/null || { tail -40 "$log"; fail "server exited before Done ($log)"; }
    [ "$waited" -ge "$BOOT_TIMEOUT" ] && { kill "$pid"; tail -40 "$log"; fail "no Done after ${BOOT_TIMEOUT}s"; }
    sleep 2; waited=$((waited + 2))
  done
  echo "  $log: Done after ~${waited}s"
  while IFS= read -r line || [ -n "$line" ]; do
    case "$line" in
      ''|'#'*) ;;
      'wait '*) sleep "${line#wait }" ;;
      *) echo "$line" >&3; sleep 1 ;;
    esac
  done < "$commands"
  echo "stop" >&3
  local stopped=0
  for _ in $(seq 1 90); do kill -0 "$pid" 2>/dev/null || { stopped=1; break; }; sleep 2; done
  exec 3>&-
  [ "$stopped" = 1 ] || { kill -9 "$pid"; fail "server did not stop ($log)"; }
  wait "$pid" || fail "server exit code $? ($log)"
}

# Lines every loader prints that are not our problem. Keep this list short and specific.
# kqueue: vanilla 1.21.11 probes the macOS/BSD network transport on Linux and NeoForge's debug
# log appender fails to render Netty's debug message about it.
KNOWN_NOISE='No data fixer registered for fiw_bosses:(boss|minion)|Missing (texture|sound)|io\.netty\.channel\.kqueue|Only supported on OSX/BSD'

check_log() {
  local log="$1"; shift
  local bad
  bad="$(grep -nE 'Exception|Caused by:|/ERROR\]|\[ERROR\]|FATAL|Ticking entity' "$log" | grep -vE "$KNOWN_NOISE" || true)"
  [ -z "$bad" ] || { echo "$bad" | head -40; fail "errors in $log"; }
  for want in "$@"; do
    grep -qF -- "$want" "$log" || { tail -60 "$log"; fail "$log is missing: $want"; }
  done
  [ ! -d crash-reports ] || [ -z "$(ls -A crash-reports)" ] || fail "crash report written: $(ls crash-reports)"
}

echo "Boot test $TARGET (Minecraft $MC) in $WORK"
run_session first.log "$COMMANDS"
check_log first.log \
  "boss definitions (0 failed)" "minion definitions (0 failed)" \
  "No config problems found." \
  "Spawned boss qa_domain" "Spawned boss qa_golem_giant" "Spawned boss qa_scale_abilities" \
  "Spawned boss qa_summon" "Not enough room for boss 'qa_huge_room'"

if [ "$MC" != "1.20.1" ]; then
  grep -qE "attribute .*(Scale|scale).* is 2(\.0)?$" first.log || fail "qa_golem_giant scale attribute is not 2.0"
fi

run_session restart.log "$RESTART_COMMANDS"
check_log restart.log "boss definitions (0 failed)" "has the following entity data: 2"

echo "BOOT TEST PASSED ($TARGET, Minecraft $MC)"
