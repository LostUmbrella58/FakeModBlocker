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

## Not stealing the player's screen

Opening the detection sign is `setScreen()` on the client, so it replaces whatever the player is
currently looking at. If that happens to be a **resource pack confirmation**, the player never gets
to answer it and silently never receives the pack. Dialogs and other plugins' menus are lost the
same way.

So the check waits for the screen to clear first:

```yaml
extra-detections:
  sign-translation:
    avoid-open-screens: true      # wait instead of overwriting whatever is on screen
    max-attempts: 4               # rounds to open the sign when the client never answers
    evade-timeout-seconds: 300    # report once after this long with a screen still open
    evade-action: NOTICE          # NOTICE | KICK
```

**The wait has no deadline, on purpose.** A deadline would mean "hold any GUI open long enough and
this session is never checked". The player has to close it to play, and the check resumes the second
they do. If it still cannot start after `evade-timeout-seconds`, that is reported once and the
waiting continues.

Likewise, a round in which the client answered nothing is retried (backoff 10s / 30s / 60s / 120s)
rather than treated as clean, so a client that simply stalls past the response timeout cannot skip
the check by waiting. A round counts as done as soon as the client answers at least one page.

`evade-action` is `NOTICE` by default: a stuck resource pack prompt, a slow client or a bad
connection all end up here, and kicking for those costs more than this symbolic check is worth.

### PacketEvents makes this sharper (optional)

Bukkit only reports **container menus**. With [PacketEvents](https://modrinth.com/plugin/packetevents)
installed, dialogs, resource pack prompts and books become visible too, and one extra signal kicks in:

> The client only sends a "rotation changed" movement packet when the rotation actually changed, and
> **every** open screen releases the mouse and freezes it. So seeing the player turn their camera is
> the client telling us its screen is empty.

That is what makes a dialog closed with **ESC** detectable — the client sends nothing on that path,
so without this the check would sit idle until the fallback timer expires. It cannot cause the
opposite mistake either: a player staring at a resource pack prompt physically cannot send one.

PacketEvents remains **entirely optional**. Without it the check only avoids container menus, which
is a smaller blind spot rather than a broken feature.

## Finding translation keys

1. Unpack the mod jar and open `assets/.../lang/*.json`
2. Pick a key that is **unique to that mod** (often `key.*` keybinds)
3. Put it in `detect.key` / `detect.keys`, reload, and test

::: tip
`/modblocker check <player>` also triggers a sign check when supported — watch the console for results.
:::
