# FIW Bosses

> Custom Boss Framework · Fabric & NeoForge 1.21.11 / 1.21.8 / 1.21.1 · Fabric & Forge 1.20.1 · JSON-Driven · Client + Server

[![Modrinth](https://img.shields.io/modrinth/v/fiw-bosses?label=Modrinth&logo=modrinth&color=00AF5C)](https://modrinth.com/mod/fiw-bosses)
[![Minecraft](https://img.shields.io/badge/Minecraft-1.21.11_|_1.21.8_|_1.21.1_|_1.20.1-62B47A)](https://minecraft.net)
[![NeoForge](https://img.shields.io/badge/NeoForge-21.11_|_21.8_|_21.1-F16436)](https://neoforged.net)
[![Fabric](https://img.shields.io/badge/Fabric-1.21.11_|_1.21.8_|_1.21.1_|_1.20.1-DBB591)](https://fabricmc.net)
[![Forge](https://img.shields.io/badge/Forge-1.20.1_47.x-1E2D4A)](https://minecraftforge.net)
[![License](https://img.shields.io/badge/License-GPL--v3-blue)](LICENSE)

A data-driven boss framework — inspired by MythicMobs, built from scratch for my SMP. Define fully custom multi-phase bosses entirely through JSON. No coding, no restarts — drop a config, run `/boss reload`, and your boss is live.

> **Both-sides mod:** required on the client *and* the server. A client without FIW Bosses installed cannot connect to a server running it.

## Supported versions

This is a **mono-repo**: every loader and Minecraft version below is built from this single source tree. A Minecraft-free `core` engine plus a per-version `common` source set are shared across all loader modules, so the **same JSON schema, the same 42 abilities, the same commands** behave identically everywhere. A boss config written for one target works on every other.

| Loader | Minecraft | Gradle module | Java |
|---|---|---|---|
| Fabric | 1.21.11 | `fabric-1.21.11` | 21 |
| NeoForge | 1.21.11 | `neoforge-1.21.11` | 21 |
| Fabric | 1.21.8 | `fabric-1.21.8` | 21 |
| NeoForge | 1.21.8 | `neoforge-1.21.8` | 21 |
| Fabric | 1.21.1 | `fabric-1.21.1` | 21 |
| NeoForge | 1.21.1 | `neoforge-1.21.1` | 21 |
| Fabric | 1.20.1 | `fabric-1.20.1` | 17 |
| Forge | 1.20.1 | `forge-1.20.1` | 17 |

---

## Features

- **JSON-driven** — create any boss without touching a single line of code
- **Multi-phase system** — HP thresholds trigger phase transitions with new abilities, speeds, equipment, sounds, and particles
- **42 abilities** — melee, ranged, mobility, AoE, utility, crowd-control, and ultimates — all configurable per phase
- **Custom minion system** — define custom minions via JSON with their own stats, skins, equipment, abilities, loot, and AI modes
- **Pre-fight activation** — boss starts passive/immortal; player right-clicks to trigger dialogue and start the fight
- **Pre-death dialogue** — boss held at 1 HP on lethal hit, speaks final words, then dies
- **Idle system** — configurable despawn or gradual heal when no players are nearby
- **Custom skins** — any player skin or local PNG file (bosses and minions)
- **Custom equipment** — full item + NBT support per slot, changeable per phase
- **Optional Fiw Tools integration** — reference Fiw Tools items by `toolId` in equipment and loot (no hard dependency)
- **Dynamic aggro** — aggro switching, revenge targeting, multiplayer-friendly
- **Strafing AI** — bosses circle and strafe at close range
- **Custom loot tables** — per-item drop chances with full NBT support (bosses and minions)
- **Hot reload** — `/boss reload` reloads all configs without a server restart
- **Phase persistence** — boss phase survives server restarts via NBT

---

## Requirements

| Target | Minecraft | Loader | Fabric API | Java | Required on |
|---|---|---|---|---|---|
| Fabric 1.21.11 | 1.21.11 | Fabric Loader 0.16+ | 0.141.4+1.21.11 | 21 | Client + Server |
| NeoForge 1.21.11 | 1.21.11 | NeoForge 21.11.x | — | 21 | Client + Server |
| Fabric 1.21.8 | 1.21.8 | Fabric Loader 0.16+ | 0.130.0+1.21.8 | 21 | Client + Server |
| NeoForge 1.21.8 | 1.21.8 | NeoForge 21.8.x | — | 21 | Client + Server |
| Fabric 1.21.1 | 1.21.1 | Fabric Loader 0.16+ | 0.115.0+1.21.1 | 21 | Client + Server |
| NeoForge 1.21.1 | 1.21.1 | NeoForge 21.1.x | — | 21 | Client + Server |
| Fabric 1.20.1 | 1.20.1 | Fabric Loader 0.15+ | 0.92.2+1.20.1 | 17 | Client + Server |
| Forge 1.20.1 | 1.20.1 | Forge 47.x | — | 17 | Client + Server |

Fabric builds require [Fabric API](https://modrinth.com/mod/fabric-api). [Fiw Tools](https://modrinth.com/mod/fiw-tools) is an optional soft dependency — if absent, `toolId` config entries are simply skipped.

---

## Installation

1. Install the loader for your Minecraft version (NeoForge / Forge / Fabric + Fabric API).
2. Install the matching jar in the `mods/` folder on **both the client and the server** (a client without it cannot join):

   ```
   fiw-bosses-fabric-1.21.11-<version>.jar
   fiw-bosses-neoforge-1.21.11-<version>.jar
   fiw-bosses-fabric-1.21.8-<version>.jar
   fiw-bosses-neoforge-1.21.8-<version>.jar
   fiw-bosses-fabric-1.21.1-<version>.jar
   fiw-bosses-neoforge-1.21.1-<version>.jar
   fiw-bosses-fabric-1.20.1-<version>.jar
   fiw-bosses-forge-1.20.1-<version>.jar
   ```

3. Start the server — configs live in `config/fiw_bosses/` (`bosses/`, `minions/`, `skins/`).

---

## Commands

```
/boss spawn <id>                — spawn at your location
/boss spawn <id> <x> <y> <z>   — spawn at coordinates
/boss list                     — list all loaded boss IDs
/boss reload                   — reload all boss + minion configs (op level 3)
/boss kill <id>                — kill all living bosses with that ID
/boss kill all                 — kill every boss currently alive
/boss minion list              — list all loaded minion definitions
/boss minion spawn <id>        — spawn a custom minion at your position
/boss minion kill <id>         — kill all living minions with that ID
/boss minion kill all          — kill every minion currently alive
```

`/boss` requires op level 2 (gamemasters); `/boss reload` requires op level 3 (admins).

---

## Abilities

| Ability | Description |
|---|---|
| `melee_slash` | Quick arc swing at close range |
| `arc_slash` | Animated blade sweep — configurable roll, height, reach |
| `dodge` | Sidestep on taking damage (configurable chance) |
| `slam` | Guaranteed ground pound every cooldown — no RNG, pairs well with `dodge` |
| `aoe_smash` | Windup → AoE ground pound with knockback |
| `charge` | Line dash through the target, hits everything in the path |
| `teleport` | Enderman-style blink toward the target |
| `shield` | Temporary damage reduction bubble |
| `heal` | Self-heal channel below an HP threshold |
| `ranged_projectile` | Fireball or arrow volley |
| `summon_minions` | Spawn mobs from the phase's minion list |
| `beam` | Freeze + dense particle laser |
| `chain_lightning` | Lightning that chains between nearby players |
| `orbital` | Orbiting particle orbs that damage on contact |
| `meteor` | Fireballs or wither skulls falling from above |
| `pull` | Vortex that pulls all nearby players in |
| `flames` | Sustained fire aura around the boss — boss keeps moving freely |
| `freeze` | Holds nearby players in a configurable freeze for a set duration |
| `random_message` | Sends a random taunt from a list to nearby players |
| `particle_tornado` | Rising funnel tornado — narrow at base, wide at top, optional damage |
| `swap` | Instantly swaps the boss and target positions to disorient players |
| `shockwave` | Ground slam sends expanding rings — players must jump over each one |
| `slash_wave` | Fast forward-traveling blade of energy that follows a straight path |
| `sonic_boom` | Warden-style charge-and-release — ignores armor, optional Darkness |
| `domain` | **Ultimate** — multi-layered dark sphere; boss and players sealed inside |
| `ice_crystal` | Snowflake burst of ice crystals — outer ring Slowness IV, center near-freeze |
| `fire_arrow` | Charged fire projectile fires at high speed and explodes on contact |
| `crimson_slash` | 3 consecutive energy claws converging in a dark-flame explosion |
| `singularity_cannon` | Charging plasma ring → high-speed beam that drags players |
| `lightning_radial` | Boss leaps up, radiates 16–24 electric blades 360° at ground level |
| `orb_throw` | Green mystic orb launches forward with a knockback explosion |
| `tracking_orb` | Passive purple orb fires homing projectiles — runs alongside other abilities |
| `moving_tornado` | Tornado advances toward the target, absorbing and lifting players |
| `ground_spike` | FallingBlock spikes erupt from the ground launching players upward |
| `arrow_rain` | Marks a circular area, then arrows fall from above across the zone |
| `potion_field` | Creates a persistent effect field that applies a configurable status effect |
| `detect_mark` | Marks the highest-HP player — Glowing + bonus damage |
| `phantom_dash` | 3 rapid zigzag lightning dashes hitting players at each endpoint |
| `guardian_shield` | Passive cyan shield — counterattacks with damage+knockback on hit |
| `essence_absorption` | Vampiric soul projectile — steals HP and applies Weakness |
| `judgment_mark` | **Ultimate** — marks all nearby players, detonates after delay |
| `divine_execution` | Seizes one player, lifts them, holds, then hurls them away |

---

## Documentation

Full configuration reference — all fields, ability parameters, minion system, and examples:

**[BOSS_CONFIG_DOCS.md](BOSS_CONFIG_DOCS.md)**

---

## Included Bosses

Pre-built bosses live in the `examples/` configs — copy any into `config/fiw_bosses/bosses/` to use them. `example_boss` is a minimal starter template.

| Boss | Style | Highlights |
|---|---|---|
| `blade_dancer` | Skirmisher | arc slash, dodge, slam, charge, fire arrow, crimson slash |
| `storm_caller` | Ranged mage | chain lightning, orbital, beam, ice crystal, singularity cannon, lightning radial |
| `iron_warden` | Tank bruiser | shield, aoe smash, charge, slam + minion guards |
| `void_witch` | Caster | flames, freeze, particle tornado, swap, ice crystal |
| `domain_sovereign` | Ultimate | shockwave, slash wave, sonic boom, domain expansion, lightning radial |
| `arcane_sovereign` | Elemental mage | ice crystal, fire arrow, crimson slash, singularity cannon, lightning radial |
| `fi3w0_spirit` | Custom | personal boss with all-phase mechanics |

---

## Issues & Feedback

Found a bug or have a feature request? Open an issue — every loader and version is tracked in one place:

**[Issue Tracker](https://github.com/Fi3w0/Fiw-Bosses/issues)**

---

## Building from Source

One clone builds every target. The Gradle daemon must run on **JDK 21** (the 1.20.1 modules compile against Java 17 via Gradle toolchains, auto-provisioned).

```bash
git clone https://github.com/Fi3w0/Fiw-Bosses
cd Fiw-Bosses

# Build everything + run core tests:
./gradlew build

# Or build specific targets (recommended combined command):
./gradlew :core:test \
  :fabric-1.21.11:build :neoforge-1.21.11:build \
  :fabric-1.21.1:build  :neoforge-1.21.1:build \
  :fabric-1.21.8:build  :neoforge-1.21.8:build \
  :fabric-1.20.1:build  :forge-1.20.1:build
```

Each loader's jar lands in `<module>/build/libs/`. Pushes and pull requests are built automatically by the `build` GitHub Actions workflow; tagging `vX.Y.Z` builds all targets and publishes a GitHub Release.

> Note: the first Forge build downloads and decompiles Minecraft, so it takes several minutes. Don't pass `--configure-on-demand` to a single-loader build — loader modules reference `:core` before it is configured.

---

## Known Issues

- Particle types are hardcoded — full particle customization not yet implemented
- Player skins require an internet connection on server start (Mojang API lookup)
- The shared renderer is a "safe humanoid" model; slim-arm geometry and exact player outer layers are not yet fully matched

---

## License

This project is licensed under the **GNU General Public License v3.0**.
You are free to use, modify, and distribute this mod under the same license.
See [LICENSE](LICENSE) for full terms.

---

## Credits

- **Fi3w0** — design, mechanics, and direction
- **Claude (Anthropic)** — assisted in code implementation

---

*Made by Fi3w0 — built for my SMP, shared with everyone.*
