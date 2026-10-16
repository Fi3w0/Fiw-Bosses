# 1.3.1 regression pack

Use the existing **FIW Test Fabric 1.21.8** and **FIW Test NeoForge 1.21.11** Prism instances (Java 21). Replace their old FIW Bosses 1.3.0 jars with matching **1.3.1** production jars after a successful build; use a fresh disposable world. For a dedicated server, install the matching jar on both server and client. This pack works without Fiw Custom Items, and uses a bundled technical PNG rather than a network skin lookup.

Status on 2026-10-16: all eight dedicated-server launch attempts were blocked before Gradle configuration. No server reached `Done`. These are prepared tests, not completed gameplay tests.

## Build and install

From this repository:

```sh
JAVA_HOME=/Library/Java/JavaVirtualMachines/temurin-21.jdk/Contents/Home ./gradlew :core:test :fabric-1.21.11:build :neoforge-1.21.11:build :fabric-1.21.8:build :neoforge-1.21.8:build :fabric-1.21.1:build :neoforge-1.21.1:build :fabric-1.20.1:build :forge-1.20.1:build
```

The two player-test jars are `fabric-1.21.8/build/libs/fiw-bosses-fabric-1.21.8-1.3.1.jar` and `neoforge-1.21.11/build/libs/fiw-bosses-neoforge-1.21.11-1.3.1.jar`. Do not rename old jars to these names.

Copy `bosses/*.json` directly into `config/fiw_bosses/bosses/`, `minions/*.json` into `config/fiw_bosses/minions/`, and `skins/qa_skin.png` into `config/fiw_bosses/skins/`. Back up the existing `settings.json`, then use this pack's `settings.json` in the test instance. Do not activate the file in `negative/` yet. The schedule creates at most one QA boss near x=8,z=8 once per minute; this is for the disposable world only.

## Required player checks on both instances

Run `/boss reload` as an operator (level 3); other `/boss` commands need level 2.

### 1. Local skins: boss and custom minion

```text
/boss spawn qa_skin_short
/boss spawn qa_skin_full
/boss minion spawn qa_minion_short
/boss minion spawn qa_minion_full
```

- [ ] All four show the same turquoise/orange checker skin, with no missing-file warning. This compares `value: "qa_skin"` and `value: "qa_skin.png"` on both bosses and minions.
- [ ] Reload and spawn again: both spellings still work. Reconnect, or restart the world/server, and compare again.
- [ ] Move `qa_skin.png` out of the active skins folder, reload, and spawn one new skin boss. Expect a missing-file warning and fallback appearance, not a crash. Restore the file, reload and spawn again. Do not judge the already-spawned entity's cached skin when checking the restored file.

Clean up only QA entities:

```text
/boss kill qa_skin_short
/boss kill qa_skin_full
/boss minion kill qa_minion_short
/boss minion kill qa_minion_full
```

### 2. Domain cooldown aliases and reload mid-cast

```text
/gamemode survival
/boss spawn qa_domain
```

Stay within eight blocks. It uses zero configured push/pull/melee damage, without blindness/darkness. Creative and spectator players are excluded from domain capture, so use survival for this check.

- [ ] Phase 1 (`cooldown: 80`) emits `QA legacy pulse` messages while the domain is active.
- [ ] Force phase 2, then allow the current domain to finish and the next one to start:

```text
/boss phase @e[nbt={BossId:"qa_domain"}] set 2
```

It emits `QA alias pulse` at the same cadence as phase 1 (`cooldownTicks: 80`). Count messages over a full domain; do not assume a goal evaluation happens every server tick.

- [ ] Force phase 3: `cooldown: 80` plus `cooldownTicks: 10`. `QA precedence pulse` should keep the legacy cadence, proving `cooldown` wins.
- [ ] Force phase 4: neither inner cooldown key. `QA default pulse` uses the default 40 and should be more frequent than phases 1–3.
- [ ] During a cast, `/boss reload`. No stuck domain, tick exception or permanent movement lock. Finish the fight/recast and verify abilities still work.
- [ ] At collapse, the bundled domain-break sound plays. No resource-pack installation is needed.
- [ ] Stop/restart with this boss still alive. It must keep its configured identity and saved phase, then resume fighting when targeted. `/data get entity @e[nbt={BossId:"qa_domain"},limit=1] BossPhase` is zero-based; the `/boss phase ... set` argument is one-based.

Finish with `/boss kill qa_domain`.

