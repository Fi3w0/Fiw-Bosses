# FIW Bosses

> Custom Boss Framework · Fabric 1.20.1 · JSON-Driven · Server-Side Only

[![Modrinth](https://img.shields.io/modrinth/v/fiw-bosses?label=Modrinth&logo=modrinth&color=00AF5C)](https://modrinth.com/mod/fiw-bosses)
[![Minecraft](https://img.shields.io/badge/Minecraft-1.20.1-62B47A)](https://minecraft.net)
[![Fabric](https://img.shields.io/badge/Mod_Loader-Fabric-DBB591)](https://fabricmc.net)
[![License](https://img.shields.io/badge/License-GPL--v3-blue)](LICENSE)

A data-driven boss framework for Fabric — inspired by MythicMobs, built from scratch for my SMP. Define fully custom multi-phase bosses entirely through JSON. No coding, no restarts — drop a config, run `/boss reload`, and your boss is live.

---

## Features

- **JSON-driven** — create any boss without touching a single line of code
- **Multi-phase system** — HP thresholds trigger phase transitions with new abilities, speeds, equipment, sounds, and particles
- **36 abilities** — melee, ranged, mobility, AoE, utility, crowd-control, and ultimates — all configurable per phase
- **Idle system** — configurable despawn or gradual heal when no players are nearby
- **Custom skins** — any player skin or local PNG file
- **Custom equipment** — full item + NBT support per slot, changeable per phase
- **Minion spawning** — spawn any mob with caps and radius control
- **Dynamic aggro** — aggro switching, revenge targeting, multiplayer-friendly
- **Strafing AI** — bosses circle and strafe at close range
- **Custom loot tables** — per-item drop chances with full NBT support
- **Hot reload** — `/boss reload` reloads all configs without a server restart
- **Phase persistence** — boss phase survives server restarts via NBT

---

## Requirements

| Dependency | Version |
|---|---|
| Minecraft | 1.20.1 |
| Fabric Loader | latest |
| Fabric API | 0.92.2+1.20.1 or newer |
| Java | 21 |
| Client-side required | No |

---

## Installation

1. Install [Fabric Loader](https://fabricmc.net/use/installer/) for Minecraft 1.20.1
2. Install [Fabric API](https://modrinth.com/mod/fabric-api)
3. Drop `fiw-bosses-1.0.4.jar` into your `mods/` folder
4. Start the server — configs generate automatically in `config/fiw_bosses/`

---

## Commands

```
/boss spawn <boss_id>               — spawn at your location
/boss spawn <boss_id> <x> <y> <z>  — spawn at coordinates
/boss list                          — list all loaded boss IDs
/boss reload                        — reload all JSON configs (permission level 3)
/boss kill <boss_id>                — kill all living bosses with that ID
/boss kill all                      — kill every boss currently alive
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
| `domain` | **Ultimate** — multi-layered dark sphere anchored at cast position; boss and players sealed inside, custom attack pattern, custom speed, domain_break sound on collapse |
| `ice_crystal` | Snowflake burst of ice crystals — outer ring Slowness IV, center near-freeze, crystals persist then shatter |
| `fire_arrow` | Charged fire projectile fires at high speed and explodes on contact — no terrain damage |
| `crimson_slash` | 3 consecutive energy claws along the ground, each larger than the last, converging in a dark-flame explosion |
| `singularity_cannon` | Charging plasma ring → high-speed beam that drags players; nearby players slowed 30% during charge |
| `lightning_radial` | Boss leaps into the air, channels energy, then radiates 16–24 electric blades 360° at ground level |
| `orb_throw` | Green mystic orb orbits the boss with spinning rings, then launches forward with a knockback explosion |
| `tracking_orb` | Passive purple orb follows the boss and fires homing projectiles — runs alongside other abilities |
| `moving_tornado` | Tornado advances toward the target, absorbing and lifting players caught inside |
| `ground_spike` | Boss marks an area, then FallingBlock spikes erupt from the ground launching players upward |
| `arrow_rain` | Marks a circular area with a warning ring, then actual arrows fall from above across the zone |
| `potion_field` | Throws a potion in an arc; on landing creates a persistent effect field that applies a configurable status effect |

Full parameter reference: [BOSS_CONFIG_DOCS.md](BOSS_CONFIG_DOCS.md)

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

A test boss ships in `run/config/fiw_bosses/bosses/` for quick in-game testing:

| Boss | Purpose |
|---|---|
| `ability_tester` | Phase 1: ice_crystal + fire_arrow · Phase 2: crimson_slash + singularity_cannon · Phase 3: all 11 new abilities |

---

## Building from Source

```bash
git clone https://github.com/Fi3w0/fiw-bosses
cd fiw-bosses
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
