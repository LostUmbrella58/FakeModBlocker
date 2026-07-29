# Commands & Permissions

## Commands

Main command: `/modblocker`

| Usage | Description |
|-------|-------------|
| `/modblocker reload` | Reload config and language files |
| `/modblocker check <player>` | Inspect an online player's plugin channels against `forbiddenList`; also triggers sign detection when available |

Required permission: `fakemodblocker.admin` (default: op)

## Permissions

| Permission | Default | Description |
|------------|---------|-------------|
| `fakemodblocker.admin` | op | Admin commands (reload / check) |
| `fakemodblocker.bypass` | op | Fully bypass detection (notice and kick) |
| `fakemodblocker.kickbypass` | op | Won't be kicked; channel logs may still print |
| `fakemodblocker.notify` | op | Staff notifications (`notifyStaff` / `notificationPermission`) |

::: tip
Give staff or spectator accounts `fakemodblocker.bypass` so they are not kicked by mistake. For debugging, use `kickbypass`: you still see channel info in console without kicks.
:::
