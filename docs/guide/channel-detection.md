# Channel Detection

This is FakeModBlocker's **primary** detection path and works on the widest range of versions.

## How it works

Many mods / loaders register **Plugin Message Channels** with the server. On join (or via PacketEvents), the plugin checks the player's listening channels against `forbiddenList` keywords.

Typical examples:

| Keyword / channel | Common source |
|-------------------|---------------|
| `fml:hs` / `forge` | Forge |
| `fabric:registry/sync` / `fabric` | Fabric |
| `xaeroworldmap` | Xaero's World Map |
| `replay` | Replay Mod |
| `servux:tweaks` | Tweakeroo (via Servux) |
| `servux:structures` | MiniHUD / Structures (via Servux) |

## PacketEvents enhancement (optional)

```yaml
extra-detections:
  packet-events:
    enabled: true
```

With [PacketEvents](https://www.spigotmc.org/resources/packetevents-api.80279/) installed, channel registration packets can be intercepted directly:

- Earlier than a delayed post-join scan
- More reliable when mods register channels mid-session
- Without PacketEvents, this option has no effect; the default path still works

## Collecting new keywords

1. Set `logger: true`
2. Join with the target mod
3. Find console lines about listening plugin channels
4. Add the relevant fragment to `forbiddenList` (respect prefix vs exact rules)
5. `/modblocker reload`

You can also use `/modblocker check <player>` in-game.

## Limits

- Only mods that **register plugin channels** can be caught this way
- Most pure client cheats and some HUD / movement mods open **no** channel → list detection fails
- For those cases, use [Sign Translation Detection](./sign-detection), or accept that this plugin cannot detect them
