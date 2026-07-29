# Sign Translation Detection

Optional enhancement for mods that **do not register plugin channels**, but expose unique translation keys in client language files.

## How it works (short version)

1. On join, the plugin opens a **virtual sign** to the player
2. Sign lines use `Component.translatable(<key>)`
3. If the client **translates** the key to something other than the raw key string, that client has the mod owning the key → hit

## Version requirements

- Needs Paper **1.21.5+** `Player#openVirtualSign` (**not** available on 1.21.4 or earlier)
- Spigot and some Paper forks (Purpur / Pufferfish / Leaves, etc.) may lack the API depending on upstream tracking
- If the API is missing, the module **auto-disables** at startup and logs which class/method failed; **main channel detection is unaffected**

## Config example

```yaml
extra-detections:
  sign-translation:
    enabled: true
    skip-bedrock-via-floodgate: true   # skip Bedrock when floodgate is present
    mods:
      Freecam:
        detect:
          key: "key.freecam.toggle"
        punishment:
          action: "KICK"               # NOTICE | KICK | BAN | IGNORE
          reason: "&c&lWarning: Please do not use &b&lFreecam &c&lon this server."

      AccurateBlockPlacement:
        detect:
          key: "key.category.accurateblockplacement.category"
        punishment:
          action: "KICK"
          reason: "&c..."

      # Multiple keys: any one translated → hit
      # Xaero:
      #   detect:
      #     keys:
      #       - "key.xaero_open_map"
      #       - "key.xaero_zoom_in"
      #   punishment:
      #     action: "KICK"
      #     reason: "&cXaero's Map detected."
```

### detect

| Field | Description |
|-------|-------------|
| `key` | A single translation key |
| `keys` | Multiple keys; any hit counts (useful across versions / keybind names) |

### punishment.action

| Action | Meaning |
|--------|---------|
| `NOTICE` | Notify (no kick) |
| `KICK` | Kick |
| `BAN` | Ban (per plugin implementation) |
| `IGNORE` | Ignore / log-only use |

## Finding translation keys

1. Unpack the mod jar and open `assets/.../lang/*.json`
2. Pick a key that is **unique to that mod** (often `key.*` keybinds)
3. Put it in `detect.key` / `detect.keys`, reload, and test

::: tip
`/modblocker check <player>` also triggers a sign check when supported — watch the console for results.
:::
