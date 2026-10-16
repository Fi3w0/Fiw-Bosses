# QA pack (1.3.1)

One test pack for the current version. Update it in place for each release instead of adding
new folders.

## Install

Into a **disposable** test world's config folder:

| From | To |
|------|----|
| `bosses/*.json` | `config/fiw_bosses/bosses/` |
| `minions/*.json` | `config/fiw_bosses/minions/` |
| `skins/qa_skin.png` | `config/fiw_bosses/skins/` |
| `settings.json` | `config/fiw_bosses/settings.json` (back up yours first) |

Then `/boss reload` (op level 3). Leave `negative/` out until step 7. Use a flat world and
press **F3+B** for hitboxes. Every QA boss deals 0-4 damage; most stand still
(`guard_point`).

## 1. Domain

```text
/gamemode survival
/boss spawn qa_domain
```

Stay within 8 blocks (creative/spectator players are not captured).

- [ ] Phase 1 pulses `QA legacy pulse` every 4 s (`cooldown: 80`).
- [ ] After the domain collapses, the next one waits about **15 s** (`params.cooldown: 300`).
- [ ] `/boss phase @e[nbt={BossId:"qa_domain"}] set 2`: `QA alias pulse`, same 4 s cadence (`cooldownTicks: 80`).
- [ ] Phase 3: `QA precedence pulse` keeps 4 s (`cooldown` wins over `cooldownTicks: 10`).
- [ ] Phase 4: `QA default pulse` every 2 s (default 40).
- [ ] Change phase while a domain is active, and `/boss reload` mid-domain: no crash, no stuck domain.
- [ ] Collapse sound is quiet (no dragon roar). Set `domainBreakVolume` to 100, then 0, and `domainBreakSound` to false, with `/boss reload` between: louder / silent / silent.

## 2. Melee

- [ ] In survival, `/boss spawn qa_melee` about 5 blocks away: it closes in while strafing and hits you (4 damage).

## 3. Skins

```text
/boss spawn qa_skin
/boss minion spawn qa_minion_skin
```

- [ ] Both show the turquoise/orange checker skin (`"qa_skin"` and `"qa_skin.png"` both work).

## 4. Size and hitbox (GitHub issue #3)

- [ ] `qa_golem_disguise`: the F3+B box wraps the iron golem (1.4 x 2.7).
- [ ] `qa_golem_giant` (x2), `qa_golem_small` (x0.6), `qa_player_giant` (x3), `qa_player_tiny` (x0.5): model and box match, name tag above the head.
- [ ] Stand still next to `qa_player_giant`: it hits you without walking into you.
- [ ] Stand about 4 blocks from `qa_player_reach`: it hits you; `qa_melee` at that distance does not.
- [ ] `qa_scale_abilities` (x2.5): after teleport, swap and slam it is never inside blocks. In a 2-high tunnel, swap and slam skip the teleport. Shadow clones match its size; zombie minions are x1.5 with a matching box.
- [ ] Under a 3-block ceiling `/boss spawn qa_huge_room` fails with "Not enough room ... about 3.6 x 11.7 blocks". In the open it spawns, lifted up to 3 blocks if needed.
- [ ] `/boss minion spawn qa_vanilla_minion`: a real vanilla chicken (vanilla-base minions keep their own size).
- [ ] Rejoin the world and restart the game: sizes, boxes and reach stay the same.

## 5. Shield and summon

- [ ] `/boss spawn qa_shield`: two pylons; boss immune until both die.
- [ ] Hold paper, right-click a lodestone: `qa_summon` appears, one paper is used. A second summon is refused while it lives. Under a low ceiling the summon is refused with the size message and the paper is kept.
- [ ] `/boss spawn qa_summon variant hard` has 160 HP. `/boss rage @e[nbt={BossId:"qa_summon"}] start qa_rage` and `... end` work. A player kill drops a stick and updates `/boss stats qa_summon`.

## 6. Schedule and settings

```text
/boss schedule trigger qa_schedule
/boss schedule trigger qa_schedule
/boss summary top 2
```

- [ ] First trigger spawns at x=8 z=8; the second is refused while it lives.
- [ ] Restart: the schedule remembers its living boss; summary stays at top 2.

## 7. Invalid config

- [ ] `/boss validate` with this pack installed: "No config problems found."
- [ ] Copy `negative/qa_bad_schedule.json.invalid` to `bosses/qa_bad_schedule.json`, `/boss reload`: reload reports problems; `/boss validate` lists the schedule needing exactly one of `intervalMinutes`/`times`. The other bosses still load. Delete it and reload.
- [ ] Misspell a key in a copy of a QA boss (`"scael": 2`): `/boss validate` shows `scael: unknown key; ignored (did you mean 'scale'?)`.

## Dedicated server

Run `console-smoke.txt` line by line in the server console after `Done`. It builds a small
platform at y=79 and force-loads it, so it works without a player.
