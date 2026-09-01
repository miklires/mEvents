# Changelog

## 1.1.0 - 2026-09-01

### Added

- Full English and Russian language files with English defaults.
- Automatic template schedules with initial delay, interval, and bounded jitter.
- Per-template cooldowns, airdrop grace periods, boss minimum damage, and winner limits.
- `/event info`, `/event history`, permission-aware completion, and public API start support.
- Configurable global concurrent-event limit and focused per-template validation reports.

### Fixed

- Prevented commands and API starts from racing database initialization.
- Journaled airdrop and all boss rewards before completing their run.
- Made reward journaling idempotent per player/run and scoped pending reward lookup by template.
- Prevented duplicate concurrent starts, occupied-block replacement, piston/inventory bypasses, and overkill damage inflation.
- Added restart cleanup for orphaned crates and removed H2 `AUTO_SERVER` network mode.

## 1.0.0 - 2026-08-26

- Added supply-drop and vanilla boss event templates.
- Added atomic lifecycle transitions and persistent H2 audit data.
- Added durable command-reward journal and startup reconciliation.
- Added Paper, Purpur and Folia scheduling support.
- Added commands, protections, API and automated tests.
