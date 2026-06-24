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
- **More boss abilities** — an expanded set of configurable abilities (42 in total).
- **Vanilla mobs as minions** — a minion can use any entity type (e.g. `minecraft:zombie`) with custom
  stats, equipment, loot, and AI, in addition to the custom humanoid minion.
- Optional **Fiw Tools** integration — reference Fiw Tools items by `toolId` in boss/phase/minion
  equipment and loot. Reflection-only, no hard dependency; skipped if Fiw Tools is absent.

### Changed
- **Reworked several boss abilities** and **reworked minion/boss behavior** (AI, targeting, movement modes,
  vanilla-mob handling).
- **Same JSON config format on every loader and version** — and still backward-compatible with configs
  written for **1.0.9 and earlier** (including the old item NBT format), so existing bosses keep working.
- Large parts of the mod rewritten for performance and long-term maintainability; shared logic centralized
  in a Minecraft-free `core` engine plus per-version `common` source sets.
- `core` is bundled into every loader jar; Gson + SLF4J are compile-only (provided by Minecraft).

### Fixed
- `/boss reload` correctly requires op level 3 (admins) again, instead of inheriting the level-2 gate
  of the root `/boss` command.
- Client skin textures are now released when a boss/minion unloads (Fabric `ENTITY_UNLOAD`,
  NeoForge/Forge `EntityLeaveLevelEvent`), fixing a GPU texture/resource leak.

### Known issues
- Because this is a rewrite, some abilities/mechanics may be broken, untested, or inconsistent between
  loaders — bugs are expected, and fixes will follow in small updates.
- In-game testing is incomplete: configuration loading is verified on every target, but the abilities,
  persistence, vanilla-mob behavior, and Fiw Tools paths are not yet fully play-tested across all six targets.
- The shared renderer is a "safe humanoid" model — slim arms and exact player outer layers are not yet matched.
- Player skins require an internet connection at server start (Mojang API lookup).
