# Changelog

All notable changes to this project are documented here. Each release's section below
is used verbatim as the GitHub Release notes (the release workflow extracts the entry
matching the tag). The newest version goes on top.

The format is based on [Keep a Changelog](https://keepachangelog.com/en/1.1.0/),
and this project aims to follow [Semantic Versioning](https://semver.org/spec/v2.0.0.html).

## [1.1.0] - 2026-06-24

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
- **More boss abilities** — an expanded set of configurable abilities (48 in total).
- Six new configurable boss abilities, available on every supported Minecraft version and loader:
  - `rift_cleave`: wind-up line cleave with soul/sculk rift visuals, damage, width, range, knockback, linger, and taunt controls.
  - `fear_burst`: Warden-inspired soul burst with Darkness, Weakness, Slowness, knockback, optional damage, and taunt support.
  - `mirror_image`: temporary decoys with optional boss invisibility, periodic swaps, reappear effects, and optional reappear damage.
  - `sacrifice_minion`: consumes owned minions to heal the boss and damage nearby players at the sacrificed minion location.
  - `last_breath`: low-health interruptible channel that releases a large soul blast if players do not deal enough damage.
  - `wither_crown`: orbiting wither skull crown that fires real wither skull projectiles one by one.
- **Vanilla mob visuals for FIW entities** — bosses and custom minions can now look like vanilla/modded
  mobs while still using FIW boss/minion logic. Use `renderEntity` on `baseEntity: "custom"` minions,
  or set a boss `baseEntity` / `renderEntity` to a registry ID such as `minecraft:zombie`.
- **Vanilla mobs as simple minions** — minions can still use true vanilla/modded entity types
  (e.g. `baseEntity: "minecraft:zombie"`) for simple summons with custom stats, equipment, and loot.
  These keep native mob AI and are intentionally less customizable than FIW custom minions.
- Optional **Fiw Tools** integration — reference Fiw Tools items by `toolId` in boss/phase/minion
  equipment and loot. Reflection-only, no hard dependency; skipped if Fiw Tools is absent.

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

### Known issues
- Because this is a rewrite, some abilities/mechanics may be broken, untested, or inconsistent between
  loaders — bugs are expected, and fixes will follow in small updates.
- In-game testing is incomplete: configuration loading is verified on every target, but the abilities,
  persistence, vanilla-mob behavior, and Fiw Tools paths are not yet fully play-tested across all eight targets.
- The shared renderer is a "safe humanoid" model — slim arms and exact player outer layers are not yet matched.
- Player skins require an internet connection at server start (Mojang API lookup).
