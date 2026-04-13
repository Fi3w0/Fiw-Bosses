# FIW Bosses

> Custom Boss Framework · NeoForge 1.21.1 & Fabric 1.20.1 · JSON-Driven · Server-Side Only

[![Modrinth](https://img.shields.io/modrinth/v/fiw-bosses?label=Modrinth&logo=modrinth&color=00AF5C)](https://modrinth.com/mod/fiw-bosses)
[![Minecraft](https://img.shields.io/badge/Minecraft-1.21.1_|_1.20.1-62B47A)](https://minecraft.net)
[![NeoForge](https://img.shields.io/badge/NeoForge-21.1.x-F16436)](https://neoforged.net)
[![Fabric](https://img.shields.io/badge/Fabric-1.20.1-DBB591)](https://fabricmc.net)
[![License](https://img.shields.io/badge/License-GPL--v3-blue)](LICENSE)

A data-driven boss framework — inspired by MythicMobs, built from scratch for my SMP. Define fully custom multi-phase bosses entirely through JSON. No coding, no restarts — drop a config, run `/boss reload`, and your boss is live.

## Supported versions

| Loader | Minecraft | Status | Branch |
|---|---|---|---|
| **NeoForge** | **1.21.1** | **Active** — primary development target | [`main`](https://github.com/Fi3w0/Fiw-Bosses) |
| Fabric | 1.20.1 | Legacy — may still receive occasional updates, but not guaranteed | [`legacy-fabric-1.20.1`](https://github.com/Fi3w0/Fiw-Bosses/tree/legacy-fabric-1.20.1) |

Both loaders share the **same JSON schema, same 42 abilities, same feature set**. A boss config written for one works on the other. Development focus is on NeoForge 1.21.1 going forward; Fabric 1.20.1 is no longer the active branch but may still get ports of fixes and new abilities when practical.

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
- **Dynamic aggro** — aggro switching, revenge targeting, multiplayer-friendly
- **Strafing AI** — bosses circle and strafe at close range
- **Custom loot tables** — per-item drop chances with full NBT support (bosses and minions)
- **Hot reload** — `/boss reload` reloads all configs without a server restart
- **Phase persistence** — boss phase survives server restarts via NBT

---

## Requirements

### NeoForge 1.21.1 (active)

| Dependency | Version |
|---|---|
| Minecraft | 1.21.1 |
| NeoForge | 21.1.x |
| Java | 21 |
| Client-side required | No |

### Fabric 1.20.1 (legacy)

| Dependency | Version |
|---|---|
| Minecraft | 1.20.1 |
| Fabric Loader | latest |
| Fabric API | 0.92.2+1.20.1 or newer |
| Java | 21 |
| Client-side required | No |

---

## Installation

**NeoForge 1.21.1:**
1. Install [NeoForge](https://neoforged.net/) for Minecraft 1.21.1
2. Drop the NeoForge jar (`fiw-bosses-<version>-neoforge-1.21.1.jar`) into your `mods/` folder
3. Start the server — configs live in `config/fiw_bosses/`

**Fabric 1.20.1 (legacy):**
1. Install [Fabric Loader](https://fabricmc.net/use/installer/) for Minecraft 1.20.1
2. Install [Fabric API](https://modrinth.com/mod/fabric-api)
3. Drop the Fabric jar (`fiw-bosses-<version>-fabric-1.20.1.jar`) into your `mods/` folder
4. Start the server — configs generate automatically in `config/fiw_bosses/`

---

## Commands

```
/boss spawn <id>                — spawn at your location
/boss spawn <id> <x> <y> <z>   — spawn at coordinates
/boss list                     — list all loaded boss IDs
/boss reload                   — reload all boss + minion configs
/boss kill <id>                — kill all living bosses with that ID
/boss kill all                 — kill every boss currently alive
/boss minion list              — list all loaded minion definitions
/boss minion spawn <id>        — spawn a custom minion at your position
/boss minion kill <id>         — kill all living minions with that ID
/boss minion kill all          — kill every minion currently alive
```

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

Seven pre-built bosses live in `examples/` — copy any into `config/fiw_bosses/bosses/` to use them.

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

Found a bug or have a feature request? Open an issue — both NeoForge and Fabric are tracked in one place:

**[Issue Tracker](https://github.com/Fi3w0/Fiw-Bosses/issues)**

---

## Building from Source

**NeoForge 1.21.1 (default branch):**
```bash
git clone https://github.com/Fi3w0/Fiw-Bosses
cd Fiw-Bosses
./gradlew build
```

**Fabric 1.20.1 (legacy branch):**
```bash
git clone -b legacy-fabric-1.20.1 https://github.com/Fi3w0/Fiw-Bosses
cd Fiw-Bosses
./gradlew build
```

Output JAR in `build/libs/`.

---

## Known Issues

- Particle types are hardcoded — full particle customization not yet implemented
- Player skins require an internet connection on server start (Mojang API lookup)

---

## License

This project is licensed under the **GNU General Public License v3.0**.
You are free to use, modify, and distribute this mod under the same license.
See [LICENSE](LICENSE) for full terms.

---

## Credits

- **Fi3w0** — design, mechanics, and direction
- **Claude Opus 4.6 (Anthropic)** — assisted in code implementation

---

*Made by Fi3w0 — built for my SMP, shared with everyone.*
