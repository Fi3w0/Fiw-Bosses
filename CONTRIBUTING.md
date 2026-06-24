# Contributing to FIW Bosses

## Getting Started

1. **Fork** the repo and clone it.
2. Open the project in your IDE (IntelliJ / Eclipse / VS Code with Java extensions).
3. Run `./gradlew build` to download dependencies and compile all targets.

## Build

The mod targets 4 Minecraft versions across 3 loaders (8 modules total):

```
./gradlew build
```

This compiles `core`, all `common-*` source sets, and all loader modules.
For a single target:

```
./gradlew :fabric-1.21.11:build
```

## Development Workflow

- New abilities are developed on **`common-1.21.11`** first, then ported to
  `common-1.21.8`, `common-1.21.1`, and `common-1.20.1`.
- Shared logic lives in `core/` and is Minecraft-free.
- Each loader module pulls its `common-*` source set via Gradle `srcDir`.

## Code Conventions

- 4-space indentation, UTF-8, LF line endings (see `.editorconfig`).
- Follow the patterns in existing goal classes for new abilities.
- Keep per-version API adaptations minimal — change only what differs between
  Minecraft versions.
- No AI co-author trailers on commits. Author must be a human.

## Testing

- Run `./gradlew :core:test` for core config parsing and text utility tests.
- For in-game testing, build and copy the jar to your Minecraft instance's
  `mods/` folder, or use the IDE's run configuration.

## Pull Requests

- Use the PR template (it will pre-fill when you open a PR).
- Ensure `./gradlew build` passes before pushing.
- Reference any related issues in your PR description.
