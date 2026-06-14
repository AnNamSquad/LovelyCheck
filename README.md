# LovelyCheck

LovelyCheck is a Paper/Purpur server plugin for detecting suspicious Minecraft Java clients and client-side mods. It combines connection-time evidence with active sign translation probes based on MC-265322.

The active probe is server-side only. No client mod is required.

## How It Works

Minecraft clients resolve translation and keybind components before sending edited sign text back to the server. LovelyCheck uses that behavior to probe configured translation keys and keybind keys:

1. A hidden sign probe is opened for the target player.
2. The sign contains configured translation or keybind keys.
3. Vanilla clients normally return the fallback text or raw key.
4. Clients that recognize a configured key may return translated text, which is treated as a detection.
5. Confirmed detections can be logged, alerted, sent to Discord, or punished.

LovelyCheck supports three check modes:

- `TRANSLATE` - detects custom translation keys.
- `KEYBIND` - detects custom keybind keys.
- `METEOR` - legacy Meteor-style behavior for custom configs.

## Main Features

- 25 built-in sign fingerprints in `config.yml`.
- Double confirmation for direct detections.
- Batched sign probing with up to 4 checks per sign.
- PacketEvents virtual sign packet flow.
- No physical sign placement or real-sign fallback.
- Translation shield handling with `PROTECTED` results.
- Translation masking evidence from connection-time data.
- Manual scans, join scans, and anticheat-triggered scans.
- GrimAC, Vulcan, and Spartan flag hooks.
- Discord detection and locale reports.
- Locale probe command.
- Bedrock/Geyser/Floodgate skip support.
- SQLite scan and punishment history.
- Configurable commands for detected, protected, and clean results.
- Permission bypass support.

## Built-In Fingerprints

The default configuration includes:

- Meteor Client
- LiquidBounce
- Vape
- Freecam
- Wurst
- XRay Fabric
- ChestESP
- KillAura Fabric
- AutoFish
- AutoTotem
- AutoTotem Meteor
- Lumina
- AutoSwitch
- BleachHack
- Aristois
- Coffee Client
- World Downloader
- Item Scroller
- Xaero's Minimap
- JourneyMap
- AutoClicker Fabric
- AntiAFK
- Auto Clicker p1k0chu
- Inventory Profiles Next
- Tweakeroo

You can add more fingerprints by adding entries under `hacks:` in `config.yml`.

Example:

```yaml
hacks:
  example-client:
    display-name: "Example Client"
    key: "key.example.open_gui"
    mode: KEYBIND
```

Then include the ID in `default-check-hacks`, `auto-check-on-join.hacks`, or `detect-flag.hacks`.

## Commands

Primary command:

```text
/lovelychecker <subcommand>
```

Alias:

```text
/lc <subcommand>
```

Available subcommands:

```text
/lc scan <player> [hack1,hack2,...]
/lc reload
/lc alerts
/lc lang <player>
/lc check <player>
/lc list
/lc inv
```

## Permissions

```text
lovelycheck.check              Run sign scans
lovelycheck.reload             Reload configuration
lovelycheck.alerts             Receive/toggle scan alerts
lovelycheck.checklang          Run locale probes
lovelycheck.command.check      Review connection/sign detections
lovelycheck.command.list       List detected players
lovelycheck.command.inv        Open the inventory view
lovelycheck.bypass             Bypass LovelyCheck checks
lovelycheck.*                  All LovelyCheck permissions
```

`lovelychecker.*` aliases are also supported.

## Configuration Files

New installs create:

```text
plugins/lovelycheck/config.yml
plugins/lovelycheck/lovelycheck.toml
```

`config.yml` controls:

- Sign fingerprints.
- Join checks.
- Anticheat-triggered checks.
- Discord webhooks.
- Alert messages.
- Punishment commands.
- Bedrock skip behavior.
- Translation masking behavior.

`lovelycheck.toml` controls:

- Connection-time Forge/Fabric/Lunar/generic payload checks.
- Bedrock provider integration.
- Connection detection actions.

## Detection Results

LovelyCheck stores and reports these result types:

```text
DETECTED       The configured key resolved like an installed client/mod.
NOT_DETECTED   The response looked vanilla-safe for that fingerprint.
PROTECTED      The probe was blocked, timed out, or was masked by matching connection evidence.
SKIPPED        The check did not run.
```

