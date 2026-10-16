<div align="center">

# ⚔️ FIW Bosses

### Build the boss fight you've always wanted — from a text file.

**Phases. Minions. Loot. Dialogue. Custom skins. 50+ abilities.**
No Java. No restarts. No combat-modding degree required.

[![Modrinth](https://img.shields.io/modrinth/v/fiw-bosses?label=Modrinth&logo=modrinth&color=00AF5C)](https://modrinth.com/mod/fiw-bosses)
[![Minecraft](https://img.shields.io/badge/Minecraft-1.21.11_·_1.21.8_·_1.21.1_·_1.20.1-62B47A)](https://minecraft.net)
[![Fabric](https://img.shields.io/badge/Fabric-✓-DBB591)](https://fabricmc.net)
[![NeoForge](https://img.shields.io/badge/NeoForge-✓-F16436)](https://neoforged.net)
[![Forge](https://img.shields.io/badge/Forge_1.20.1-✓-1E2D4A)](https://minecraftforge.net)

</div>

Local `file` skins accept names with or without `.png`. Domain `attacks[]` accept both `cooldown` and `cooldownTicks` (ticks; `cooldown` wins if both are set). See the [configuration guide](BOSS_CONFIG_DOCS.md).

Back up `config/fiw_bosses/data/`: `spawns.json`, `lockouts.json`, `records.json` and `schedules.json`. Example defaults: `examples/settings.json`; scheduled boss: `examples/schedules/clockwork_guardian.json`.


---

## The pitch

Vanilla has three bosses. You've beaten all of them.

FIW Bosses hands you the toolkit Mojang didn't: drop a JSON file into `config/fiw_bosses/bosses/`, run `/boss reload`, and a fully custom encounter is live on your world — changing tactics as its health drops, summoning minions, taunting your players, and dropping exactly the loot you decide.

It isn't one hardcoded boss bolted onto the game. It's a **framework**: HP-threshold phases, per-phase ability loadouts, custom minions with their own AI, pre-fight and pre-death dialogue, custom skins, hot reload — one config schema that behaves the same on every loader and version it supports.

> 🧩 **Both-sides mod.** Install the matching jar on the **server and every client** that joins it.
---

![Short showcase](https://cdn.modrinth.com/data/O9BUcsSY/images/7a6808da1a73d6c234247a25f17242dba341652f.gif)

---

## Sixty seconds to your first boss

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
        { "type": "beam", "cooldownTicks": 180, "params": { "damage": 4 } }
      ]
    }
  ]
}
```

```
/boss reload
/boss spawn storm_caller
```

That's the whole loop. Tweak the file, reload, fight again — no restart, ever.

---

## What you can build

🔥 **Phase-based fights** — every boss can rewrite its own rules as it bleeds. Each phase changes speed, damage, equipment, the minion pool, transition messages, sounds, particles, and its entire ability set.

🧠 **Eight movement styles** — `hit_and_run`, `guard_point`, `phase_walk`, `hover`, `sniper`, `berserk`, plus classic strafing or plain `vanilla` pathing. Your sniper kites; your berserker hunts.

👥 **Custom minions** — true FIW minions with skins, gear, abilities, AI modes and loot, or plain vanilla mobs for quick summons. Minions can escort the boss, hold a position, or fight on their own.

🎭 **Disguises** — keep full FIW logic but render a boss or minion as *any* vanilla or modded mob with `renderEntity`, or use a real player skin. Your "zombie" can cast meteors.

💬 **Fight flow & dialogue** — start a boss dormant and invulnerable until a player right-clicks it; play pre-fight lines, then begin. Hold it at 1 HP for last words before it falls. Every chat message is optional — silent bosses are one omitted param away.

🛡️ **Damage protection** — per-boss, per-phase, and per-minion damage multipliers keyed by weapon item, damage type, or category. Tame the overpowered mace/spear (`"minecraft:mace": 0.2`), resist potions (`"magic": 0.3`), or grant true immunities (`"fall": 0`) — made for servers with custom weapons.

⚔️ **Factions** — allied bosses and minions never turn on each other and their AoE can't hurt allies, with per-entity switches (`damageFactionAllies`, `targetFactionAllies`, `damageOwnGroup`) when you *want* infighting.

🌊 **Water & lava handling** — no more river cheese: bosses can chase players into water (`canSwim`), never drown, sink and fight underwater (`floats: false`), swim faster, ignore currents, and shrug off lava (`fireImmune`).

🏆 **Raid rewards** — MMO-style per-player loot: everyone who really fought gets their own roll straight into their inventory. Mark a drop `unique` and exactly one raider gets it — never a second copy. Top damage dealers can be luckier (`damageLuck`) or the only ones eligible (`topRanks`), and kill/participant/MVP commands hand out anything else. Leechers and `/boss kill` earn nothing.

📜 **Fight summary** — an optional chat recap after the kill: killing blow, damage ranking with share %, who tanked the most, who fell, unique drops, fight time. Only real participants are listed. Global defaults with per-boss overrides, toggled live with `/boss summary`.

❤️ **Any health value** — go past vanilla's 1024 HP cap: a 60,000 HP raid boss really has 60,000 HP — shown correctly in Jade and similar mods, and optionally in the boss bar (`60,000/60,000 ❤`).

🗝️ **Summon items & altars** — players start fights themselves: right-click with a summon item, optionally on an altar block. Cap how many can be alive, add a server-wide cooldown, and players are told exactly why a summon was refused. Admin spawns are never limited.

⚔️ **Difficulty variants** — a `hard` key spawns a tougher boss: more health, more damage (all, melee-only or ability-only), more loot and its own name. It keeps its variant through restarts.

🧱 **Cheese-proof fights** — bosses never break blocks, yet boxing, pillaring, boats, cobwebs and minion traps don't work: arena leash resets (no loot for the abandoned attempt), stuck detection that teleports, strikes or enrages with escalation, vehicles destroyed on contact, slowing blocks ignored, and minions that teleport to players they can't reach.

😡 **Rages & enrage timer** — phases triggered by behavior instead of HP: trap the boss and it rages with extra health, a red bar and new abilities; take too long and the enrage timer kicks in.

🛡️ **Shield phases** — at 40% HP the boss raises a shield and summons pylons that fight back; it's immune until your players tear them all down.

⏰ **Scheduled world spawns** — "The Ember Warden awakens in 10 minutes at 120, 70, -40!" Bosses appear every N minutes or at set times of day, with countdowns, never twice while one lives.

⚡ **Swarm-friendly path finding** — optional shared mode: a squad chasing the same player shares one path search. Vanilla stays the default.

📊 **Lockouts, records & Discord** — loot lockouts stop farming, `/boss stats` shows fastest kills and top players, and every kill can post a Discord embed to your community channel.

🎚️ **Admin phase control** — `/boss phase` moves a live boss to the next, previous or any phase without touching its HP. Players struggling? Ease it. Fight dragging? Push it.

🎲 **Loot ranges** — roll random drop amounts with `minCount`/`maxCount` (10–120 diamonds at 50% chance); big rolls split into stacks automatically.

♻️ **Hot reload & persistence** — `/boss reload` re-reads everything live, deleted configs are removed from the world, and a boss's phase survives server restarts.
---

## 50+ abilities, mixed and matched

Abilities are small, tunable attack modules. Stack them on a phase, set cooldowns, tune the params, and a moveset is born.

| Ability | What it does |
|---|---|
| `domain` | Seals players in a sphere with its own attack set |
| `phantom_dash` | Wall-clipping zigzag dashes that snap to safe ground |
| `rift_cleave` | Tears a delayed damaging line through the floor |
| `fear_burst` | Warden-style soul burst: Darkness, Weakness, knockback |
| `mirror_image` | Particle decoys, invisibility, position swaps |
| `wither_crown` | Orbiting wither skulls that fire one by one |
| `last_breath` | Low-HP channel — fail to interrupt it and eat a soul blast |
| `rewind` | Records position + health and snaps back when low |
| `second_wind` | One-shot auto-revive that negates a fatal blow |
| `adaptation` | Grows resistant to whatever's been hurting it most |
| `blink_strike` | Telegraphs a target, blinks in, and punishes nearby players |
| `curse_bomb` | Delayed player bombs that force the group to spread out |
| `soul_tether` | Chains players to the boss and punishes broken tethers |
| `wind_charge` | Mace-style jump-slam, no self fall damage, damage fully config-driven regardless of what's equipped |

…and dozens more — slams, beams, meteors, orbital strikes, tornadoes, sonic booms, judgment marks, divine executions, summons, heals, shields, and ultimates. The full list and every parameter live in the [documentation](https://github.com/Fi3w0/Fiw-Bosses/blob/main/BOSS_CONFIG_DOCS.md).

---

## Supported versions

| Loader | Minecraft | Java |
|---|---|---|
| Fabric | 1.21.11 | 21 |
| NeoForge | 1.21.11 | 21 |
| Fabric | 1.21.8 | 21 |
| NeoForge | 1.21.8 | 21 |
| Fabric | 1.21.1 | 21 |
| NeoForge | 1.21.1 | 21 |
| Fabric | 1.20.1 | 17 |
| Forge | 1.20.1 | 17 |

Fabric builds need [Fabric API](https://modrinth.com/mod/fabric-api). One boss file behaves the same on all eight targets.

**Optional:** [Fiw Custom Items](https://modrinth.com/mod/fiw-custom-items) — when present, equipment and loot can reference its items by `toolId`; when absent, those entries are skipped and nothing breaks.

---

## Commands

`/boss` needs op level 2; `/boss reload` needs op level 3.

```
/boss spawn <id> [x y z] [count]   /boss list        /boss kill <id> | all
/boss minion spawn <id> [count]    /boss minion list /boss minion kill <id> | all
/boss phase <targets> next | prev | set <n>
/boss summary [on | off | top <n> | show <section> <true|false>]
/boss spawn <id> variant <name> [x y z]   /boss limits <id> [reset]
/boss rage <targets> start <name> | end   /boss stats [id]
/boss schedule list | trigger <id>
/boss reload
```

---

<div align="center">

**[📖 Documentation](https://github.com/Fi3w0/Fiw-Bosses/blob/main/BOSS_CONFIG_DOCS.md)** · **[🐛 Report a bug](https://github.com/Fi3w0/Fiw-Bosses/issues)** · **[GPL-3.0](https://github.com/Fi3w0/Fiw-Bosses/blob/main/LICENSE)**

Made by **Fi3w0** for SMP-style custom fights — shared for anyone who wants stronger bosses without building a combat mod from scratch.

</div>
