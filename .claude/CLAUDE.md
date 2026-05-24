# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## Project Overview

lovelycheck is a Minecraft plugin that detects clients and mods by analyzing custom payload packets and running sign translation probes. It supports Spigot, Paper, and Purpur servers.

## Build Commands

```bash
./gradlew clean build
```

The shaded output jar is `build/libs/lovelycheck.jar`.

## Architecture

lovelycheck uses two Gradle modules:
- **lovelycheck-core**: Shared logic, configuration, and detection algorithms.
- **lovelycheck-spigot**: Bukkit/Paper/Purpur implementation using ProtocolLib or PacketEvents.

Only the Bukkit-family platform module is present in this project.

## Detection System

The plugin detects clients through:
1. **Generic checks**: Pattern matching on custom payload channels/messages (`generic.toml`).
2. **Forge/NeoForge/Fabric mod lists**: FML and related handshake detection.
3. **Lunar Client Apollo**: Protobuf-based Lunar Client mod detection.
4. **Sign translation probes**: Configured checks in `checks.yml`.

## Configuration Files

Core resources live in `lovelycheck-core/src/main/resources/`:
- `config.toml`: Main settings.
- `actions.toml`: Action definitions.
- `generic.toml`: Generic payload-based checks.
- `forge.toml`: Forge/NeoForge/Fabric detection rules.
- `lunar.toml`: Lunar Client Apollo settings.
- `languages/`: Localization files.

Spigot resources live in `lovelycheck-spigot/src/main/resources/`:
- `plugin.yml`: Bukkit plugin descriptor.
- `config.yml`: Sign-check settings.
- `checks.yml`: Sign translation checks.

## Dependencies

Key dependencies managed via `build.gradle.kts`:
- **apollo-protos** and **protobuf**: Lunar Client protobuf messages, shaded and relocated.
- **adventure**: Kyori Adventure text components.
- **tomlj**: TOML configuration parsing.
- **hopper**: Runtime dependency loader for ProtocolLib or PacketEvents.
- **Purpur API**: Compile-only compatibility check for Purpur.

All shaded dependencies are relocated to `org.lovelycheck.shaded.*` to avoid conflicts.

## Coding Conventions

- **Language**: Java 21 bytecode via Gradle `--release 21`.
- **Text Components**: Use Adventure MiniMessage for formatted text.
- **Placeholders**: Use `Placeholder.unparsed()` for raw values, `Placeholder.parsed()` only for MiniMessage content.
- Keep changes focused and avoid over-engineering.
