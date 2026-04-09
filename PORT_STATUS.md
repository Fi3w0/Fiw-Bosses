# FIW Bosses — NeoForge 1.21.1 Port Status

Branch: `neoforge-1.21.1` (Mojmap). Source: Fabric 1.20.1 / Yarn.

## Build

```
./gradlew build
```

Jar: `build/libs/fiw-bosses-1.0.7.jar`

## Testing in-game

1. Drop the jar into a NeoForge 1.21.1 instance's `mods/` folder.
2. Boss JSON files live at `<instance>/config/fiw_bosses/bosses/*.json`. A `test_boss.json` is included in `run/config/fiw_bosses/bosses/` of this repo — copy it there, or it auto-loads when launching `./gradlew runClient`.
3. In-game commands (op level 2):
   - `/fiwboss reload` — re-scan the bosses directory.
   - `/fiwboss spawn <id>` — spawn a boss by JSON id at your position. E.g. `/fiwboss spawn test_boss`.

The included `test_boss` has 3 phases and exercises every ported ability.

## Ported abilities (42)

`melee_slash`, `dodge`, `flames`, `random_message`, `aoe_smash`, `teleport`,
`shield`, `heal`, `charge`, `pull`, `swap`, `summon_minions`,
`ranged_projectile`, `meteor`, `slam`, `shockwave`, `beam`, `chain_lightning`,
`sonic_boom`, `slash_wave`, `freeze`, `arc_slash`, `particle_tornado`,
`orbital`, `fire_arrow`, `ice_crystal`, `ground_spike`, `lightning_radial`,
`arrow_rain`, `crimson_slash`, `orb_throw`, `tracking_orb`,
`detect_mark`, `judgment_mark`, `divine_execution`, `essence_absorption`,
`guardian_shield`, `moving_tornado`, `phantom_dash`, `potion_field`,
`singularity_cannon`, `domain`

All Fabric 1.20.1 ability goals have been ported. Unknown ability types in
JSON still resolve to a no-op goal (no crash).

## Other ported subsystems

- `BossEntity` + attributes registration
- `BossConfigLoader` (server-start load + `/fiwboss reload`)
- `BossDefinition` schema (health, armor, speed, kb resist, attack damage,
  bossbar, skin, equipment, phases, minions, loot)
- Phase transitions with damage/speed multipliers and transition messages
- `ModSounds` registry
