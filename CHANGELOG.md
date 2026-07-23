# Changelog

All notable changes to this project are documented here. Each release's section below
is used verbatim as the GitHub Release notes (the release workflow extracts the entry
matching the tag).

## [1.1.3]

### Added
- **`wind_charge` boss/minion ability** — leaps toward the target and slams down on landing
  (no fall damage to the boss itself). This is a fully custom, config-driven mace-style
  jump-slam: `damage`, `radius`, `knockback`, `launchPower`, `jumpPower`, `horizontalSpeed`,
  `minRange`/`maxRange`, and an optional `fallDamagePerBlock` (capped by `maxFallBonus`) for a
  vanilla-flavored fall-scaling bonus — entirely independent of whatever is actually equipped.
  A boss can visibly wield a `minecraft:mace` and still hit for 1 damage here, or the reverse.
  See `BOSS_CONFIG_DOCS.md` for the full param table.
- **Note on real maces:** equipping `"minecraft:mace"` in `equipment.mainHand` already gives any
  boss/minion the authentic vanilla smash-attack mechanic for free (mobs holding a mace get it
  automatically, same as players) — no new code was needed for that. `wind_charge` is the
  separate, tunable alternative; the two can be combined or used independently.
- On 1.20.1 (pre-1.21, no mace item or Gust particles), `wind_charge` behaves identically —
  jump/slam/knockback are entirely our own code — with period-appropriate particles/sounds.

### Fixed
- `/boss minion spawn` used to reject any minion definition with a non-`"custom"` `baseEntity`
  ("uses a vanilla base entity — spawn it via a boss with summon_minions"), even though the identical
  definition worked fine when dropped in the `bosses/` folder and spawned with `/boss spawn`. Vanilla-base
  minions can now be spawned directly by command — the same stat/equipment-override logic
  `summon_minions` already used is now shared by the command path.
- Damage protection (`protection` maps) is now applied before adaptation tracking records the hit,
  so fully-blocked damage is never recorded and partial protection is reflected in what adaptation
  actually resists.

### Notes
- All features are available on every supported version and loader (1.21.11, 1.21.8, 1.21.1, 1.20.1 —
  Fabric, NeoForge, Forge).

## [1.1.2]

### Added
- **Damage protection maps** — new `protection` config on bosses, phases, and custom minions.
  Scale or block incoming damage per weapon item id (`"minecraft:mace": 0.2`), exact damage type id
  (`"minecraft:mace_smash"`, `"minecraft:spear"`), or category (`melee`, `projectile`, `magic`, `fire`,
  `explosion`, `fall`, `lightning`, `freezing`, `drowning`). `0` = fully immune. Phase maps override the
  boss map key-by-key. Built to tame the overpowered vanilla mace/spear and custom server weapons —
  on versions without the dedicated damage types (mace_smash is 1.21.8+, spear is 1.21.11 only), the
  item-id key works instead.
- **Faction system** — optional `faction` id on bosses and custom minions. Allies (same faction, or a
  boss and its own minions) don't retaliate against each other and their abilities/AoE can't hurt each
  other. Fully configurable per entity via `damageFactionAllies`, `targetFactionAllies`, and
  `damageOwnGroup` (all default `false`, preserving previous behavior).
- **Spawn counts** — `/boss spawn <id> [count]`, `/boss spawn <id> <pos> [count]`, and
  `/boss minion spawn <id> [count]` can now spawn multiple entities at once (default 1, no cap;
  extra spawns are slightly scattered).
- **Loot count ranges** — loot entries support `minCount`/`maxCount` for random drop amounts
  (e.g. 10–120 diamonds at 50% chance). Oversized amounts split into multiple stacks automatically.
- **Water/lava handling** — new optional `fluid` block on bosses and custom minions:
  `drownImmune`, `fireImmune`, `floats` (false = sinks and fights underwater), `swimSpeed`,
  `pushedByFluids`, and `canSwim` (pathfinding stops avoiding water so bosses chase players into
  rivers instead of being cheesed from the shore).

### Changed
- **All ability chat messages are now optional.** The `shield`, `heal`, `teleport`, and
  `summon_minions` abilities no longer print built-in fallback taunts ("Your attacks are futile!",
  "You cannot stop me!", "Behind you...", "Rise, my servants!") when no `taunt` is configured —
  omit the param and the ability is silent. Configure `taunt` to restore a message.
- Ability target filtering was unified across all AoE/melee abilities using the new ally rules. Abilities
  that previously could never hit another boss (aoe_smash, melee_slash, slam, shockwave, charge) can now
  hit **non-allied** bosses, so rival boss fights behave consistently across all abilities.