Direct `DETECTED` results are double-confirmed when `double-confirmation.enabled` is true.

`PROTECTED` means anti-fingerprinting behavior was observed. It does not prove which exact client or mod caused it.

## Fabric Loader Notice

The connection message:

```text
<player> just logged in using Fabric !
```

means the client announced Fabric-related brand or plugin-channel data. It is an informational loader detection, not proof of a cheat mod. A normal Fabric Loader install with no gameplay mods can still produce this message.

If a player then shows:

```text
Detected 0 | Protected 25 | Clean 0 | Skipped 0
```

the sign probe did not receive a usable response before timeout, so all configured fingerprints were marked `PROTECTED`. This does not mean the player has 25 mods installed.

For normal servers, keep the first-probe timeout conservative:

```yaml
shield-detection:
  timeout-ticks: 20
  buffer-ticks: 20
```

If your server is slow, has high ping players, or PacketEvents is loading mappings on first join, increase these values.

## Frequently Asked Questions

### Does LovelyCheck detect Mio or Thunder?

Not by default. Mio and Thunder are not included in the bundled fingerprints. LovelyCheck can detect them only if you know reliable translation keys or keybind keys for those clients and add them to `config.yml`.

Do not promise Mio or Thunder detection unless you have tested the exact client version and keys you plan to use.

### Does LovelyCheck detect LiquidBounce?

LiquidBounce is included in the default config:

```yaml
liquidbounce:
  display-name: "LiquidBounce"
  key: "liquidbounce.module.killaura.name"
  mode: TRANSLATE
```

This can detect clients that expose that translation key normally. If a LiquidBounce build or addon spoofs, blocks, or masks translation responses, LovelyCheck may report `PROTECTED` or `Translation Masking Bypass` instead of a clean LiquidBounce name.

### Does LovelyCheck detect OpSec or translation-shield mods?

LovelyCheck detects the behavior, not always the exact mod name.

If a client blocks the sign response entirely, the scan is marked `PROTECTED`. If a client makes mod probes look vanilla while connection-time evidence still matches the same mod, LovelyCheck can mark it as `Translation Masking Bypass`.

By default, translation masking is not punishable:

```yaml
detect-translation-masking:
  punishable: false
```

This is intentional. Shield evidence is useful for alerts and review, but it should be punished only after you are comfortable with your server's false-positive rate.

### Can water or lava near the player cause false alarms?

Water near the player should not cause a direct hack detection.

The current engine does not place a sign block in the world. It uses PacketEvents to send a fake client-side sign block, sign NBT, open-sign-editor packet, and close-window packet. If the packet probe cannot open or the client does not answer, LovelyCheck marks the probe as `PROTECTED` instead of falling back to a real sign.

If the client does not answer the probe, LovelyCheck can mark the scan as `PROTECTED`, but water or lava should not turn a vanilla response into a specific mod detection.

### Suggested buyer-facing answer

Use this wording when answering questions:

```text
Mio and Thunder are not guaranteed out of the box. LovelyCheck can detect them only if reliable translation or keybind keys are known and added to the config.

LiquidBounce has a bundled fingerprint, but spoofed or protected builds may show as PROTECTED or Translation Masking Bypass instead of a named LiquidBounce detection.

OpSec and similar translation-shield mods are handled as anti-fingerprinting behavior. The plugin can flag blocked probes as PROTECTED, but it cannot always prove the exact shield mod.

Water near a player should not create a direct false hack detection. The probe uses PacketEvents virtual sign packets only. A blocked or missing response may be marked PROTECTED, not as a specific client.
```

## Requirements

- Java 21
- Paper, Purpur, or compatible Bukkit server for Minecraft 1.21.x
- PacketEvents required for virtual sign packet probing

Optional integrations:

- Geyser/Floodgate for Bedrock detection
- GrimAC
- Vulcan
- Spartan
- PlaceholderAPI

## Build

```bash
GRADLE_USER_HOME=/tmp/simppay-gradle ./gradlew clean build
```

The shaded jar is written to:

```text
build/libs/lovelycheck-1.0.0.jar
```

## References

- MC-265322: https://mojira.dev/MC-265322
- Sign Translation Vulnerability overview: https://wurst.wiki/sign_translation_vulnerability
- OpSec project page: https://www.curseforge.com/minecraft/mc-mods/opsec
- LiquidBounce project page: https://liquidbounce.net/
