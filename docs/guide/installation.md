# Installation

## Requirements

- **Java 21 or newer.** FakeModBlocker is compiled using Java 21, so an older JVM refuses
  to load it with `UnsupportedClassVersionError`.
- A Bukkit API server: Spigot / Paper / Purpur / Folia / PandaSpigot, etc.
- Main feature: roughly 1.8–1.21.11+
- Sign translation detection: Paper **1.21.5+** (or a fork with the same API)

## Soft dependencies (optional)

| Plugin | Role |
|--------|------|
| PlaceholderAPI | Message placeholder expansion (when integrated) |
| LuckPerms | Permission management |
| floodgate | Skip Bedrock players for sign detection |
| [PacketEvents](https://www.spigotmc.org/resources/packetevents-api.80279/) | Intercept plugin-channel registration packets earlier and more reliably |
| [DiscordSRV](https://modrinth.com/plugin/discordsrv) | Post detections to a Discord channel (see [Discord Notifications](./discord)) |

If these are missing, related features are skipped or fall back quietly.

## Steps

1. Download the `.jar` from [Releases](https://github.com/LostUmbrella58/FakeModBlocker/releases)
2. Place it in `plugins/`
3. Start (or reload) the server to generate:
   - `plugins/FakeModBlocker/config.yml`
   - `plugins/FakeModBlocker/messages_en.yml` and `messages_cn.yml` (both are generated; pick one with `language:`)
4. Edit the config as needed
5. Run `/modblocker reload` to apply changes

## Quick check

1. Join with an OP account
2. Run `/modblocker check <player>` to inspect registered plugin channels
3. With `logger: true`, the console prints channel lists so you can extend `forbiddenList`

Next: [Configuration](./configuration)
