<div align="center">

# FIW Bosses

**A data-driven boss framework for Fabric, NeoForge, and Forge. Define multi-phase bosses and minions with 50+ configurable abilities entirely through JSON — no coding, no restarts.**

[![Build](https://github.com/Fi3w0/Fiw-Bosses/actions/workflows/build.yml/badge.svg)](https://github.com/Fi3w0/Fiw-Bosses/actions/workflows/build.yml)
[![Modrinth](https://img.shields.io/modrinth/v/fiw-bosses?label=Modrinth&logo=modrinth&color=00AF5C)](https://modrinth.com/mod/fiw-bosses)
[![Minecraft](https://img.shields.io/badge/Minecraft-1.21.11_·_1.21.8_·_1.21.1_·_1.20.1-62B47A)](https://minecraft.net)
[![License](https://img.shields.io/badge/License-GPL--3.0-blue)](LICENSE)

[![Fabric](https://img.shields.io/badge/Fabric-✓-DBB591)](https://fabricmc.net)
[![NeoForge](https://img.shields.io/badge/NeoForge-✓-F16436)](https://neoforged.net)
[![Forge](https://img.shields.io/badge/Forge_1.20.1-✓-1E2D4A)](https://minecraftforge.net)

[Quick Start](#quick-start) · [Features](#features) · [Abilities](#abilities) · [Documentation](BOSS_CONFIG_DOCS.md) · [Issues](https://github.com/Fi3w0/Fiw-Bosses/issues)

</div>

---

## Overview

FIW Bosses turns JSON files into custom boss encounters. Drop a boss file into `config/fiw_bosses/bosses/`, run `/boss reload`, and your server can spawn it immediately — no code, no restart.

It's a **framework**, not a single hardcoded boss: HP-threshold phases, per-phase ability loadouts, custom minions with their own AI and gear, pre-fight and pre-death dialogue, custom skins and loot, and hot reload — all from one config schema that behaves the same on every supported loader and version.

> **Both-sides mod.** Install the matching jar on the server **and** on every client that joins it.

---

## Quick Start

```bash
# 1. Install the loader for your MC version (+ Fabric API on Fabric)
# 2. Drop the matching FIW Bosses jar into mods/ on server and clients
# 3. Start once, then add a boss file:
```

`config/fiw_bosses/bosses/storm_caller.json`

```json
{
  "id": "storm_caller",
  "displayName": "&b&lStorm Caller",
  "health": 500,
  "speed": 0.28,
  "phases": [
    {
      "hpThresholdPercent": 1.0,
      "abilities": [
        { "type": "chain_lightning", "cooldownTicks": 120, "params": { "bounces": 5, "damage": 10 } },
        { "type": "beam", "cooldownTicks": 180, "params": { "damage": 4, "particle": "minecraft:electric_spark" } }
      ]
    }
  ]
}
```

```
/boss reload
/boss spawn storm_caller
```

Full JSON reference → **[BOSS_CONFIG_DOCS.md](BOSS_CONFIG_DOCS.md)**

---

## Supported Targets

| Loader   | Minecraft | Module             | Java |
| -------- | --------- | ------------------ | ---- |
| Fabric   | 1.21.11   | `fabric-1.21.11`   | 21   |
| NeoForge | 1.21.11   | `neoforge-1.21.11` | 21   |
| Fabric   | 1.21.8    | `fabric-1.21.8`    | 21   |
| NeoForge | 1.21.8    | `neoforge-1.21.8`  | 21   |
| Fabric   | 1.21.1    | `fabric-1.21.1`    | 21   |
| NeoForge | 1.21.1    | `neoforge-1.21.1`  | 21   |
| Fabric   | 1.20.1    | `fabric-1.20.1`    | 17   |
| Forge    | 1.20.1    | `forge-1.20.1`     | 17   |

Fabric builds require **[Fabric API](https://modrinth.com/mod/fabric-api)**. **[Fiw Custom Items](https://modrinth.com/mod/fiw-custom-items)** is optional — when present, equipment and loot can reference its items by `toolId`; when absent, those entries are simply skipped.

---

## Features

### Phase-based fights
Bosses change behavior as their HP drops. Each phase can alter speed, damage, equipment, minion pools, transition messages, sounds, particles, and the active ability set.

### Movement modes
Bosses and custom minions can use configurable movement styles:

| Mode          | Behavior                                            |
| ------------- | --------------------------------------------------- |
| `side` / `normal` | Chase the target and strafe when close          |
| `vanilla`     | Plain Minecraft pathing, no smart-movement overlay  |
| `hit_and_run` | Rush in, then retreat before re-engaging            |
| `guard_point` | Hold near the spawn point, return if pulled away    |
| `phase_walk`  | Occasionally blink to a safe nearby position        |
| `hover`       | Float while keeping pressure and spacing             |
| `sniper`      | Keep distance and strafe at range                   |
| `berserk`     | Fight normally until low HP, then hard-chase        |

### Custom minions
Use true vanilla mobs for simple summons, or **FIW custom minions** with skins, gear, abilities, movement modes, and loot. Set `baseEntity: "custom"` for full features, and `renderEntity` to make a custom minion *look* like a vanilla mob:

```json
{
  "id": "skeleton_duelist",
  "baseEntity": "custom",
  "renderEntity": "minecraft:skeleton",
  "movement": "hit_and_run",
  "abilities": [
    { "type": "dodge", "cooldownTicks": 80, "params": { "taunt": "&7Too slow." } }
  ]
}
```

Custom minions also support `follow_boss` (escort) and `static` (hold position). Vanilla-base minions like `"baseEntity": "minecraft:zombie"` keep native AI and are intentionally less customizable. Bosses follow the same idea: always the FIW boss entity internally, but `baseEntity` / `renderEntity` can render them as any vanilla or modded mob.

### Dialogue & fight flow
Bosses can start inactive and immortal until a player right-clicks them — pre-fight dialogue plays, then combat begins. Pre-death dialogue can hold the boss at 1 HP for final lines before it dies.

### Persistence & reloading
Bosses persist by default, with phase index saved to NBT. `/boss reload` re-reads configs live, and configs you delete are removed from the world on reload.

---

## Abilities

Abilities are small, configurable attack modules. Add them to a phase, set a cooldown, tune the params, and combine them into a moveset — **50+ available**.

**Highlights**

| Ability             | What it does                                                                   |
| ------------------- | ------------------------------------------------------------------------------ |
| `domain`            | Seals players inside a sphere with its own attack set; boss-level cooldown      |
| `phantom_dash`      | Zigzag dashes that clip walls and snap to safe ground                           |
| `particle_tornado`  | Configurable particles, spin, fire, damage, size, and funnel shape             |
| `beam`              | Configurable outer/core particles and max beam length                           |
| `dodge`             | Sidesteps when hit; supports taunts                                            |
| `rift_cleave`       | Tears a delayed damaging line through the ground                                |
| `fear_burst`        | Warden-style soul burst: Darkness, Weakness, Slowness, knockback, optional dmg  |
| `mirror_image`      | Particle decoys, invisibility, position swaps, optional reappear damage         |
| `sacrifice_minion`  | Consumes nearby minions to heal the boss and punish players at the spot         |
| `last_breath`       | Low-HP interruptible channel; releases a huge soul blast if players fail it     |
| `wither_crown`      | Orbiting wither skulls that fire one by one                                     |
| `rewind`            | Records position+health and snaps back a few seconds when low — very strong      |
| `second_wind`       | One-shot auto-revive: negates a fatal blow and restores partial health           |
| `adaptation`        | Grows resistant to whichever damage type has recently hurt it most               |
| `cleanse`           | Strips all debuffs off the boss and briefly blocks new ones                      |
| `blink_strike`      | Telegraphs a target, blinks in, and punishes nearby players                      |
| `curse_bomb`        | Delayed player bombs that force the group to spread out                          |
| `soul_tether`       | Chains players to the boss, pulls them inward, and punishes broken tethers        |

<details>
<summary><strong>All ability IDs</strong></summary>

```text
melee_slash, arc_slash, dodge, slam, aoe_smash, charge, teleport, shield, heal,
ranged_projectile, summon_minions, beam, chain_lightning, orbital, meteor, pull,
flames, freeze, random_message, particle_tornado, swap, shockwave, slash_wave,
sonic_boom, domain, ice_crystal, fire_arrow, crimson_slash, singularity_cannon,
lightning_radial, orb_throw, tracking_orb, moving_tornado, ground_spike,
arrow_rain, potion_field, detect_mark, phantom_dash, guardian_shield,
essence_absorption, judgment_mark, divine_execution, rift_cleave, fear_burst,
mirror_image, sacrifice_minion, last_breath, wither_crown, cleanse, second_wind,
adaptation, rewind, gravity_well, shadow_clone, blink_strike, curse_bomb,
soul_tether
```

</details>

---

## Commands

`/boss` requires op level 2; `/boss reload` requires op level 3.

```text
/boss spawn <id> [x y z]      /boss minion list
/boss list                    /boss minion spawn <id>
/boss reload                  /boss minion kill <id>
/boss kill <id> | all         /boss minion kill all
```

**Config folders**

```text
config/fiw_bosses/bosses/    active boss definitions
config/fiw_bosses/minions/   custom minion definitions
config/fiw_bosses/skins/     local PNG skins
```

---

## Building

Run Gradle with **JDK 21** (the 1.20.1 modules compile against a Java 17 toolchain).

```bash
git clone https://github.com/Fi3w0/Fiw-Bosses
cd Fiw-Bosses
./gradlew build
```

<details>
<summary><strong>Full release-style matrix build</strong></summary>

```bash
./gradlew :core:test \
  :fabric-1.21.11:build :neoforge-1.21.11:build \
  :fabric-1.21.8:build  :neoforge-1.21.8:build \
  :fabric-1.21.1:build  :neoforge-1.21.1:build \
  :fabric-1.20.1:build  :forge-1.20.1:build
```

Output jars land in each module's `build/libs/`. The first Forge build is slow (ForgeGradle prepares Minecraft). Don't pass `--configure-on-demand` for single-loader builds — loader modules depend on `:core` being configured first.

</details>

### Repository layout

```text
core/             Minecraft-free config, text, skin, and shared support code
common-1.21.11/   shared game logic for Minecraft 1.21.11
common-1.21.8/    shared game logic for Minecraft 1.21.8
common-1.21.1/    shared game logic for Minecraft 1.21.1
common-1.20.1/    shared game logic for Minecraft 1.20.1
fabric-*/         Fabric entrypoints, registration, networking, client hooks
neoforge-*/       NeoForge entrypoints, registration, networking, client hooks
forge-1.20.1/     Forge entrypoint and 1.20.1 integration
```

Every supported loader and version builds from this one source tree, and the JSON schema is intended to stay consistent across targets — a boss written for one version should behave the same on the others.

---

## Known Limits

- Many ability visuals still use code-defined particle styles, though some abilities expose particle params.
- Player-skin lookup needs internet access when fetching Mojang skins.
- The shared renderer uses a safe humanoid model — exact slim-arm geometry and every outer-layer detail aren't fully matched yet.

---

<div align="center">

**[Documentation](BOSS_CONFIG_DOCS.md)** · **[Issue Tracker](https://github.com/Fi3w0/Fiw-Bosses/issues)** · **[License (GPL-3.0)](LICENSE)**

Designed and maintained by **Fi3w0** for SMP-style custom fights — shared for anyone who wants stronger boss encounters without building a combat mod from scratch.

</div>
