<div align="center">
  <h1>mEvents</h1>
  <p>Reliable scheduled airdrops and contribution-based boss events for modern Minecraft networks.</p>
  <p>
    <a href="https://papermc.io/software/paper"><img alt="Paper" height="56" src="https://cdn.jsdelivr.net/npm/@intergrav/devins-badges@3/assets/cozy/supported/paper_vector.svg"></a>
    <a href="https://purpurmc.org"><img alt="Purpur" height="56" src="https://cdn.jsdelivr.net/npm/@intergrav/devins-badges@3/assets/cozy/supported/purpur_vector.svg"></a>
  </p>
  <p>
    <a href="https://github.com/miklires/mEvents"><img alt="GitHub" src="https://tr7zw.github.io/uikit/social_buttons_icon/Github-Button-64.png"></a>
    <a href="https://modrinth.com/plugin/mevents"><img alt="Modrinth" src="https://tr7zw.github.io/uikit/social_buttons_icon/Modrinth-Button-64.png"></a>
    <a href="https://discord.gg/pes25cnWKy"><img alt="Discord" src="https://tr7zw.github.io/uikit/social_buttons_icon/Discord-Button-64.png"></a>
  </p>
  <p>
    <a href="https://bstats.org/plugin/bukkit/mEvents/27941"><img alt="bStats" src="https://img.shields.io/badge/bStats-27941-2F9BE6?style=for-the-badge"></a>
    <a href="https://github.com/miklires/mEvents/releases"><img alt="Release" src="https://img.shields.io/github/v/release/miklires/mEvents?style=for-the-badge"></a>
    <img alt="Java 25" src="https://img.shields.io/badge/Java-25-5382A1?style=for-the-badge">
  </p>
</div>

## What it does

- Runs protected supply crates with a configurable countdown, claim grace period, weighted rewards, and deterministic cleanup.
- Runs vanilla boss encounters with real-damage contribution tracking, minimum-damage eligibility, and optional top-winner limits.
- Starts templates manually or on independent intervals with bounded random jitter and per-template cooldowns.
- Journals every reward before dispatching commands; unresolved deliveries remain pending for startup retry.
- Persists lifecycle and audit history in asynchronous embedded H2 storage.
- Reconciles interrupted runs and removes orphaned event crates after restart.
- Uses global, region, and entity schedulers correctly on both Paper and Folia.
- Exposes a public `MEventsApi` through Bukkit's Services Manager.

## Requirements

- Java 25
- Paper, Purpur, or Folia 26.2

## Installation

1. Put `mEvents-1.1.0.jar` in the server's `plugins` directory.
2. Start the server once.
3. Edit `plugins/mEvents/events.yml`.
4. Run `/event reload`.

English is used by default. Set `language: ru_RU` in `config.yml` to switch every command, announcement, and notification to Russian. Existing language files automatically receive newly added defaults.

## Event configuration

```yaml
events:
  supply:
    name: "Supply Crate"
    type: AIRDROP
    location: { world: world, x: 0, y: 100, z: 0 }
    preparation-ticks: 100
    duration-ticks: 1200
    cooldown-ticks: 1200
    airdrop:
      grace-ticks: 60
    schedule:
      enabled: false
      initial-delay-ticks: 1200
      interval-ticks: 72000
      jitter-ticks: 6000
    rewards:
      - id: supply_iron
        weight: 70
        commands: ["give {player} iron_ingot 16"]
```

Reward commands support `{player}`, `{uuid}`, `{event}`, and `{run}`. Do not include a leading slash. IDs accept lowercase letters, digits, `_`, and `-`, with a maximum length of 64 characters. Invalid templates are reported and skipped without discarding valid templates.

Automatic intervals must be longer than the event preparation plus duration. `limits.max-concurrent-events` protects against floods from commands, the API, or overlapping schedules.

## Commands

| Command | Permission | Description |
|---|---|---|
| `/event list` | `mevents.use` | List templates and active runs |
| `/event status` | `mevents.use` | Show active runs |
| `/event history` | `mevents.use` | Show ten recent persisted runs |
| `/event info <template>` | `mevents.use` | Inspect a template |
| `/event start <template>` | `mevents.admin` | Start an event |
| `/event stop <run-id>` | `mevents.admin` | Cancel a run |
| `/event reload` | `mevents.reload` | Validate and reload configuration |

## Safety model

- Airdrop blocks cannot be opened, broken, exploded, moved by pistons, or accessed by hoppers before a valid claim.
- Occupied locations are never overwritten.
- Concurrent starts of the same template are serialized.
- Player-controlled placeholders are inserted only into administrator-authored commands.
- H2 no longer opens an `AUTO_SERVER` listener, and its executor receives a bounded shutdown.

## API

Obtain `MEventsApi` from Bukkit's Services Manager. Consumers can list templates, start a run, query an active run, or cancel by stable run UUID. The new `start` method is a default interface method to preserve compatibility with alternative providers.

## Telemetry

mEvents uses anonymous [bStats metrics](https://bstats.org/plugin/bukkit/mEvents/27941). No player names, UUIDs, locations, contributions, or rewards are collected. Disable metrics with `metrics.enabled: false`.

## Build

```bash
./gradlew clean build
```

Licensed under the MIT License.
