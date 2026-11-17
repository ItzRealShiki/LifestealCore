# Native WorldGuard, KOTH and Block History

This branch intentionally excludes WorldEdit. It contains the native WorldGuard/region work, KOTH integration and a CoreProtect-style block history and rollback foundation.

## Native regions

- Select corners with `/lwg pos1` and `/lwg pos2`.
- Create regions with `/lwg create <id>`.
- Regions support owners, members, priorities, parent regions and inherited boolean flags.
- SQL is authoritative; legacy `regions.yml` data is imported and a YAML backup is maintained.
- Region and flag mutations write audit records.

## Native protection flags

- `BUILD`, `INTERACT`, `PVP`, `EXPLOSIONS`, `MOB-SPAWNING`
- `KOTH_PVP_ARENA`, `KOTH_CAPTURE`, `COMBAT_FORCEFIELD`
- `LIQUID-FLOW`, `REDSTONE`, `FIRE-SPREAD`, `ENTITY-INTERACT`, `VEHICLE-USE`, `PISTON`, `HANGING`
- `CRYSTAL`: allows obsidian placement in the region and removes that obsidian after 3 minutes. Expiry timestamps persist across restarts.
- `ARENA_RESET`: captures a compressed baseline and restores the region every 5 minutes by default. Configure with `worldguard.arena-reset-minutes`; max region size defaults to 100,000 blocks via `worldguard.arena-reset-max-blocks`.
- Arena snapshots include block data, container inventories and sign lines. Older block-only snapshots remain readable.

Arena capture and restore currently execute on the server thread. Keep reset regions small and profile them before production use.

## KOTH

KOTH uses native `KOTH_CAPTURE` and `KOTH_PVP_ARENA` flags and is visible in the `/admin` KOTH dashboard. Full event lifecycle and multi-arena testing still require a live server.

## CoreProtect-style block history

The native history module records player block breaks/placements, explosions, container inventory changes, fluids, piston movement, fire and entity-driven block changes. Snapshots include block data, container inventories and sign text.

- Open `/admin` → `Logs` → `CoreProtect`.
- Choose a region, time window and optionally a player.
- The module previews affected blocks before a rollback, then reopens the `/admin` CoreProtect panel.
- Confirm the pending rollback with the in-GUI **Confirm Pending Rollback** button within 60 seconds.
- Rollbacks are applied in batches and the rollback itself is recorded, allowing a later rollback of that administrator's changes.
- Default limit: 20,000 unique blocks per rollback, configurable with `history.max-rollback-blocks`.

This is a native foundation, not full feature parity with CoreProtect. It currently targets registered WorldGuard regions rather than arbitrary radius/coordinate queries. Some Minecraft block-entity types beyond containers and signs are not fully serialized. Test backups and rollback on a staging world before production use.

## Admin integration API

The public `WorldGuardIntegrationApi` allows third-party plugins to register namespaced flag adapters. `/admin` → `Logs` → `WorldGuard Integrations` displays registered adapters grouped by plugin and lists their flags. Actual enforcement still requires each integration plugin to supply a working adapter.

## Verification still required

- Run the latest Java 25 Maven build after the most recent commits.
- Test all protection event combinations on a live Paper server.
- Test Crystal expiry and arena reset across restarts.
- Test container/sign restoration and rollback conflict handling on a staging copy.
- Complete KOTH lifecycle and multi-arena tests.
