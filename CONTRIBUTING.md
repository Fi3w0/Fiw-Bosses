# Contributing to FIW Bosses

## Getting Started

1. **Fork** the repo and clone it.
2. Open the project in your IDE (IntelliJ / Eclipse / VS Code with Java extensions).
3. Run `./gradlew build` to download dependencies and compile all targets.

## Build

The mod targets 6 Minecraft versions across 3 loaders (12 loader modules plus `core`). Run Gradle with JDK 25;
toolchains compile each module for its own Java version (17, 21 or 25):

```
./gradlew build
```

This compiles `core`, all `common-*` source sets, and all loader modules.
For a single target:

```
./gradlew :fabric-1.21.11:build
```

## Development Workflow

- New abilities are developed on **`common-26.3`** first, then ported to
  `common-26.1.2`, `common-1.21.11`, `common-1.21.8`, `common-1.21.1`, and `common-1.20.1`.
- Shared logic lives in `core/` and is Minecraft-free.
- Each loader module pulls its `common-*` source set via Gradle `srcDir`.

## Code Conventions

- 4-space indentation, UTF-8, LF line endings (see `.editorconfig`).
- Follow the patterns in existing goal classes for new abilities.
- Keep per-version API adaptations minimal — change only what differs between
  Minecraft versions.
- No co-author trailers on commits.

## Testing

- Run `./gradlew :core:test` for core tests. They also validate every config under
  `examples/`, so a new example must load without warnings.
- A new ability type goes in its `BossGoalFactory` on every version and in core
  `AbilityTypes`; a test checks they match.
- Gameplay check on a real server with the built jar (Node 22+, Java 17, 21 and 25 installed):
  `bash .github/scripts/verify-target.sh fabric-1.21.8`. It installs the QA pack, spawns every
  QA boss, forces phases and rages, kills a boss with damage and checks its loot and affixes,
  then restarts the server and checks again. A client cannot join a FIW Bosses test server
  without the mod, so everything is driven from the server console. CI runs it for every target
  before publishing.
- For in-game testing, build and copy the jar to your Minecraft instance's
  `mods/` folder and use the checklist in `examples/qa/README.md`.

## Pull Requests

- Use the PR template (it will pre-fill when you open a PR).
- Ensure `./gradlew build` passes before pushing.
- Reference any related issues in your PR description.
