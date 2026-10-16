# Scale / disguise hitbox test pack (GitHub issue #3)

Copy `bosses/*.json` into `config/fiw_bosses/bosses/` and `minions/*.json` into
`config/fiw_bosses/minions/`, then `/boss reload`. Use a flat creative world; press **F3+B**
to show hitboxes. Every boss here does 1-2 damage.

| Boss | What it tests |
|------|---------------|
| `qa_golem_disguise` | The issue's case: `renderEntity: minecraft:iron_golem`, no scale. Hitbox = iron golem (1.4 x 2.7). |
| `qa_golem_giant` | Iron golem disguise at `scale: 2.0`. Model and hitbox 2.8 x 5.4. |
| `qa_golem_small` | Iron golem disguise at `scale: 0.6`. |
| `qa_player_giant` | Player model at `scale: 3.0`. Melee reach grows with size on its own. |
| `qa_player_tiny` | Player model at `scale: 0.5`. |
| `qa_player_reach` | Normal size with `reach: 3.0`: hits from about 3 blocks further away than `qa_melee`. |
| `qa_scale_abilities` | `scale: 2.5` with teleport, swap, slam, shadow clones and scaled zombie minions. |
| `qa_huge_room` | `scale: 6.0` (3.6 x 11.7). Refuses to spawn under a low ceiling. |

## Checklist

- [ ] `/boss spawn qa_golem_disguise`: the F3+B box wraps the golem model; arrows and sword hits land on the whole body.
- [ ] `qa_golem_giant`, `qa_golem_small`, `qa_player_giant`, `qa_player_tiny`: model and box match; name tag sits above the head.
- [ ] Stand still next to `qa_player_giant`: it reaches you without walking into you.
- [ ] Stand about 4 blocks from `qa_player_reach`: it hits you; `qa_melee` at the same distance does not.
- [ ] `qa_scale_abilities`: never ends up inside blocks after teleport/swap/slam; in a 2-block tunnel swap and slam-teleport are skipped instead of burying it. Shadow clones are the same size as the boss. The zombie minions are 1.5x with a matching box.
- [ ] Under a 3-block ceiling `/boss spawn qa_huge_room` fails with "Not enough room ... about 3.6 x 11.7 blocks"; in the open it spawns, lifted up to 3 blocks if needed.
- [ ] Leave and rejoin the world, and restart the game: sizes, boxes and reach stay the same.
- [ ] Disconnect from a server with the bosses loaded and reconnect: the client shows the same sizes (synced, not only server-side).
- [ ] Set `"scale": 100` on a copy: it is clamped to 16. `"reach": -2` means no extra reach.
