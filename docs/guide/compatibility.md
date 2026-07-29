# Compatibility

## Main channel detection

- Target range: roughly **1.8 – 1.21.11+**
- Spigot / Paper / Purpur / Folia / PandaSpigot, etc.
- Folia is handled via a compatible scheduler adapter

## Sign translation detection

| Environment | Expectation |
|-------------|-------------|
| Paper 1.21.5+ | Available (`openVirtualSign`) |
| Paper ≤ 1.21.4 | Unavailable, skipped automatically |
| Some Paper forks | Depends on whether they track upstream API |
| Spigot | Usually no API, skipped automatically |

Missing API does **not** break the plugin — only this module is disabled.

## Soft dependencies

| Plugin | Effect |
|--------|--------|
| PacketEvents | Earlier interception of channel registration when enabled |
| floodgate | `skip-bedrock-via-floodgate` can skip Bedrock for sign detection |
| LuckPerms / PlaceholderAPI | Permissions / placeholders ecosystem as needed |
