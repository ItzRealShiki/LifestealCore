# Native WorldGuard and KOTH

This branch focuses only on native WorldGuard and its KOTH integration. WorldEdit is intentionally out of scope.

## Region selection and management

- Select corners with `/lwg pos1` and `/lwg pos2`.
- Create regions with `/lwg create <id>`.
- Regions support owners, members, priorities, parent regions, and inherited boolean flags.
- SQL is authoritative; legacy `regions.yml` data is migrated and a YAML backup is maintained.

## Native flags

- `build`, `interact`, `pvp`, `explosions`, `mob-spawning`
- `KOTH_PVP_ARENA`, `KOTH_CAPTURE`
- `COMBAT_FORCEFIELD`
- `CRYSTAL`: allows obsidian placement in the region and removes that obsidian after 3 minutes. Pending expiry timestamps are persisted in `worldguard-crystal-expiry.yml`.
- `ARENA_RESET`: captures a compressed baseline block-data snapshot and restores the region on a repeating timer. The interval defaults to 5 minutes and can be changed with `worldguard.arena-reset-minutes`. The maximum snapshot size defaults to 100,000 blocks and can be changed with `worldguard.arena-reset-max-blocks`.

Arena snapshots are stored under `worldguard/arena-resets` and survive restarts. They currently store block data only; tile-entity data such as chest inventories, sign text, and other block-entity NBT is not captured. Capture and restore are synchronous, so large regions must be profiled carefully.

## KOTH

KOTH uses the native `KOTH_CAPTURE` and `KOTH_PVP_ARENA` flags. Full lifecycle testing, multiple-arena edge cases, and reset integration require live-server verification.

## Remaining work

- Complete protection event coverage for containers, vehicles, entities, fluids, redstone, and special interactions.
- Complete third-party adapter enforcement and plugin-grouped flag management UI.
- Add audit coverage for every critical region/flag mutation.
- Profile and, if needed, make arena snapshot capture/restore incremental.
- Complete Maven build and live Paper server tests before merging.
