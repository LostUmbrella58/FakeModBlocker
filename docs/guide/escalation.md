# Escalation (Warn → Kick → Ban)

Optional **strike system**. Instead of applying the same punishment every time, the plugin
counts violations per player and climbs a ladder:

> 1st join with Freecam → warned and kicked
> 2nd join with Freecam → permanently banned

It is **off by default**. While `escalation.enabled` is `false`, every detection applies its
own fixed punishment exactly as before.

## Enable it

```yaml
escalation:
  enabled: true
  per-mod: true
  count-once-per-session: true
  reset-after: "30d"

  ladder:
    - violations: 1
      action: KICK
      message: |-
        &c&lWarning &7(strike %count%)
        &fYou joined with &c%mod%&f, which is not allowed on this server.
        &7Remove it before rejoining — joining again with it will get you &c&lpermanently banned&7.
    - violations: 2
      action: BAN
      duration: ""      # empty / 0 / "permanent" = permanent
      message: |-
        &4&lBanned
        &fYou joined with &c%mod%&f again after being warned.
        &7Strike &f%count%&7. Duration: &f%duration%
```

Then `/modblocker reload`.

## What it takes over

| Detection | Escalated? |
|-----------|-----------|
| `forbiddenList` plugin-channel hits | ✅ always |
| Sign translation mod with `action: KICK` / `BAN` | ✅ unless it sets `punishment.escalation: false` |
| Sign translation mod with `action: NOTICE` / `IGNORE` | ❌ never |

`NOTICE` and `IGNORE` stay observational on purpose: turning escalation on must never promote
a mod you only wanted to watch into a ban.

To keep one specific mod on a fixed punishment:

```yaml
      Freecam:
        detect:
          key: "key.freecam.toggle"
        punishment:
          action: "KICK"
          reason: "&cFreecam is not allowed."
          escalation: false     # always kick, never ban
```

## Options

| Option | Default | Description |
|--------|---------|-------------|
| `enabled` | `false` | Master switch for the whole system |
| `per-mod` | `true` | `true`: a separate counter per mod. `false`: one shared counter per player |
| `count-once-per-session` | `true` | At most one strike per mod per session |
| `reset-after` | `"30d"` | Forget a counter after this idle time. `0` / `never` = keep forever |
| `use-custom-ban-command` | `false` | Hand `BAN` to another plugin instead of the vanilla ban list |
| `ban-command` | `ban %player% %reason%` | Used when the step is a permanent ban |
| `temp-ban-command` | `tempban %player% %duration% %reason%` | Used when the step has a `duration` |
| `ladder` | 2 steps | The rungs, see below |

::: warning count-once-per-session
Keep this `true`. A single join is often seen by several detectors at once (PacketEvents
channel packet, the join scan, the sign check). Without it, one join could jump several rungs.
:::

## The ladder

Each rung is `violations` (the strike count it starts at) plus an action. The **last rung keeps
applying** to every further violation, so a ladder ending in a permanent ban never runs out.

| Action | Effect |
|--------|--------|
| `WARN` | Send the message, player stays online |
| `KICK` | Message becomes the kick reason (honours `useCustomKickCommand`) |
| `BAN` | Ban for `duration` (empty = permanent), then kick |
| `COMMAND` | Run `command:` from console |
| `IGNORE` | Only count and log |

Durations accept `30d`, `12h`, `90m`, `45s`, `2w` and compounds like `1d12h`.

A softer four-rung example:

```yaml
  ladder:
    - violations: 1
      action: WARN
      message: "&e&lWARNING &r&f%mod% is not allowed here. Remove it — next time you will be kicked."
    - violations: 2
      action: KICK
    - violations: 3
      action: BAN
      duration: "7d"
    - violations: 4
      action: BAN
      duration: ""
```

Handing bans to LiteBans / AdvancedBan / CMI:

```yaml
  use-custom-ban-command: true
  ban-command: "banoffline %player% %reason%"
  temp-ban-command: "tempban %player% %duration% %reason%"
```

When `use-custom-ban-command` is on, FakeModBlocker only runs the command — the other plugin is
responsible for removing the player.

## Placeholders

Usable in every ladder `message`, in the `escalation.*` entries of `messages_<lang>.yml`, and in
`command:` / ban command templates.

| Placeholder | Meaning |
|-------------|---------|
| `%player%` | Player name |
| `%mod%` | Detected mod / channel keyword (comma separated when several matched) |
| `%count%` | This player's violation number for that counter |
| `%step%` / `%steps%` | Current rung / total rungs |
| `%next%` | Violation count of the following rung, `-` on the last one |
| `%duration%` | Ban duration, or `permanent` |
| `%reason%` | The mod's own `reason` from `sign-translation`, if any |
| `%source%` | `plugin channel` or `sign translation` |
| `%action%` | `warn` / `kick` / `ban` / `command` / `ignore` |

Leave a rung's `message` out to fall back to `escalation.warn` / `escalation.kick` /
`escalation.ban` from your language file.

## Managing strikes

```
/modblocker violations <player>     # show stored counters (works offline)
/modblocker clear <player>          # clear all counters for that player
/modblocker clear <player> Freecam  # clear one counter
```

Counters live in `plugins/FakeModBlocker/violations.yml`, are written asynchronously, and survive
restarts. Expired counters are pruned when the file is loaded.

```yaml
records:
  - uuid: 069a79f4-44e9-4726-a5be-fca90e38aaf5
    name: Notch
    counters:
      - key: Freecam
        count: 1
        last: 1755000000000
```

Only edit that file while the server is stopped — use the commands otherwise.

## Notes

- Players with `fakemodblocker.bypass` or `fakemodblocker.kickbypass` never receive channel
  strikes, same as they never got kicked before.
- Staff with `fakemodblocker.notify` get a line per escalated detection (respects `notifyStaff`).
- If the ladder is empty or unparseable, the plugin logs a warning and falls back to the fixed
  per-detection punishment — a broken ladder never means "cheaters go free".
