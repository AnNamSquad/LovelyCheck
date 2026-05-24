# lovelycheck

lovelycheck is a Spigot/Paper/Purpur plugin for client and mod detection.

## Features

- Connection-time Forge, NeoForge, Fabric, Lunar Client, generic payload, and Bedrock detection.
- Sign translation checks for configured clients/mods.
- Manual checks, join checks, anticheat-triggered checks, alert toggles, Discord webhooks, and SQLite scan history.

## Commands

- `/lovelychecker scan` - run sign translation hack/mod checks.
- `/lovelychecker reload` - reload all lovelycheck configuration.
- `/lovelychecker alerts` - toggle sign-check alerts.
- `/lovelychecker check`, `/lovelychecker list`, `/lovelychecker inv` - connection-detection review commands.

## Configuration

New installs write two editable files under `plugins/lovelycheck/`:

- `config.yml` - sign checks, messages, Discord, alerts, and punishments.
- `lovelycheck.toml` - connection-time payload, Lunar, Forge, Bedrock, and probing detection.

## Build

```bash
./gradlew clean build
```

The shaded jar is written to `build/libs/lovelycheck.jar`.
