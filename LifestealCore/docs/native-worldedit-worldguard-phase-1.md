# Native WorldEdit / WorldGuard Rework — Phase 1

## Accepted architecture decisions

- WorldGuard command compatibility: `custom` (LifestealCore-owned commands and semantics).
- Third-party integrations: both dedicated adapters and a public integration API.
- WorldEdit undo: persistent across server restarts.
- Storage target: MySQL. The existing shared database layer is reused; the plugin's current SQLite default is intentionally not silently changed because doing so could make existing server data appear missing.

## Phase 1 foundation

The database schema now reserves independent tables for:

- Native regions, members, and typed flags.
- Persistent WorldEdit history metadata and operation records.
- Schematic metadata.
- Append-oriented module audit events.
- Idempotent module migration markers.

Large edit snapshots and schematic payloads are represented by managed file paths and SHA-256 checksums. They are not stored as large SQL blobs.

A public third-party flag contract is introduced. Adapters must expose only flags they can actually enforce. Merely detecting that another plugin is installed is not sufficient to claim integration support.

## Compatibility and migration safety

The legacy WorldGuard region module and `regions.yml` remain untouched in this phase. The new tables are additive and do not import, delete, or overwrite legacy data. A later migration must copy regions, owners, members, and flags, verify counts and contents, and only then mark the migration complete.

The current runtime still has direct WorldGuard registration paths and hard plugin dependencies. They are intentionally not removed in Phase 1: doing so before replacing every direct API usage would break startup or existing combat/KOTH behavior.

## Next implementation steps

1. Add module lifecycle/bootstrap and MySQL validation without changing the global database automatically.
2. Implement a persistent snapshot repository with atomic file writes, checksum verification, and undo/redo recovery.
3. Implement the native region repository and one-time verified `regions.yml` migration.
4. Move KOTH region lookups to the new region manager.
5. Replace direct WorldEdit/WorldGuard API usage, then remove hard dependencies.
