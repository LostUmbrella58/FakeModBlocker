# 消息与多语言

语言文件位于 `plugins/FakeModBlocker/messages_<language>.yml`。

`config.yml` 里：

```yaml
language: en   # → messages_en.yml
```

可复制一份改成 `messages_cn.yml`，再把 `language` 设为 `cn`。

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
