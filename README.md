# FIW Bosses

> A JSON-powered boss framework for Minecraft servers that want real fights, custom phases, minions, loot, dialogue, skins, and 48 configurable abilities without writing Java.

[![Modrinth](https://img.shields.io/modrinth/v/fiw-bosses?label=Modrinth&logo=modrinth&color=00AF5C)](https://modrinth.com/mod/fiw-bosses)
[![Minecraft](https://img.shields.io/badge/Minecraft-1.21.11_|_1.21.8_|_1.21.1_|_1.20.1-62B47A)](https://minecraft.net)
[![NeoForge](https://img.shields.io/badge/NeoForge-21.11_|_21.8_|_21.1-F16436)](https://neoforged.net)
[![Fabric](https://img.shields.io/badge/Fabric-1.21.11_|_1.21.8_|_1.21.1_|_1.20.1-DBB591)](https://fabricmc.net)
[![Forge](https://img.shields.io/badge/Forge-1.20.1_47.x-1E2D4A)](https://minecraftforge.net)
[![License](https://img.shields.io/badge/License-GPL--v3-blue)](LICENSE)

FIW Bosses turns JSON files into custom Minecraft boss fights. Drop a boss file into `config/fiw_bosses/bosses/`, run `/boss reload`, and the server can spawn it immediately.

This is not a single hardcoded boss mod. It is a framework for building your own fights:

- phase changes at HP thresholds
- ability loadouts per phase
- custom minions with their own AI, gear, loot, skins, and abilities
- bosses and custom minions that can look like vanilla mobs while keeping FIW behavior
- pre-fight and pre-death dialogue
- player skins or local skin files
- equipment with item data
- custom loot
- hot reload
- Fabric, Forge, and NeoForge targets from one repo

FIW Bosses is a **both-sides mod**. Install the matching jar on the server and every client that joins it.

## What You Can Build

Make a duelist that dodges hits, taunts players, and chains slash combos. Build a caster that seals players inside a domain and switches to a special arena moveset. Create a raid boss that summons custom guards, changes gear at 50% HP, and drops server-specific rewards.

Every boss is just JSON:

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

Full JSON reference: [BOSS_CONFIG_DOCS.md](BOSS_CONFIG_DOCS.md)

## Supported Targets

| Loader | Minecraft | Module | Java |
|---|---:|---|---:|
| Fabric | 1.21.11 | `fabric-1.21.11` | 21 |
| NeoForge | 1.21.11 | `neoforge-1.21.11` | 21 |
| Fabric | 1.21.8 | `fabric-1.21.8` | 21 |
| NeoForge | 1.21.8 | `neoforge-1.21.8` | 21 |
| Fabric | 1.21.1 | `fabric-1.21.1` | 21 |
| NeoForge | 1.21.1 | `neoforge-1.21.1` | 21 |
| Fabric | 1.20.1 | `fabric-1.20.1` | 17 |
| Forge | 1.20.1 | `forge-1.20.1` | 17 |

Fabric builds require [Fabric API](https://modrinth.com/mod/fabric-api). [Fiw Tools](https://modrinth.com/mod/fiw-tools) is optional. If it is installed, boss equipment and loot can reference Fiw Tools items by `toolId`; if not, those entries are skipped.

## Core Systems

### Phase-Based Fights

Bosses can change behavior as their HP drops. A phase can alter speed, damage, equipment, minion pools, transition messages, sounds, particles, and active abilities.

### Movement Modes

Bosses and custom minions can use configurable movement styles:

- `side` or `normal`: chase targets and strafe when close
- `vanilla`: use Minecraft pathing with no smart movement overlay
- `hit_and_run`: rush in, then retreat before re-engaging
- `guard_point`: fight near the spawn point and return if pulled away
- `phase_walk`: occasionally blinks to a safe nearby position
- `hover`: floats while keeping pressure and spacing
- `sniper`: keeps distance and strafes at range
- `berserk`: fights normally until low HP, then hard-chases

### Ability Engine

Abilities are small, configurable attack modules. Add them to phases, set cooldowns, tune params, and combine them into a moveset.

Current ability IDs:

```text
melee_slash, arc_slash, dodge, slam, aoe_smash, charge, teleport, shield, heal,
ranged_projectile, summon_minions, beam, chain_lightning, orbital, meteor, pull,
flames, freeze, random_message, particle_tornado, swap, shockwave, slash_wave,
sonic_boom, domain, ice_crystal, fire_arrow, crimson_slash, singularity_cannon,
lightning_radial, orb_throw, tracking_orb, moving_tornado, ground_spike,
arrow_rain, potion_field, detect_mark, phantom_dash, guardian_shield,
essence_absorption, judgment_mark, divine_execution, rift_cleave, fear_burst,
mirror_image, sacrifice_minion, last_breath, wither_crown
```

Highlights:

- `domain`: seals players inside a sphere, can run its own attack set, has a boss-level cooldown so it cannot instantly recast after ending
- `phantom_dash`: zigzag dashes that clip walls and snap to safe ground
- `particle_tornado`: configurable particles, spin, fire, damage, size, and funnel shape
- `beam`: configurable outer/core particles and max beam length
- `dodge`: sidesteps when hit and supports taunts
- `rift_cleave`: tears a delayed damaging line through the ground
- `fear_burst`: warden-style soul burst with Darkness, Weakness, Slowness, knockback, and optional damage
- `mirror_image`: particle decoys, invisibility, position swaps, and optional reappear damage
- `sacrifice_minion`: consumes nearby minions to heal the boss and punish players near the sacrifice
- `last_breath`: low-health interruptible channel that releases a huge soul blast if players fail the damage check
- `wither_crown`: rotating wither skulls that orbit the boss before firing one by one

### Custom Minions

Minions can be true vanilla mobs for simple summons, or FIW custom minions with skins, gear, abilities, movement modes, loot, and optional vanilla-mob visuals.

For advanced minions, use `baseEntity: "custom"` and set `renderEntity` when you want the model to look like a vanilla mob:

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

Custom minions support the boss movement modes plus:

- `follow_boss`: escorts the boss
- `static`: stays in place and uses abilities

Vanilla-base minions, like `"baseEntity": "minecraft:zombie"`, keep their native vanilla AI and are only lightly customizable. They are useful for simple mobs, but custom abilities, skins, and smart movement belong to `baseEntity: "custom"`.

Bosses follow the same visual idea: they always use the FIW boss entity internally, but `baseEntity` or `renderEntity` can make them render as a vanilla/modded mob.

### Dialogue and Fight Flow

Bosses can start inactive and immortal until a player right-clicks them. Pre-fight dialogue plays, then combat begins. Pre-death dialogue can hold the boss at 1 HP for final lines before it dies.

### Persistence and Reloading

Bosses are persistent by default. Phase index is saved to NBT, configs can be reloaded with `/boss reload`, and deleted boss configs are removed from the world on reload.

## Installation

1. Install the correct loader for your Minecraft version.
2. Install Fabric API if you use Fabric.
3. Put the matching FIW Bosses jar in `mods/` on the server and every client.
4. Start the game/server once.
5. Add boss files to `config/fiw_bosses/bosses/`.
6. Run `/boss reload`.

Config folders:

```text
config/fiw_bosses/bosses/    active boss definitions
config/fiw_bosses/minions/  custom minion definitions
config/fiw_bosses/skins/    local PNG skins
```

Jar naming:

```text
fiw-bosses-fabric-1.21.11-<version>.jar
fiw-bosses-neoforge-1.21.11-<version>.jar
fiw-bosses-fabric-1.21.8-<version>.jar
fiw-bosses-neoforge-1.21.8-<version>.jar
fiw-bosses-fabric-1.21.1-<version>.jar
fiw-bosses-neoforge-1.21.1-<version>.jar
fiw-bosses-fabric-1.20.1-<version>.jar
fiw-bosses-forge-1.20.1-<version>.jar
```

## Commands

`/boss` requires op level 2. `/boss reload` requires op level 3.

```text
/boss spawn <id>
/boss spawn <id> <x> <y> <z>
/boss list
/boss reload
/boss kill <id>
/boss kill all

/boss minion list
/boss minion spawn <id>
/boss minion kill <id>
/boss minion kill all
```

## Repository Layout

This repo builds every supported loader and Minecraft version from one source tree.

```text
core/             Minecraft-free config, text, skin, and shared support code
common-1.21.11/  shared game logic for Minecraft 1.21.11
common-1.21.8/   shared game logic for Minecraft 1.21.8
common-1.21.1/   shared game logic for Minecraft 1.21.1
common-1.20.1/   shared game logic for Minecraft 1.20.1
fabric-*/        Fabric entrypoints, registration, networking, client hooks
neoforge-*/      NeoForge entrypoints, registration, networking, client hooks
forge-1.20.1/    Forge entrypoint and 1.20.1 Forge integration
```

The JSON schema is intended to stay consistent across targets. A boss written for one supported version should behave the same on the others.

## Building

Use JDK 21 to run Gradle. The 1.20.1 modules compile with Java 17 toolchains.

```bash
git clone https://github.com/Fi3w0/Fiw-Bosses
cd Fiw-Bosses
./gradlew build
```

Build a full release-style matrix:

```bash
./gradlew :core:test \
  :fabric-1.21.11:build :neoforge-1.21.11:build \
  :fabric-1.21.8:build  :neoforge-1.21.8:build \
  :fabric-1.21.1:build  :neoforge-1.21.1:build \
  :fabric-1.20.1:build  :forge-1.20.1:build
```

Output jars are written to each module's `build/libs/` directory.

The first Forge build can take a while because ForgeGradle downloads and prepares Minecraft. Avoid `--configure-on-demand` for single-loader builds in this repo because loader modules depend on `:core` being configured.

## Known Limits

- Many ability visuals still use code-defined particle styles, although some abilities expose particle params.
- Player skin lookup needs internet access when fetching Mojang skins.
- The shared renderer uses a safe humanoid model. Exact slim-arm geometry and every outer-layer detail are not fully matched yet.

## Documentation

- Full JSON wiki: [BOSS_CONFIG_DOCS.md](BOSS_CONFIG_DOCS.md)
- Issue tracker: [github.com/Fi3w0/Fiw-Bosses/issues](https://github.com/Fi3w0/Fiw-Bosses/issues)
- License: [GPL-3.0](LICENSE)

## Credits

FIW Bosses is designed and maintained by **Fi3w0** for SMP-style custom fights, then shared for anyone who wants stronger boss encounters without building a whole combat mod from scratch.
