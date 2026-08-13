# 消息与多语言

语言文件位于 `plugins/FakeModBlocker/messages_<language>.yml`。

插件**自带两份，首次启动时自动释放**：

| 文件 | `language:` 取值 | 内容 |
|------|------------------|------|
| `messages_en.yml` | `en`（默认） | English |
| `messages_cn.yml` | `cn` | 简体中文 |

`config.yml` 里：

```yaml
language: cn   # → messages_cn.yml
```

`zh`、`zh_CN`、`zh-cn`、`chinese` 都会被识别为 `cn`。

想加别的语言，复制其中一份改名为 `messages_<后缀>.yml`，再把 `language` 指过去即可。

::: tip 改动不会被覆盖
插件不会覆盖已存在的语言文件。你删掉的条目、或新版本新增的条目，都会自动回退到插件内置的同名文件，
所以升级后不会出现 `Missing message: ...`。
:::

## 常用段落

```yaml
prefix: "&7[&cModBlocker&7] "

kick:
  reason-default: "&c&lWarning: ..."
  mods:
    fabric: "&cFabric mod detected! ..."
    forge: "&cForge detected! ..."
    "servux:tweaks": "&c..."   # 含特殊字符的 key 请加引号

command:
  reload-success: "&aModBlocker configuration and messages reloaded successfully."
  usage: "&eUsage: /modblocker reload or /modblocker check <player>"
```

- `kick.mods` 下的 key 应对应 `forbiddenList` 里的关键词
- 1.16+ 可用 Hex：`&#00ffcc` 或 `&x&0&0&f&f&c&c` 等形式（以插件实际解析为准）
- 改完执行 `/modblocker reload`

## 占位符

消息与踢出命令中常见：

| 占位符 | 含义 |
|--------|------|
| `%player%` | 玩家名 |
| `%kickMessage%` | 踢出原因文本 |
| `%channels%` | 频道列表（日志） |
| `%mods%` | 匹配到的关键词（日志） |
| `%mod%` | 单个模组相关信息（日志） |
