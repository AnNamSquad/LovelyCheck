# Lunar Client configuration

The `[lunar]` section in `lovelycheck.toml` controls Apollo-based Lunar Client detection and how detected mods are used. It is copied to `plugins/lovelycheck/lovelycheck.toml` on first run.

The configuration is loaded on startup and on `/lovelycheck reload`.

When enabled, the plugin listens for Lunar Client's `lunar:apollo` plugin message, parses the handshake payload, and stores reported mods with `id`, `name`, `version`, and `type`.

Those results can mark generic checks, run configured actions, and appear in `/lovelycheck check <player>`.
