# Commands & Permissions

## Commands

Main command: `/modblocker`

| Usage | Description |
|-------|-------------|
| `/modblocker reload` | Reload config and language files |
| `/modblocker check <player>` | Inspect an online player's plugin channels against `forbiddenList`; also triggers sign detection when available |
| `/modblocker violations <player>` | Show stored violation counters ([Escalation](./escalation)); works for offline players |
| `/modblocker clear <player> [mod]` | Clear that player's counters — all of them, or just one mod |
| `/modblocker discord test` | Send a test message through [DiscordSRV](./discord) |

Required permission: `fakemodblocker.admin` (default: op)

## Permissions

| Permission | Default | Description |
|------------|---------|-------------|
| `fakemodblocker.admin` | op | Admin commands (reload / check / violations / clear / discord) |
| `fakemodblocker.bypass` | op | Fully bypass detection (notice and kick) |
| `fakemodblocker.kickbypass` | op | Won't be kicked; channel logs may still print |
| `fakemodblocker.notify` | op | Staff notifications (`notifyStaff` / `notificationPermission`) |

::: tip
Give staff or spectator accounts `fakemodblocker.bypass` so they are not kicked by mistake. For debugging, use `kickbypass`: you still see channel info in console without kicks.
:::
