# Configuration

Main config: `plugins/FakeModBlocker/config.yml`

## Basics

```yaml
language: en          # locale file suffix → messages_en.yml
enable: true          # master switch
logger: true          # print player channels / mod-related messages (great for collecting keywords)
useCustomKickCommand: true
command: "kick %player% %kickMessage%"   # placeholders: %player% %kickMessage%
notifyStaff: true
notificationPermission: fakemodblocker.notify
```

| Option | Description |
|--------|-------------|
| `language` | Loads `messages_<language>.yml` |
| `enable` | `false` disables mod detection |
| `logger` | Verbose console channel lists; useful for debugging, optional in production |
| `useCustomKickCommand` | Use a custom kick command instead of the default kick |
| `command` | Custom kick command template |
| `notifyStaff` | Notify staff with the notify permission |

## forbiddenList

These are **plugin channel keywords**, not mod display names.

```yaml
forbiddenList:
  - "fabric"
  - "forge"
  - "xaeroworldmap"
  - "servux:tweaks"      # with ':' → exact channel match only
  - "servux:structures"
```

Matching rules:

- **No `:`**: channel equals the keyword, or starts with `keyword:`
  - Example: `xaeroworldmap` matches `xaeroworldmap:main`
- **With `:`**: exact channel name only
  - Example: `servux:tweaks` will not match `servux:litematics`

::: danger Common mistake
Putting display names like `meteorclient` or `sodium` in the list usually does **nothing** — unless that mod actually registers a matching channel.

Do this instead:

1. Enable `logger: true`
2. Join with the mod and copy the channel from console (if any)
3. Add the real channel keyword to `forbiddenList`
4. If the mod never opens a channel, use [Sign Translation Detection](./sign-detection)
:::

## extra-detections

```yaml
extra-detections:
  packet-events:
    enabled: true          # requires PacketEvents
  sign-translation:
    enabled: true
    skip-bedrock-via-floodgate: true
    mods:
      Freecam:
        detect:
          key: "key.freecam.toggle"
        punishment:
          action: "KICK"   # NOTICE | KICK | BAN | IGNORE
          reason: "&c..."
```

See also: [Channel Detection](./channel-detection), [Sign Translation Detection](./sign-detection)
