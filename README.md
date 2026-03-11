# FIW Bosses

> Custom Boss Framework · Fabric 1.20.1 · JSON-Driven · Server-Side Only

A data-driven boss framework for Fabric — inspired by MythicMobs, built from scratch for my SMP. Define fully custom multi-phase bosses entirely through JSON. No coding, no restarts — drop a config, run `/boss reload`, and your boss is live.

---

## Features

- **JSON-driven** — create any boss without touching a single line of code
- **Multi-phase system** — HP thresholds trigger phase transitions with new abilities, speeds, equipment, sounds, and particles
- **16 abilities** — melee, ranged, mobility, AoE, and utility, all configurable per phase
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
3. Drop `fiw-bosses-1.0.0.jar` into your `mods/` folder
4. Start the server — configs generate automatically in `config/fiw_bosses/`

---

## Commands

```
/boss spawn <boss_id>               — spawn at your location
/boss spawn <boss_id> <x> <y> <z>  — spawn at coordinates
/boss list                          — list all loaded boss IDs
/boss reload                        — reload all JSON configs (permission level 3)
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

Full parameter reference: [BOSS_CONFIG_DOCS.md](BOSS_CONFIG_DOCS.md)

---

## Included Bosses

Four ready-to-use bosses are included in `examples/` out of the box.

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
- v1.0.0 — stable but may have occasional edge-case bugs

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