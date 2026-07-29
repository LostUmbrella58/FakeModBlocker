# Messages & Locales

Language files live at `plugins/FakeModBlocker/messages_<language>.yml`.

In `config.yml`:

```yaml
language: en   # → messages_en.yml
```

Copy to `messages_cn.yml` and set `language: cn` for Chinese, or create any other suffix you need.

## Common sections

```yaml
prefix: "&7[&cModBlocker&7] "

kick:
  reason-default: "&c&lWarning: ..."
  mods:
    fabric: "&cFabric mod detected! ..."
    forge: "&cForge detected! ..."
    "servux:tweaks": "&c..."   # quote keys that contain special characters

command:
  reload-success: "&aModBlocker configuration and messages reloaded successfully."
  usage: "&eUsage: /modblocker reload or /modblocker check <player>"
```

- Keys under `kick.mods` should match keywords in `forbiddenList`
- Hex colors on 1.16+: `&#00ffcc` (or equivalent forms the plugin parses)
- After edits, run `/modblocker reload`

## Placeholders

Common placeholders in messages and kick commands:

| Placeholder | Meaning |
|-------------|---------|
| `%player%` | Player name |
| `%kickMessage%` | Kick reason text |
| `%channels%` | Channel list (logs) |
| `%mods%` | Matched keywords (logs) |
| `%mod%` | Single mod-related info (logs) |
