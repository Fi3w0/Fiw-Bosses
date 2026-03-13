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
  - [shockwave](#shockwave)
  - [slash_wave](#slash_wave)
  - [sonic_boom](#sonic_boom)
  - [domain](#domain)
  - [ice_crystal](#ice_crystal)
  - [fire_arrow](#fire_arrow)
  - [crimson_slash](#crimson_slash)
  - [singularity_cannon](#singularity_cannon)
  - [lightning_radial](#lightning_radial)
- [Idle System](#idle-system)
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
| `idleTimeout` | int | -1 | Ticks with no nearby player before idle action triggers. `-1` disables (default) |
| `idleAction` | string | `"despawn"` | What to do when idle: `"despawn"` removes the boss, `"heal"` gradually restores HP |
| `idleHealAmount` | float | 2.0 | HP restored per heal tick (only when `idleAction = "heal"`) |
| `idleHealInterval` | int | 40 | Ticks between each heal tick (only when `idleAction = "heal"`) |

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

### `shockwave`

Boss slams the ground sending out expanding rings of energy. Players on the ground when a ring passes through them take damage — **jumping over a ring avoids it entirely**. Rings expand outward from the boss and are staggered in time.

| Param | Default | Description |
|-------|---------|-------------|
| `damage` | 8.0 | Damage per ring hit |
| `waves` | 3 | Number of rings |
| `maxRadius` | 14.0 | Max radius the rings travel |
| `waveSpeed` | 0.55 | Blocks per tick each ring expands |
| `knockback` | 0.5 | Horizontal knockback on hit |
| `windupTicks` | 20 | Charge ticks before first ring fires |

```json
{
  "type": "shockwave",
  "cooldownTicks": 200,
  "params": {
    "damage": 10.0, "waves": 3, "maxRadius": 16.0,
    "waveSpeed": 0.6, "windupTicks": 18,
    "taunt": "&6&lDodge... if you can."
  }
}
```

> Players with upward velocity (jumping) are immune to each ring they are airborne over.

---

### `slash_wave`

The boss charges and fires a fast-traveling blade of energy that advances in a straight line. Like `arc_slash` but the slash follows a path — players must dodge sideways. A brief windup tracks the target before locking direction.

| Param | Default | Description |
|-------|---------|-------------|
| `damage` | 14.0 | Damage on hit |
| `speed` | 1.8 | Blocks per tick the slash advances |
| `range` | 20.0 | Max distance it travels |
| `width` | 1.4 | Half-width of the hitbox perpendicular to travel |
| `chargeTime` | 10 | Windup ticks before firing |

```json
{
  "type": "slash_wave",
  "cooldownTicks": 160,
  "params": {
    "damage": 14.0, "speed": 2.0, "range": 22.0,
    "width": 1.5, "chargeTime": 10,
    "taunt": "&7&lNo escaping this."
  }
}
```

> Knockback is applied sideways (perpendicular to travel) for disorientation. High `speed` values make the slash very hard to outrun — keep `chargeTime` generous so players can react.

---

### `sonic_boom`

Warden-style charge-and-release. The boss charges with rising vibration particles and a growing rumble, then fires a devastating sound pulse that **ignores armor** (magic damage). Players in the blast get knocked back and optionally receive Darkness.

| Param | Default | Description |
|-------|---------|-------------|
| `damage` | 30.0 | Damage on hit — bypasses armor |
| `radius` | 15.0 | Max range |
| `coneAngle` | 60.0 | Half-angle of the cone in degrees (`180` = full circle) |
| `chargeTime` | 40 | Charge ticks before the boom fires |
| `knockback` | 2.0 | Knockback strength |
| `darkness` | true | Apply Darkness (4 seconds) to hit players |

```json
{
  "type": "sonic_boom",
  "cooldownTicks": 220,
  "params": {
    "damage": 28.0, "radius": 16.0, "coneAngle": 65.0,
    "chargeTime": 45, "knockback": 2.5, "darkness": true,
    "taunt": "&8&lBrace yourself."
  }
}
```

> The charge is clearly telegraphed with sculk particles and sound — players should retreat behind the boss's back or break line of sight. `coneAngle: 180` creates a full-area blast with no safe direction.

---

### `domain`

**The boss's ultimate ability.** Seals a multi-layered dark particle sphere around nearby players. The sphere is **anchored to the activation position** — it does not move. Neither the boss nor trapped players can leave the sphere. Outsiders are pushed away on contact. When the domain collapses a custom `domain_break` sound fires. If all trapped players die, the domain ends early.

The boss can run a completely separate set of attacks while the domain is active (`attacks` array). Its speed can also be overridden independently.

**Sphere visuals** (6 layers):
- Outer shell — 20 rotating latitude rings (PORTAL / WARPED_SPORE / REVERSE_PORTAL)
- Inner shell — 12 counter-rotating rings at 72% radius (SCULK_CHARGE_POP)
- Equatorial band — dense highlight ring at the equator (SOUL)
- Ground ring — flat circle at the sphere floor with inner concentric ring
- Polar caps — glow rings at top and bottom poles
- Ambient fill — floating particles drifting inside the dome

| Param | Default | Description |
|-------|---------|-------------|
| `radius` | 15.0 | Sphere radius in blocks |
| `duration` | 300 | Max duration in ticks (300 = 15 s) |
| `domainSpeed` | 0.36 | Boss movement speed inside the domain |
| `pushDamage` | 3.0 | Damage applied to outsiders trying to enter |
| `pullDamage` | 3.0 | Damage applied to insiders trying to escape |
| `darkness` | true | Apply Darkness to trapped players every second |
| `blindness` | false | Apply Blindness to trapped players every second |
| `taunt` | null | Message on activation |
| `attacks` | [] | Domain-specific abilities (same format as phase abilities). If empty, the boss uses its current phase goals |

```json
{
  "type": "domain",
  "cooldownTicks": 600,
  "params": {
    "radius": 18,
    "duration": 240,
    "domainSpeed": 0.38,
    "pushDamage": 4.0,
    "pullDamage": 4.0,
    "darkness": true,
    "blindness": false,
    "taunt": "&5&lDOMAIN EXPANSION",
    "attacks": [
      { "type": "sonic_boom",  "cooldown": 100, "params": { "damage": 20, "radius": 16, "coneAngle": 180, "chargeTime": 30 } },
      { "type": "slash_wave",  "cooldown": 80,  "params": { "damage": 12, "speed": 2.0, "range": 16 } },
      { "type": "shockwave",   "cooldown": 120, "params": { "damage": 9,  "waves": 3,   "maxRadius": 15 } }
    ]
  }
}
```

> **Sound file:** The domain collapse plays `domain_break` — place your sound file at `src/main/resources/assets/fiw_bosses/sounds/domain_break.ogg`. Minecraft requires **`.ogg` format** — convert `.mp3` files with Audacity or ffmpeg (`ffmpeg -i domain_break.mp3 domain_break.ogg`).

> `attacks` entries use `"cooldown"` (not `"cooldownTicks"`) inside the domain attacks array.

---

### `ice_crystal`

Summons a burst of giant ice crystals from the ground in a snowflake pattern. Enemies in the radius are slowed; enemies in the center are nearly frozen solid. Crystals persist for a short duration then shatter.

| Param | Default | Description |
|-------|---------|-------------|
| `radius` | 10.0 | Full snowflake radius in blocks |
| `centerRadius` | 4.0 | Inner radius that applies the hard freeze |
| `damage` | 8.0 | Damage applied on activation |
| `slowDuration` | 60 | Ticks of Slowness IV for outer ring players |
| `freezeDuration` | 30 | Ticks of Slowness VIII + Mining Fatigue III for center players (near-freeze) |
| `windupTicks` | 15 | Ticks of windup before crystals erupt |
| `duration` | 100 | Ticks the crystal field persists |

```json
{ "type": "ice_crystal", "cooldownTicks": 220, "params": { "radius": 10, "centerRadius": 4, "damage": 9, "slowDuration": 65, "freezeDuration": 32 } }
```

---

### `fire_arrow`

Boss charges a blazing energy arrow for a short duration (tick sound at mid-charge), then fires it at high speed. The arrow explodes on player contact or when it reaches max range. No terrain damage.

| Param | Default | Description |
|-------|---------|-------------|
| `chargeTime` | 25 | Ticks to charge before firing |
| `damage` | 20.0 | Max explosion damage (falls off with distance) |
| `explosionRadius` | 4.0 | Blast radius |
| `speed` | 2.5 | Blocks per tick |
| `range` | 25.0 | Max travel distance before exploding |

```json
{ "type": "fire_arrow", "cooldownTicks": 160, "params": { "chargeTime": 25, "damage": 20, "explosionRadius": 4, "speed": 2.5, "range": 25 } }
```

---

### `crimson_slash`

Launches 3 consecutive energy claws along the ground toward the target. Each claw is larger than the last and leaves a glowing crimson trail. After all 3 claws converge, they explode in a rising dark-flame wave.

| Param | Default | Description |
|-------|---------|-------------|
| `damage` | 12.0 | Base claw damage (each successive claw deals +20%) |
| `range` | 16.0 | Distance each claw travels |
| `clawCount` | 3 | Number of consecutive claws |
| `clawSpeed` | 1.2 | Blocks per tick |
| `explosionRadius` | 5.0 | Final explosion radius |
| `delayBetweenClaws` | 12 | Ticks between consecutive claw launches |

```json
{ "type": "crimson_slash", "cooldownTicks": 140, "params": { "damage": 12, "range": 16, "clawCount": 3, "explosionRadius": 5 } }
```

---

### `singularity_cannon`

Boss channels a rotating plasma ring in front of itself that grows and spins for the charge duration, with a chromatic aberration ring of rainbow dust. Nearby players are slowed 30% during the charge. Fires a high-speed particle beam that drags caught players forward.

| Param | Default | Description |
|-------|---------|-------------|
| `chargeTime` | 30 | Ticks to charge the ring |
| `damage` | 25.0 | Beam damage per player hit |
| `range` | 20.0 | Beam max travel distance |
| `beamWidth` | 1.2 | Hit radius of the beam |
| `beamSpeed` | 3.0 | Blocks per tick |

```json
{ "type": "singularity_cannon", "cooldownTicks": 180, "params": { "chargeTime": 30, "damage": 25, "range": 20, "beamWidth": 1.2 } }
```

---

### `lightning_radial`

Boss leaps 3 blocks into the air (immune during ascent), channels electric energy in a growing spiral, then fires 16–24 energy blades simultaneously in all directions at ground level. Each blade travels 12 blocks, and impact points scatter ash shrapnel.

| Param | Default | Description |
|-------|---------|-------------|
| `bladeCount` | 20 | Number of blades fired in 360° |
| `bladeRange` | 12.0 | Distance each blade travels |
| `damage` | 16.0 | Damage per blade hit |
| `channelTime` | 16 | Ticks of channeling after landing |
| `bladeSpeed` | 1.5 | Blocks per tick per blade |

```json
{ "type": "lightning_radial", "cooldownTicks": 220, "params": { "bladeCount": 20, "bladeRange": 12, "damage": 16, "channelTime": 16 } }
```

---

### `orb_throw`

Summons a green mystic orb that orbits the boss with three spinning ring layers (END_ROD, ENCHANT, tilted yellow dust), then launches it at the target. On hit or reaching max range, it explodes with knockback.

| Param | Default | Description |
|-------|---------|-------------|
| `orbitTime` | 50 | Ticks spent orbiting before launch |
| `speed` | 1.5 | Orb travel speed (blocks/tick) |
| `range` | 22.0 | Max travel range before explosion |
| `damage` | 12.0 | Explosion damage at center |
| `knockback` | 3.5 | Knockback force |
| `explosionRadius` | 6.0 | Explosion area radius |

```json
{ "type": "orb_throw", "cooldownTicks": 140, "params": { "orbitTime": 50, "speed": 1.5, "range": 22, "damage": 12, "knockback": 3.5, "explosionRadius": 6 } }
```

---

### `tracking_orb`

A passive purple orb that follows the boss in a figure-8 pattern and periodically fires homing projectiles at nearby players. Uses `EnumSet.noneOf` so the boss can move and use other abilities simultaneously.

| Param | Default | Description |
|-------|---------|-------------|
| `orbitRadius` | 2.5 | Radius of the figure-8 orbit around the boss |
| `fireRate` | 30 | Ticks between projectile shots |
| `projectileSpeed` | 0.8 | Speed of each fired projectile |
| `projectileDamage` | 5.0 | Damage per projectile hit |
| `duration` | 300 | Total ticks the orb stays active |

```json
{ "type": "tracking_orb", "cooldownTicks": 200, "params": { "orbitRadius": 2.5, "fireRate": 30, "projectileSpeed": 0.8, "projectileDamage": 5, "duration": 300 } }
```

---

### `moving_tornado`

A tornado appears in front of the boss and advances toward the target. Players inside the radius are pulled toward the center and lifted if in the inner vortex core.

| Param | Default | Description |
|-------|---------|-------------|
| `radius` | 4.0 | Outer radius of the tornado |
| `height` | 8.0 | Visual height of the funnel |
| `speed` | 0.3 | Blocks/tick the tornado moves |
| `range` | 24.0 | Max travel distance |
| `pullForce` | 0.15 | Horizontal pull toward vortex center |
| `liftForce` | 0.2 | Upward lift applied inside inner vortex |
| `damage` | 4.0 | Damage dealt per hit tick |
| `damageTick` | 15 | Ticks between damage applications |
| `windupTicks` | 25 | Ticks of windup swirl before tornado moves |

```json
{ "type": "moving_tornado", "cooldownTicks": 160, "params": { "radius": 4, "height": 8, "speed": 0.3, "range": 24, "pullForce": 0.15, "liftForce": 0.2, "damage": 4, "damageTick": 15 } }
```

---

### `ground_spike`

Boss marks multiple positions with pulsing indicators, then FallingBlock columns erupt from the ground launching players upward. No permanent terrain modification.

| Param | Default | Description |
|-------|---------|-------------|
| `radius` | 10.0 | Radius around target to distribute spikes |
| `spikeCount` | 8 | Number of spike columns |
| `damage` | 12.0 | Damage on spike eruption |
| `knockback` | 2.5 | Upward knockback force |
| `markTicks` | 40 | Duration of the warning phase |
| `spikeTicks` | 20 | Duration of the spike phase |

```json
{ "type": "ground_spike", "cooldownTicks": 180, "params": { "radius": 10, "spikeCount": 8, "damage": 12, "knockback": 2.5, "markTicks": 40, "spikeTicks": 20 } }
```

---

### `arrow_rain`

Boss marks a circular area with an orange warning ring, then rains actual `ArrowEntity` projectiles from above. Arrows are non-pickup and deal configurable damage.

| Param | Default | Description |
|-------|---------|-------------|
| `radius` | 8.0 | Radius of the rain zone |
| `arrowCount` | 20 | Total arrows per volley |
| `height` | 20.0 | Height above ground from which arrows fall |
| `damage` | 8.0 | Arrow damage |
| `warnTicks` | 40 | Duration of the warning ring phase |
| `rainTicks` | 30 | Duration of the arrow rain phase |

```json
{ "type": "arrow_rain", "cooldownTicks": 150, "params": { "radius": 8, "arrowCount": 20, "height": 20, "damage": 8, "warnTicks": 40, "rainTicks": 30 } }
```

---

### `potion_field`

Boss throws a potion in an arc; on landing it creates a persistent ground field that applies a configurable status effect to players inside.

| Param | Default | Description |
|-------|---------|-------------|
| `effect` | `"minecraft:slowness"` | Status effect identifier (e.g. `"minecraft:poison"`, `"minecraft:weakness"`) |
| `amplifier` | 1 | Effect level (0 = level I) |
| `effectDuration` | 100 | Ticks of effect applied per interval |
| `applyInterval` | 20 | Ticks between effect applications |
| `fieldDuration` | 200 | Total ticks the field persists |
| `fieldRadius` | 5.0 | Radius of the effect zone |
| `damage` | 0.0 | Direct damage on landing (0 = none) |
| `throwSpeed` | 0.6 | Initial projectile speed |

```json
{ "type": "potion_field", "cooldownTicks": 120, "params": { "effect": "minecraft:poison", "amplifier": 0, "effectDuration": 80, "applyInterval": 20, "fieldDuration": 200, "fieldRadius": 6, "damage": 3 } }
```

---

## Idle System

Bosses are persistent by default — they never despawn and never regen HP. The idle system lets you override this when no players are nearby.

**Trigger condition:** no alive, non-spectator, non-creative player within 64 blocks for `idleTimeout` ticks.

Idle timer is reset when:
- A player comes within 64 blocks
- The boss takes any damage

```json
{
  "idleTimeout": 6000,
  "idleAction": "despawn"
}
```

```json
{
  "idleTimeout": 3600,
  "idleAction": "heal",
  "idleHealAmount": 3.0,
  "idleHealInterval": 40
}
```

| Field | Default | Description |
|-------|---------|-------------|
| `idleTimeout` | -1 | Ticks before idle action triggers. `-1` = disabled (default — no idle behavior) |
| `idleAction` | `"despawn"` | `"despawn"` removes the entity; `"heal"` gradually restores HP |
| `idleHealAmount` | 2.0 | HP healed per interval |
| `idleHealInterval` | 40 | Ticks between each heal tick (40 = 2 s) |

> Ticks reference: 20 ticks = 1 second · 600 ticks = 30 s · 1200 ticks = 1 min · 6000 ticks = 5 min

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