### Fixed
- `/boss minion spawn` used to reject any minion definition with a non-`"custom"` `baseEntity`
  ("uses a vanilla base entity — spawn it via a boss with summon_minions"), even though the identical
  definition worked fine when dropped in the `bosses/` folder and spawned with `/boss spawn`. Vanilla-base
  minions can now be spawned directly by command — the same stat/equipment-override logic
  `summon_minions` already used is now shared by the command path.

### Notes
- Vanilla-base minions (`baseEntity` set to a real mob id) can't use `protection`, `faction`, or `fluid`
  — those apply to bosses and custom minions only.
- All features are available on every supported version and loader (1.21.11, 1.21.8, 1.21.1, 1.20.1 —
  Fabric, NeoForge, Forge).

## [1.1.1]

### Added
- Added three new configurable boss abilities on every supported Minecraft version and loader:
  - `blink_strike`: telegraphs a target, blinks behind or near them, then hits nearby players with a sweep strike.
  - `curse_bomb`: marks one or more players with delayed soul bombs that explode around them, rewarding spread-out positioning.
  - `soul_tether`: chains players to the boss with visible soul tethers, pulls them inward, pulses damage, and punishes players who stretch the tether too far.

### Changed
- Updated the release workflow fallback to accept an explicit tag/version and keep Modrinth Fabric API dependencies in the shared publish path.

## [1.1.0]

