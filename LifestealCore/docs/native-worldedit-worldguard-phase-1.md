# Native WorldEdit / WorldGuard Rework

## Accepted architecture

- WorldGuard compatibility: `custom` — LifestealCore-owned commands and region semantics.
- Third-party integrations: dedicated adapters and a public Bukkit Services API.
- WorldEdit history: persistent undo and redo across restarts.
- Storage: the existing shared SQL layer, configured for MySQL on production servers. The repository's default SQLite setting is retained to avoid silently switching an existing installation and making its old data appear missing.

## Implemented in this integration pass

### Native region protection

- LifestealCore no longer has a hard dependency on the WorldEdit or WorldGuard plugins.
- Region data is stored in SQL tables; the legacy `regions.yml` is imported once, with a migration marker written transactionally.
- The YAML file remains a recoverable backup/export. Corrupt YAML is not treated as an empty successful migration.
- Regions support owners, members, boolean native flags, priorities, and parent references.
- Effective flag lookup uses the highest-priority applicable region, then the smaller region as a tie-breaker; unset flags inherit from the parent chain.
- Custom commands include `/lwg create`, `delete`, `addmember`, `removemember`, `flag`, `priority`, and `parent`.
- KOTH capture and arena checks now query LifestealCore's native region manager.
- Combat forcefield visuals use the native `COMBAT_FORCEFIELD` region flag.

### Persistent WorldEdit history

- Edit snapshots are compressed files, with metadata and SHA-256 checksums stored in SQL.
- Undo and redo work after restarts.
- A new edit invalidates the previous redo branch.
- Snapshot paths are constrained to the managed history directory and checksums are verified before restore.
- World edits are processed in bounded tick batches. History currently records block data strings; inventories and block-entity NBT are not yet included.

### Third-party extension API

- Typed flag definitions support state, boolean, integer, decimal, string, enum, and string-list values.
- A runtime registry is published through Bukkit's ServicesManager.
- Adapters are namespaced and duplicate plugin IDs / flag keys are rejected.
- An installed plugin is not automatically treated as integrated; it needs a working adapter.

## Remaining work before calling the rework feature-complete

This pass establishes and connects the native foundation, but it is not a complete WorldEdit / WorldGuard replacement yet:

- WorldEdit clipboard, copy/cut/paste, rotation/flip, schematic import/export, operation preview, and richer edit operations are still to be implemented.
- WorldGuard needs broader event coverage (containers, vehicles, entity interaction, fluids, redstone, and additional damage/explosion cases), plus a full typed flag editor and plugin-grouped integration GUI.
- Third-party adapters still need concrete enforcement hooks and adapter implementations for specific plugins.
- Region inheritance is implemented for flags; parent-cycle checks are enforced when setting parents. More complete region query and overlap tooling remains.
- Build and live-server regression tests must pass before merging this branch.

## Language and visual style

New user-facing command messages and GUI labels are English. They use the existing `Msg` formatting pipeline so the configured Small Caps style is applied consistently.
