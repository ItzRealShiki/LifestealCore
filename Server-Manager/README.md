# Server Manager

A clean, English Minecraft-themed Windows desktop control center.

## Current prototype
- Tauri 2 + React + TypeScript desktop shell
- Dark stone / forest-green theme with Unicode small-caps headings
- Dashboard with an honest disconnected state (no fabricated live metrics)
- Local to-do list persisted in local browser storage
- Navigation placeholders for plugin library, projects, developer center and settings

## Requirements
- Windows 10/11
- Node.js 20+
- Rust stable toolchain
- Microsoft C++ Build Tools (Desktop development with C++)

## Run in development
```powershell
npm install
npm run tauri dev
```

## Build installer
```powershell
npm run tauri build
```

Installer output: `src-tauri/target/release/bundle/`.

## Planned integration
1. Authenticated LifestealCore API for live metrics, player list and diagnostics.
2. GitHub Releases catalogue for plugin JARs, checksums, changelogs and update detection.
3. Safe plugin installation with backups and explicit server-stop confirmation.
4. SQLite persistence for tasks and projects.

Never commit server passwords, API keys or signing credentials.