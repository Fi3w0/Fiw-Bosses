# Boss Configuration Guide

Bosses are defined as `.json` files — no coding or server restart needed.

| Path | Purpose |
|------|---------|
| `config/fiw_bosses/bosses/` | Active boss definitions |
| `config/fiw_bosses/skins/` | Custom skin `.png` files |
| `examples/` | Pre-built bosses ready to copy |

> **Hot reload:** `/boss reload` — picks up all changes instantly.

**Commands:**

| Command | Permission | Description |
|---------|-----------|-------------|
| `/boss spawn <id>` | level 2 | Spawn boss at your position |
| `/boss spawn <id> <x> <y> <z>` | level 2 | Spawn at coordinates |
| `/boss kill <id>` | level 2 | Kill all living bosses with that ID |
| `/boss kill all` | level 2 | Kill every living boss in all worlds |
| `/boss list` | level 2 | List all loaded boss IDs |
| `/boss reload` | level 3 | Reload all JSON configs without restart |

---

## Table of Contents

- [Root Fields](#root-fields)
- [Color Codes](#color-codes)
- [Boss Bar](#boss-bar)
- [Skin](#skin)
- [Equipment](#equipment)
- [Phases](#phases)
- [Abilities](#abilities)
  - [melee_slash](#melee_slash)
  - [arc_slash](#arc_slash)
  - [dodge](#dodge)
  - [slam](#slam)
  - [aoe_smash](#aoe_smash)
  - [charge](#charge)
  - [teleport](#teleport)
  - [shield](#shield)
  - [heal](#heal)
  - [ranged_projectile](#ranged_projectile)
  - [summon_minions](#summon_minions)
  - [beam](#beam)
  - [chain_lightning](#chain_lightning)
  - [orbital](#orbital)
  - [meteor](#meteor)
  - [pull](#pull)
  - [flames](#flames)
  - [freeze](#freeze)
  - [random_message](#random_message)
  - [particle_tornado](#particle_tornado)
  - [swap](#swap)
- [Loot](#loot)
- [Behavior Notes](#behavior-notes)

---

## Root Fields

```json
{
  "id": "my_boss",
  "displayName": "&6&lMy Boss",
  "health": 300.0,
  "armor": 8.0,
  "speed": 0.26,
  "knockbackResistance": 0.6,
  "attackDamage": 10.0,
  "bossBar": { "color": "RED", "overlay": "NOTCHED_10" },
  "skin": { "type": "player", "value": "Notch" },
  "equipment": { ... },
  "phases": [ ... ],
  "loot": [ ... ]
}
```

| Field | Type | Default | Description |
|-------|------|---------|-------------|
| `id` | string | **required** | Unique ID — used in `/boss spawn <id>` |
| `displayName` | string | **required** | Shown above head and in the boss bar. Supports `&` color codes |
| `health` | float | 200.0 | Max HP |
| `armor` | float | 0.0 | Armor points (iron chestplate ≈ 8) |
| `speed` | float | 0.3 | Move speed (zombie = 0.23, player walk ≈ 0.13) |
| `knockbackResistance` | float | 0.5 | `0.0` = full knockback · `1.0` = immune |
| `attackDamage` | float | 10.0 | Base melee damage |
| `bossBar` | object | red/progress | Bar color and style |
| `skin` | object | Steve | Player skin source |
| `equipment` | object | null | Items and armor |
| `phases` | array | [] | Phase definitions |
| `loot` | array | [] | Death drops |

---

## Color Codes

Use `&` prefix in `displayName`, `transitionMessage`, and `taunt` fields.

```
&0 Black    &8 Dark Gray     &l Bold
&1 Dark Blue  &9 Blue        &o Italic
&2 Dark Green &a Green       &n Underline
&3 Dark Aqua  &b Aqua        &m Strikethrough
&4 Dark Red   &c Red         &k Obfuscated
&5 Dark Purple &d Light Purple  &r Reset
&6 Gold       &e Yellow
&7 Gray       &f White
```

**Example:** `"&6&lSkeleton King"` → gold + bold text

---

## Boss Bar

```json
"bossBar": { "color": "PURPLE", "overlay": "NOTCHED_10" }
```

**color:** `PINK` `BLUE` `RED` `GREEN` `YELLOW` `PURPLE` `WHITE`

**overlay:** `PROGRESS` `NOTCHED_6` `NOTCHED_10` `NOTCHED_12` `NOTCHED_20`

---

## Skin

```json
"skin": { "type": "player", "value": "Notch" }
```

| `type` | `value` | Notes |
|--------|---------|-------|
| `player` | Username or UUID | Fetches skin from Mojang API on server start — requires internet |
| `file` | Filename without extension | Loads `config/fiw_bosses/skins/<name>.png` from your server config folder |

**Using a custom skin file:**

1. Export your skin as a **64×64 PNG** (standard Minecraft skin format)
2. Place it in `config/fiw_bosses/skins/` — e.g. `my_boss_skin.png`
3. Reference it in your boss JSON:

```json
"skin": { "type": "file", "value": "my_boss_skin" }
```

> The file skin is loaded at server start and cached. Run `/boss reload` to pick up a new file without restarting.

---

## Equipment

All slots are optional. Any slot can be omitted or set to `null`.

```json
"equipment": {
  "mainHand": { "item": "minecraft:diamond_sword", "nbt": "{Enchantments:[{id:\"minecraft:sharpness\",lvl:5}]}" },
  "offHand":  { "item": "minecraft:shield" },
  "head":     { "item": "minecraft:diamond_helmet" },
  "chest":    { "item": "minecraft:diamond_chestplate" },
  "legs":     { "item": "minecraft:diamond_leggings" },
  "feet":     { "item": "minecraft:diamond_boots" }
}
```

Equipment can also be defined **per phase** to swap gear on transition.

---

## Phases

Phases change the boss's behavior at HP thresholds. Define them from **highest to lowest** HP threshold.

```json
"phases": [
  {
    "hpThresholdPercent": 1.0,
    "speedMultiplier": 1.0,
    "damageMultiplier": 1.0,
    "abilities": [ ... ],
    "minions": [],
    "equipment": null,
    "transitionMessage": null,
    "transitionSound": null,
    "transitionParticle": null
  },
  {
    "hpThresholdPercent": 0.50,
    "speedMultiplier": 1.3,
    "damageMultiplier": 1.5,
    "abilities": [ ... ],
    "minions": [ ... ],
    "transitionMessage": "&c&lThe boss enters a rage!",
    "transitionSound": "minecraft:entity.wither.spawn",
    "transitionParticle": "minecraft:explosion"
  }
]
```

| Field | Type | Default | Description |
|-------|------|---------|-------------|
| `hpThresholdPercent` | float | 1.0 | Phase starts when HP drops to this fraction (`0.5` = 50% HP) |
| `speedMultiplier` | float | 1.0 | Applied on top of base speed |
| `damageMultiplier` | float | 1.0 | Applied on top of base attack damage |
| `abilities` | array | [] | Active abilities in this phase |
| `minions` | array | [] | Minion types available for `summon_minions` |
| `equipment` | object | null | Override gear for this phase |
| `transitionMessage` | string | null | Chat message sent to nearby players |
| `transitionSound` | string | null | Sound ID played on transition |
| `transitionParticle` | string | null | Particle effect burst on transition |

> The boss's current phase is stored in NBT — it survives server restarts.

---

## Abilities

Each ability entry follows this structure:

```json
{
  "type": "<ability_type>",
  "cooldownTicks": 60,
  "params": { ... }
}
```

- **`cooldownTicks`** — ticks between uses (20 ticks = 1 second)
- **`params`** — all parameters are optional; defaults are listed below
- **`taunt`** — available on every ability. Boss sends a chat message when it activates

Abilities take **priority over basic melee** — they never get interrupted mid-cast.

---

### `melee_slash`

Quick sweeping arc attack. Short windup with enchant particles, then swings.

| Param | Default | Description |
|-------|---------|-------------|
| `range` | 4.0 | Reach in blocks |
| `arc` | 90.0 | Sweep angle in degrees |
| `damage` | 10.0 | Damage per hit |

```json
{
  "type": "melee_slash",
  "cooldownTicks": 40,
  "params": { "range": 4.5, "arc": 120.0, "damage": 12.0, "taunt": "&7Feel my blade." }
}
```

---

### `arc_slash`

Cinematic animated blade sweep. The boss freezes, then carves a particle arc through space over several ticks. Each point along the arc deals damage to players it passes through.

| Param | Default | Description |
|-------|---------|-------------|
| `arc` | 180.0 | Total sweep angle in degrees |
| `radius` | 4.5 | Reach in blocks |
| `damage` | 12.0 | Damage per entity hit |
| `duration` | 8 | Ticks to complete the sweep |
| `points` | 28 | Arc resolution — more = smoother |
| `yOffset` | 1.1 | Height from ground at center |
| `height` | 1.0 | Vertical bulge at midpoint |
| `roll` | 0.0 | Tilt the slash plane — positive = upstroke (right→left), negative = downstroke |
| `hitRadius` | 1.2 | Hit sphere at each arc point |

```json
{
  "type": "arc_slash",
  "cooldownTicks": 50,
  "params": {
    "arc": 170, "radius": 4.5, "damage": 10.0,
    "duration": 9, "roll": 12,
    "taunt": "&7...one strike."
  }
}
```

> **Tip — combos:** Stack multiple `arc_slash` entries in one phase with different `roll` values for a cross-slash combo. Phase 2 of `blade_dancer.json` demonstrates this.

---

### `dodge`

When the boss takes damage it has a chance to sidestep. Leaves smoke afterimage particles.

| Param | Default | Description |
|-------|---------|-------------|
| `chance` | 0.3 | Dodge probability per hit (0.0–1.0) |
| `distance` | 3.0 | Dodge distance in blocks |

```json
{
  "type": "dodge",
  "cooldownTicks": 25,
  "params": { "chance": 0.65, "distance": 3.5, "taunt": "&5Missed." }
}
```

---

### `slam`

**Fires every cooldown — no random roll.** The boss freezes, a dark energy vortex spirals inward, then if the target is far it shadow-steps beside them before slamming the ground with a shockwave. Best used as an ultimate or paired with `dodge` for a guaranteed counter-attack.

| Param | Default | Description |
|-------|---------|-------------|
| `radius` | 5.0 | AoE hit radius |
| `damage` | 20.0 | Max damage (falls off ~40% at edge) |
| `knockback` | 2.5 | Radial knockback + upward launch |
| `windupTicks` | 16 | Charge ticks before slam lands |
| `teleportRange` | 6.0 | Shadow-step if target is farther than this |

```json
{
  "type": "slam",
  "cooldownTicks": 80,
  "params": {
    "radius": 5.0, "damage": 18.0, "knockback": 2.8,
    "windupTicks": 12, "teleportRange": 5.0,
    "taunt": "&4&lNowhere to run."
  }
}
```

> **Combo:** Pair with `dodge` — boss evades the hit, then slam fires on next cooldown as a guaranteed counter.

---

### `aoe_smash`

Boss winds up with an expanding particle ring, then slams the ground. Damage and knockback fall off with distance.

| Param | Default | Description |
|-------|---------|-------------|
| `radius` | 5.0 | AoE radius |
| `damage` | 15.0 | Max damage at center |
| `knockback` | 2.0 | Knockback strength |

```json
{
  "type": "aoe_smash",
  "cooldownTicks": 100,
  "params": { "radius": 6.0, "damage": 18.0, "knockback": 2.5, "taunt": "&7&lFeel the ground shake." }
}
```

---

### `charge`

Boss winds up then sprints in a straight line at the target, hitting everything in its path. Each entity is hit only once per charge.

| Param | Default | Description |
|-------|---------|-------------|
| `speed` | 1.5 | Charge speed multiplier |
| `damage` | 15.0 | Damage on impact |
| `distance` | 10.0 | Max charge distance |

```json
{
  "type": "charge",
  "cooldownTicks": 70,
  "params": { "speed": 1.8, "damage": 14.0, "taunt": "&8&lCHARGE!" }
}
```

---

### `teleport`

Enderman-style blink toward the target when they are too far away. Portal particles + enderman teleport sound.

| Param | Default | Description |
|-------|---------|-------------|
| `minDistance` | 8.0 | Minimum distance before teleporting |
| `maxDistance` | 20.0 | Max search range |

```json
{
  "type": "teleport",
  "cooldownTicks": 55,
  "params": { "minDistance": 6.0, "taunt": "&d&lYou blinked." }
}
```

---

### `shield`

Activates a rotating particle shield that reduces incoming damage for a duration.

| Param | Default | Description |
|-------|---------|-------------|
| `durationTicks` | 60 | How long the shield lasts |
| `damageReduction` | 0.8 | Fraction of damage blocked (`0.8` = 80% blocked) |

```json
{
  "type": "shield",
  "cooldownTicks": 120,
  "params": { "durationTicks": 80, "damageReduction": 0.6, "taunt": "&b&lYou cannot break me!" }
}
```

---

### `heal`

Boss channels a heal when below an HP threshold. Green particles spiral upward during cast.

| Param | Default | Description |
|-------|---------|-------------|
| `amount` | 30.0 | Total HP restored |
| `belowPercent` | 0.3 | Only activates below this HP fraction |

```json
{
  "type": "heal",
  "cooldownTicks": 300,
  "params": { "amount": 40.0, "belowPercent": 0.25, "taunt": "&a&lI will not fall!" }
}
```

---

### `ranged_projectile`

Boss charges energy, then fires a projectile volley at the target.

| Param | Default | Description |
|-------|---------|-------------|
| `projectile` | `"minecraft:small_fireball"` | Projectile type |
| `count` | 1 | Projectiles per volley |
| `spread` | 5.0 | Spread angle for multi-shots |

**Projectile options:** `"minecraft:small_fireball"` · `"minecraft:fireball"` (explosive) · `"minecraft:arrow"`

```json
{
  "type": "ranged_projectile",
  "cooldownTicks": 60,
  "params": { "projectile": "minecraft:small_fireball", "count": 3, "spread": 10.0, "taunt": "&6Burn!" }
}
```

---

### `summon_minions`

Spawns mobs from the **phase's `minions` array**. Minions never attack the boss, target the boss's current target, and respect the `maxAlive` cap.

```json
{
  "type": "summon_minions",
  "cooldownTicks": 200,
  "params": { "taunt": "&5&lRise, servants!" }
}
```

Minions are defined on the **phase**, not inside the ability:

```json
"minions": [
  { "entityType": "minecraft:skeleton", "count": 3, "maxAlive": 6, "spawnRadius": 5.0 },
  { "entityType": "minecraft:pillager",  "count": 2, "maxAlive": 4, "spawnRadius": 8.0 }
]
```

| Field | Default | Description |
|-------|---------|-------------|
| `entityType` | **required** | Registry ID — works with modded mobs |
| `count` | 1 | Spawned per summon cast |
| `maxAlive` | 4 | Cap on alive minions of this type |
| `spawnRadius` | 5.0 | Spawn radius around the boss |

---

### `beam`

Boss freezes in place, spiraling particles gather, then a dense particle laser fires at the locked target position. Any player inside the beam cylinder takes damage every tick.

| Param | Default | Description |
|-------|---------|-------------|
| `damage` | 3.0 | Damage per tick inside the beam |
| `width` | 0.8 | Beam cylinder half-width in blocks |
| `duration` | 40 | Ticks the beam fires |

```json
{
  "type": "beam",
  "cooldownTicks": 150,
  "params": { "damage": 4.0, "width": 1.2, "duration": 50, "taunt": "&3&lFocus beam — incoming!" }
}
```

---

### `chain_lightning`

Fires a bolt that jumps between nearby players. Each bounce shows a jagged electric-spark particle arc and a cosmetic lightning strike (no fire, no mob conversion).

| Param | Default | Description |
|-------|---------|-------------|
| `bounces` | 3 | Max players hit |
| `damage` | 8.0 | Damage per bounce |
| `radius` | 12.0 | Chain search radius |

```json
{
  "type": "chain_lightning",
  "cooldownTicks": 120,
  "params": { "bounces": 5, "damage": 10.0, "radius": 14.0, "taunt": "&e&lFeel the storm!" }
}
```

---

### `orbital`

Summons glowing orbs that orbit the boss for a duration. Orbs are rendered as `end_rod` + `soul_fire_flame` particles and deal damage on contact. Ends with a dispersal burst.

| Param | Default | Description |
|-------|---------|-------------|
| `count` | 3 | Number of orbs |
| `radius` | 3.0 | Orbit radius |
| `damage` | 6.0 | Damage per tick on contact |
| `duration` | 100 | Ticks the orbs last |
| `speed` | 8.0 | Rotation speed in degrees per tick |

```json
{
  "type": "orbital",
  "cooldownTicks": 180,
  "params": { "count": 5, "radius": 3.5, "damage": 5.0, "duration": 130, "speed": 9.0 }
}
```

---

### `meteor`

Summons fireballs or wither skulls falling from the sky above the target. Ground warning rings appear before impact.

| Param | Default | Description |
|-------|---------|-------------|
| `count` | 3 | Meteors per cast |
| `height` | 20.0 | Blocks above target where they spawn |
| `type` | `"fireball"` | `"fireball"` or `"wither_skull"` |

```json
{
  "type": "meteor",
  "cooldownTicks": 200,
  "params": { "count": 5, "height": 25.0, "type": "wither_skull", "taunt": "&4&lFrom above!" }
}
```

---

### `pull`

Creates a vortex that pulls all nearby players toward the boss. Portal particles spiral inward during the pull. Ends with an implosion burst.

| Param | Default | Description |
|-------|---------|-------------|
| `radius` | 10.0 | Pull radius |
| `strength` | 0.8 | Pull force |
| `duration` | 20 | Ticks the vortex lasts |

```json
{
  "type": "pull",
  "cooldownTicks": 160,
  "params": { "radius": 12.0, "strength": 1.5, "duration": 30, "taunt": "&5&lCome here!" }
}
```

---

### `flames`

Spawns randomized flame particles around the boss — imitates the look of a mob spawner fire effect. Purely visual; the boss keeps moving and attacking freely.

| Param | Default | Description |
|-------|---------|-------------|
| `radius` | 3.0 | Spread radius of the flames in blocks |
| `duration` | 60 | Ticks the effect lasts |
| `density` | 6 | Flame particles spawned per tick |

```json
{ "type": "flames", "cooldownTicks": 80, "params": { "radius": 3.5, "duration": 60, "density": 8 } }
```

---

### `freeze`

Freezes nearby players for a configurable duration. Re-applies frozen ticks every tick to hold the effect steady — players won't thaw until the duration expires.

| Param | Default | Description |
|-------|---------|-------------|
| `duration` | 60 | How long the freeze is held in ticks (20 = 1 second, 100 = 5 seconds) |
| `intensity` | 140 | Frozen ticks value maintained each tick — `140` = full vignette overlay (player cap) |
| `radius` | 8.0 | Range to freeze players |

```json
{ "type": "freeze", "cooldownTicks": 120, "params": { "duration": 80, "intensity": 140, "radius": 10.0, "taunt": "&b&lTime... stops." } }
```

> A glass-break sound plays 1 second before the freeze ends as a warning.

---

### `random_message`

Picks a random message from a list and sends it to nearby players. Useful for taunts, lore, or boss voice lines. No movement lock — fires instantly.

| Param | Default | Description |
|-------|---------|-------------|
| `messages` | **required** | JSON array of message strings — supports `&` color codes |
| `radius` | 32.0 | Broadcast radius in blocks |

```json
{
  "type": "random_message",
  "cooldownTicks": 200,
  "params": {
    "messages": ["&cYou will fall!", "&4&lNone survive!", "&6Fear me, mortal!"],
    "radius": 40.0
  }
}
```

---

### `particle_tornado`

Summons a rising tornado of particles centered on the boss. Narrow at the base, wide at the top. Rotates every tick. Optional damage pulls players caught inside toward the center. Boss freezes in place while active.

| Param | Default | Description |
|-------|---------|-------------|
| `maxRadius` | 4.0 | Radius at the top of the tornado in blocks |
| `height` | 6.0 | Total height of the tornado |
| `duration` | 100 | Ticks the tornado lasts |
| `rotationSpeed` | 8.0 | Degrees rotated per tick |
| `disks` | 12 | Number of horizontal rings forming the funnel |
| `damage` | 0.0 | Damage per 5 ticks to players inside (`0` = purely visual) |

```json
{
  "type": "particle_tornado",
  "cooldownTicks": 180,
  "params": {
    "maxRadius": 5.0,
    "height": 7.0,
    "duration": 120,
    "rotationSpeed": 10.0,
    "disks": 14,
    "damage": 1.5,
    "taunt": "&5&lThe vortex consumes you!"
  }
}
```

---

### `swap`

Instantly swaps the boss's position with the target's — teleporting the boss to where the player stood and the player to where the boss stood. Both locations get a burst of portal + reverse-portal particles and a pitch-shifted enderman teleport sound so players can read what happened.

| Param | Default | Description |
|-------|---------|-------------|
| `maxDistance` | 20.0 | Only activates if target is within this range |
| `minDistance` | 3.0 | Won't activate if target is already this close (swap would be pointless) |

```json
{ "type": "swap", "cooldownTicks": 90, "params": { "maxDistance": 20.0, "minDistance": 4.0, "taunt": "&5&lSurprise!" } }
```

> Pairs well with `aoe_smash` or `arc_slash` — swap places the player at ground zero right before the attack lands.

---

## Loot

```json
"loot": [
  {
    "item": "minecraft:diamond_sword",
    "nbt": "{display:{Name:'{\"text\":\"Bone Cleaver\",\"color\":\"gold\",\"bold\":true,\"italic\":false}'},Enchantments:[{id:\"minecraft:sharpness\",lvl:5}]}",
    "count": 1,
    "chance": 0.15
  },
  { "item": "minecraft:diamond", "count": 8,  "chance": 1.0 },
  { "item": "minecraft:nether_star", "count": 1, "chance": 0.05 }
]
```

| Field | Default | Description |
|-------|---------|-------------|
| `item` | **required** | Registry ID (`"minecraft:diamond"`, `"mymod:item"`) |
| `nbt` | null | SNBT string for enchantments, custom names, etc. |
| `count` | 1 | Stack size |
| `chance` | 1.0 | Drop chance (`1.0` = always, `0.05` = 5%) |

---

## Behavior Notes

**Aggro**
- Targets the nearest player on spawn
- Every 5–15 seconds: 40% chance to switch to a random nearby player
- When hit by a non-targeted player: 35% chance to switch to them (revenge)

**Strafing**
- At 2–7 blocks from target the boss circles sideways, changing direction randomly
- Strafing is automatically suppressed while any ability that locks movement is running (beam, slam, arc_slash, charge, etc.)

**Goal Priority**
- Ability goals fire before basic melee — they are never interrupted mid-cast
- Multiple abilities share priority slots 1–5; basic melee is at priority 6

**Persistence**
- Bosses never despawn
- Phase index is saved to NBT and restored on world reload
- Deleting a boss config and running `/boss reload` removes the entity from the world
