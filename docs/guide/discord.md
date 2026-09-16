# Discord Notifications

Optional. Detections that today only reach the console and online staff can also be posted to a
Discord channel, routed through **[DiscordSRV](https://modrinth.com/plugin/discordsrv)**.

**Off by default, and requires DiscordSRV.** Nothing is bundled: the plugin jar still contains
only its own classes.

## How "optional" works here

`DiscordSRVBridge` is the only class in the plugin that names a DiscordSRV or JDA type, and it
is reached exclusively through `Class.forName` — the same way the [PacketEvents](./channel-detection)
and [sign detection](./sign-detection) bridges work. On a server without DiscordSRV that class is
never loaded, so none of those types are ever resolved and nothing can fail.

The bridge is only constructed when **both** are true:

1. `discord.enabled` is `true`
2. DiscordSRV is installed

If you enable it without DiscordSRV, console says so once and the feature stays off.

## Enable it

```yaml
discord:
  enabled: true
  channel: ""
```

Then `/modblocker reload` and `/modblocker discord test`.

`channel` accepts three spellings:

| Value | Meaning |
|-------|---------|
| `""` | DiscordSRV's main channel |
| `"global"` | A DiscordSRV **game-channel name** — the mapping stays in DiscordSRV's own config |
| `"123456789012345678"` | A raw Discord **channel ID** |

::: tip Upgrading from an older version?
Your existing `config.yml` is never overwritten, so it has no `discord:` section yet. A missing
section simply means "off" — nothing breaks. Paste the block from this page into your
`config.yml` and reload. Language files are different: they fall back to the copy inside the jar,
so the `discord:` message templates work without touching `messages_*.yml`.
:::

## What gets posted

| Event | Fires when |
|-------|-----------|
| `channel-detection` | A `forbiddenList` plugin channel matched ([Channel Detection](./channel-detection)) |
| `sign-detection` | A translation key came back translated ([Sign Detection](./sign-detection)) |
| `escalation` | A ladder rung was applied ([Escalation](./escalation)) |
| `evade` | The sign check could never be completed |

```yaml
discord:
  events:
    channel-detection: true
    sign-detection: true
    escalation: true
    evade: false
```

### One detection is one message

When the escalation ladder takes a detection over, it posts the `escalation` message and the
per-detection message is skipped. You never get two posts for the same join.

Detections whose action is `IGNORE` are not posted, since `IGNORE` usually means "I do not care
about this one". Set `include-ignored: true` if you want them as telemetry anyway.

## Appearance

```yaml
discord:
  embed: true                  # false = one plain line instead of an embed
  mention: "<@&123456789012345678>"
  server-name: ""              # shown as %server%; empty = the server's own name
  colors:
    notice: "#E67E22"
    kick: "#E74C3C"
    ban: "#992D22"
    evade: "#F1C40F"
    test: "#2ECC71"
```

`mention` is prepended to every message. Use `<@&ROLE_ID>` for a role or `<@USER_ID>` for a
person — turn on Discord's developer mode and right-click to copy the ID.

::: tip
With `mention` empty, nothing can ping anyone: an `@everyone` that ended up inside a mod name or
a kick reason is sent with mentions disabled, so it renders as plain text.
:::

## Message text

The templates live in your [language file](./messages) under `discord:`, so they follow
`language:` automatically:

```yaml
discord:
  channel-detection: "**%player%** joined with a blocked plugin channel: `%mods%`"
  sign-detection: "**%player%** was detected using **%mod%** through the sign check (action: %action%)"
  escalation: "**%player%** reached violation %count% for **%mod%** -> step %step%/%steps%: %action%"
  evade: "**%player%** never completed the mod check (%reason%)"
  title-detection: "Mod detected"
  field-player: "Player"
```

Placeholders: `%player%` `%uuid%` `%server%` `%mod%` `%mods%` `%count%` `%step%` `%steps%`
`%reason%` `%source%` `%action%`

Discord markdown works (`**bold**`, `` `code` ``, `*italic*`). Minecraft colour codes are
stripped automatically, so a reason like `&c&lFreecam detected` arrives as `Freecam detected`.

Embed fields with no value for a given event are skipped rather than shown empty.

## Reliability

There is no queue, no thread and no retry logic in this plugin: DiscordSRV's JDA owns the request
queue, the rate limiting and the retries. Every call here returns immediately, so a slow or
unreachable Discord can never delay a player join, a packet handler or a region thread.

If DiscordSRV has not finished connecting when a detection fires, the message is skipped and
console says so once. `/modblocker discord test` reports that state directly:

| Reply | Meaning |
|-------|---------|
| Test message sent | It worked |
| DiscordSRV is still connecting | JDA has not finished its handshake yet — retry shortly |
| Could not resolve the channel | `discord.channel` points at nothing DiscordSRV can find |
| DiscordSRV is not installed | The bridge was never loaded |

## Why not a raw webhook?

A webhook would not need DiscordSRV, but it would mean this plugin owning an HTTP client, a send
queue, a worker thread and its own 429/5xx retry handling — or shading OkHttp and kotlin-stdlib,
which would take the jar from ~100KB to several MB and break the "no dependencies" promise.
Routing through DiscordSRV keeps all of that out of the jar.
