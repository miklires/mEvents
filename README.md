<div align="center">

# mEvents

Reliable airdrop and boss events for modern Minecraft servers.

[![Paper](https://img.shields.io/badge/Available_for-Paper-222c31?style=for-the-badge&logo=paperlessngx&logoColor=white)](https://papermc.io/software/paper)
[![Purpur](https://img.shields.io/badge/Available_for-Purpur-5f2167?style=for-the-badge)](https://purpurmc.org/)
[![Folia](https://img.shields.io/badge/Available_for-Folia-69c535?style=for-the-badge)](https://papermc.io/software/folia)

[![Build](https://img.shields.io/github/actions/workflow/status/miklires/mEvents/build.yml?style=flat-square&label=build)](https://github.com/miklires/mEvents/actions)
![Release](https://img.shields.io/badge/release-v1.0.0-0ea5e9?style=flat-square)
![Java](https://img.shields.io/badge/Java-25-5382a1?style=flat-square)
![Minecraft](https://img.shields.io/badge/Minecraft-26.2-62b47a?style=flat-square)

</div>

mEvents runs protected supply drops and contribution-based boss encounters. Its state machine and reward journal prevent duplicate rewards after retries or restarts, while region-aware scheduling keeps the plugin compatible with Folia.

## Features

- Supply drops with countdowns, exclusive claims and break/explosion/hopper protection.
- Configurable vanilla boss events with per-player damage accounting.
- Weighted command rewards with durable pending/delivered transactions.
- H2 persistence, startup reconciliation and audit records.
- Public API through Bukkit's Services Manager.
- Native Paper, Purpur and Folia scheduling.

## Commands

| Command | Permission | Description |
|---|---|---|
| `/event list` | `mevents.use` | List templates and active events |
| `/event status` | `mevents.use` | Show active event state |
| `/event start <template>` | `mevents.admin` | Start an event |
| `/event stop <run-id>` | `mevents.admin` | Stop an event |
| `/event reload` | `mevents.reload` | Reload `events.yml` |

## Installation

1. Install Java 25 and Paper, Purpur or Folia 26.2.
2. Put `mEvents-1.0.0.jar` in the server's `plugins` directory.
3. Start the server and edit `plugins/mEvents/events.yml`.
4. Restart or run `/event reload`.

## Building

```text
./gradlew clean build
```

The release jar is generated in `build/libs`.

## License

Released under the MIT License.
