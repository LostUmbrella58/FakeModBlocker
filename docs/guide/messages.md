# Messages & Locales

Language files live at `plugins/FakeModBlocker/messages_<language>.yml`.

Two are **bundled and written out on first start**:

| File | `language:` value | Content |
|------|-------------------|---------|
| `messages_en.yml` | `en` (default) | English |
| `messages_cn.yml` | `cn` | 简体中文 |

In `config.yml`:

```yaml
language: cn   # → messages_cn.yml
```

`zh`, `zh_CN`, `zh-cn` and `chinese` are accepted as aliases of `cn`.

To add another language, copy either file to `messages_<suffix>.yml` and point `language` at it.

::: tip Your edits survive updates
The plugin never overwrites a language file that already exists. Any key you delete — or that a
newer version adds — falls back to the copy bundled inside the jar, so upgrading never leaves
`Missing message: ...` on screen.
:::

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