> ⚠️ **Major rewrite — expect bugs.** This is the biggest change since the mod began, and it
> is still in progress. Because so much was rewritten, it is **not fully tested** — some
> abilities or mechanics may be broken or behave differently than before. Many mechanics will
> be play-tested and fixed before the main release, and small hotfixes / minor updates are
> expected afterwards. Please report anything broken on the
> [issue tracker](https://github.com/Fi3w0/Fiw-Bosses/issues).

### Added
- **Mono-repo build** replacing the old branch-per-loader layout — every target builds from one tree:
  - Fabric & NeoForge for Minecraft **1.21.11**, **1.21.8**, and **1.21.1**
  - Fabric & **Forge** for Minecraft **1.20.1** (brand-new Forge module — NeoForge has no 1.20.1)
- The widest loader/version support of any release so far (8 targets), all sharing one feature set.
- **More boss abilities** — an expanded set of configurable abilities (52 in total).
- Six new configurable boss abilities, available on every supported Minecraft version and loader:
  - `rift_cleave`: wind-up line cleave with soul/sculk rift visuals, damage, width, range, knockback, linger, and taunt controls.
  - `fear_burst`: Warden-inspired soul burst with Darkness, Weakness, Slowness, knockback, optional damage, and taunt support.
  - `mirror_image`: temporary decoys with optional boss invisibility, periodic swaps, reappear effects, and optional reappear damage.
  - `sacrifice_minion`: consumes owned minions to heal the boss and damage nearby players at the sacrificed minion location.
  - `last_breath`: low-health interruptible channel that releases a large soul blast if players do not deal enough damage.
  - `wither_crown`: orbiting wither skull crown that fires real wither skull projectiles one by one.
- Two new crowd-control abilities:
  - `gravity_well`: sucks players into a swirling well, lifts them with Levitation, then drops them with optional impact damage.
  - `shadow_clone`: spawns fake boss copies that mirror the boss's disguise and skin, follow the boss, and apply an optional debuff on death.
- Four new defensive/survival abilities:
  - `cleanse`: strips all harmful effects off the boss and briefly blocks new debuffs.
  - `second_wind`: one-shot auto-revive that negates a fatal blow and restores partial health, then re-arms after its cooldown.
  - `adaptation`: passively grows resistant to whichever damage type (`melee`/`projectile`/`magic`/`fire`/`explosion`) has recently hurt the boss most.
  - `rewind`: records position and health and snaps the boss back a few seconds when low — intentionally very strong, meant for long cooldowns.
- **Vanilla mob visuals for FIW entities** — bosses and custom minions can now look like vanilla/modded
  mobs while still using FIW boss/minion logic. Use `renderEntity` on `baseEntity: "custom"` minions,
  or set a boss `baseEntity` / `renderEntity` to a registry ID such as `minecraft:zombie`.
- **Vanilla mobs as simple minions** — minions can still use true vanilla/modded entity types
  (e.g. `baseEntity: "minecraft:zombie"`) for simple summons with custom stats, equipment, and loot.
  These keep native mob AI and are intentionally less customizable than FIW custom minions.
- Optional **Fiw Custom Items** integration — reference Fiw Custom Items entries by `toolId` in boss/phase/minion
  equipment and loot. Reflection-only, no hard dependency; skipped if Fiw Custom Items is absent.
  - Example configs under `examples/fiw_tools_integration/` (Void Reaver boss plus its custom items).

### Changed
- **Reworked several boss abilities** and **reworked minion/boss behavior** (AI, targeting, movement modes,
  vanilla-mob handling).
- Clarified and documented the recommended minion setup: use `baseEntity: "custom"` with `renderEntity`
  when you want full FIW abilities/smart movement but a vanilla mob appearance.
- **Same JSON config format on every loader and version** — and still backward-compatible with configs
  written for **1.0.9 and earlier** (including the old item NBT format), so existing bosses keep working.
- Ability JSON docs were refreshed to match the current implementation, including `beam`, `domain`,
  `particle_tornado`, `phantom_dash`, `tracking_orb`, `moving_tornado`, `detect_mark`, `dodge`, and the
  six new abilities.
- Rewritten `README.md` with current supported versions, module layout, ability list, install notes,
  commands, and build instructions.
- `beam` is now configurable while preserving previous defaults:
  `particle`, `coreParticle`, and `maxLength`.
- `particle_tornado` now has configurable visuals and behavior:
  `particle`, `accentParticle`, `fire`, `fireSeconds`, `size`/`maxRadius`, `height`, `spinSpeed`/`rotationSpeed`,
  `twist`, `disks`, `damage`, and `duration`.
- `dodge` now supports `taunt`, using the same nearby-player boss message style as other taunt-enabled abilities.
- Added configurable smart movement modes for bosses and custom minions: `hit_and_run`, `guard_point`,
  `phase_walk`, `hover`, `sniper`, and `berserk`. Existing `side`/`normal` strafing remains the default,
  and `vanilla` disables the smart movement overlay.
- `phase_walk` uses collision-checked teleport destinations so the entity does not blink into blocks.
- Large parts of the mod rewritten for performance and long-term maintainability; shared logic centralized
  in a Minecraft-free `core` engine plus per-version `common` source sets.
- `core` is bundled into every loader jar; Gson + SLF4J are compile-only (provided by Minecraft).

### Fixed
- `/boss reload` correctly requires op level 3 (admins) again, instead of inheriting the level-2 gate
  of the root `/boss` command.
- Client skin textures are now released when a boss/minion unloads (Fabric `ENTITY_UNLOAD`,
  NeoForge/Forge `EntityLeaveLevelEvent`), fixing a GPU texture/resource leak.
- `domain` no longer recasts immediately after ending. Domain cooldown now lives on the boss entity, survives
  goal rebuilds, supports `params.cooldown`, and defaults to at least 1800 ticks when not specified.
- `domain` speed buffs are removed cleanly when the domain ends, so the boss returns to normal behavior.
- `phantom_dash` no longer teleports the boss into blocks on slopes or near walls. Dashes now clip against
  walls, snap to safe ground within a small vertical search, and fizzle in place if no safe landing exists.
- `particle_tornado` visuals now actually read as a spinning funnel using per-disk helix twist and upward drift.
- The 1.21.11 ability fixes were ported to **1.21.8**, **1.21.1**, and **1.20.1** with matching logic and JSON format.
- `renderEntity` / vanilla-mob disguises now work on **every loader and version** (Fabric & NeoForge 1.21.8 / 1.21.1,
  Fabric & Forge 1.20.1), not just 1.21.11. The disguise id is synced to clients (`BossRenderPayload`, or Forge's
  `BossRenderMessage`, plus a client-side disguise registry), FIW boss/minion render states stay on the FIW renderer
  path and draw the vanilla disguise instead of falling back to Steve, and walk animation is copied onto the disguised
  mob — so configs such as `renderEntity: "minecraft:wither_skeleton"` render correctly in real multiplayer/client worlds.
- 1.21.8 disguises were drawn at a doubled camera offset (only the shadow showed, with no visible body); the disguise
  is now rendered at the boss's actual position.

### Known issues
- Because this is a rewrite, some abilities/mechanics may be broken, untested, or inconsistent between
  loaders — bugs are expected, and fixes will follow in small updates.
- In-game testing is incomplete: configuration loading is verified on every target, but the abilities,
  persistence, vanilla-mob behavior, and Fiw Custom Items paths are not yet fully play-tested across all eight targets.
- The shared renderer is a "safe humanoid" model — slim arms and exact player outer layers are not yet matched.
- On 1.21.1 and 1.20.1, `renderEntity` disguised mobs render with static limbs while moving — limb-swing
  animation is only copied on 1.21.8 and 1.21.11. Orientation and tick-based idle motion still animate.
- Player skins require an internet connection at server start (Mojang API lookup).