### 3. Schedule, settings and invalid config

```text
/boss schedule list
/boss schedule trigger qa_schedule
/boss schedule trigger qa_schedule
/boss summary on
/boss summary top 2
/boss summary show duration false
```

- [ ] First trigger spawns `qa_schedule` if no earlier scheduled copy is alive; second refuses an extra copy. If the minute timer already spawned it, `/boss kill qa_schedule`, then trigger again. Check at x=8,z=8.
- [ ] Reload and restart: summary settings stay enabled/top=2/duration=false; the schedule still knows its living boss and does not duplicate it. Inspect `config/fiw_bosses/settings.json` and `data/schedules.json` while stopped. Back up all four data files (`spawns`, `lockouts`, `records`, `schedules`).
- [ ] Copy `negative/qa_bad_schedule.json.invalid` into active `bosses/` as `qa_bad_schedule.json`, reload. Expect a warning about setting exactly one of `intervalMinutes`/`times`; that schedule must be disabled while the valid QA schedule still works. Remove this temporary active copy and reload.

## Optional broader smoke checks

- [ ] `/boss minion spawn qa_vanilla_minion` produces a real chicken, verifying the existing vanilla-base command path. Remove it in the disposable world afterwards.
- [ ] `/boss spawn qa_shield`: two pylons appear, boss stays immune until both die, then becomes damageable. Bosses/minions never modify arena blocks.
- [ ] Place a lodestone, hold paper and right-click it: `qa_summon` appears and consumes one paper. A second player-summon is refused while it lives. `/boss limits qa_summon` shows limits; after killing it, server-wide/per-player cooldowns should still apply across restart. Admin `/boss spawn qa_summon` intentionally bypasses these limits.
- [ ] `/boss spawn qa_summon variant hard` has 160 HP. `/boss rage @e[nbt={BossId:"qa_summon"}] start qa_rage`, then `... end`, both work. A real player kill drops one stick and updates `/boss stats qa_summon`; `/boss kill` must not grant raid rewards or update kill records.

## Dedicated-server console workflow

After a successful `runServer` reaches `Done`, enter `console-smoke.txt` one line at a time. It creates a small disposable stone platform at y=79 and force-loads its chunks so tests also work without a player. Its second schedule trigger should be refused. Confirm each command's output before continuing.

For an empty 1.21.11 dedicated test server, set `pause-when-empty-seconds=-1` in that disposable run’s `server.properties` before boot, so the schedule continues ticking without players. Older versions without this setting do not need it.

Use `stop` for a clean restart; after boot, inspect the same entities with `/data get entity`, `/boss limits`, `/boss summary` and `/boss schedule list`. Prefix console commands with `/` only when entering them in player chat. This headless pass cannot check visible skins, a player-targeted domain, right-click summons, loot attribution or client rendering.

Record the instance, first failing step and log lines. All required boxes are deliberately unchecked until actual server/player testing succeeds. No push or release.

## Dedicated-server attempt record

Each command was attempted from this repo on JDK 21 with a writable temporary Gradle cache. All failed before configuration with `FileLockContentionHandler` → `java.net.SocketException: Operation not permitted`. Full logs: `/private/tmp/fiw-runtime-20261016/`.

| Target | Command | Result |
|---|---|---|
| `fabric-1.21.11` | `./gradlew :fabric-1.21.11:runServer --console=plain --stacktrace` | Blocked before configuration |
| `neoforge-1.21.11` | `./gradlew :neoforge-1.21.11:runServer --console=plain --stacktrace` | Blocked before configuration |
| `fabric-1.21.8` | `./gradlew :fabric-1.21.8:runServer --console=plain --stacktrace` | Blocked before configuration |
| `neoforge-1.21.8` | `./gradlew :neoforge-1.21.8:runServer --console=plain --stacktrace` | Blocked before configuration |
| `fabric-1.21.1` | `./gradlew :fabric-1.21.1:runServer --console=plain --stacktrace` | Blocked before configuration |
| `neoforge-1.21.1` | `./gradlew :neoforge-1.21.1:runServer --console=plain --stacktrace` | Blocked before configuration |
| `fabric-1.20.1` | `./gradlew :fabric-1.20.1:runServer --console=plain --stacktrace` | Blocked before configuration |
| `forge-1.20.1` | `./gradlew :forge-1.20.1:runServer --console=plain --stacktrace` | Blocked before configuration |
