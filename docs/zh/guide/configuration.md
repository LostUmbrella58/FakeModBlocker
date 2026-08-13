# 配置文件

主配置：`plugins/FakeModBlocker/config.yml`

## 基础选项

```yaml
language: en          # 语言文件后缀，对应 messages_en.yml
enable: true          # 总开关
logger: true          # 控制台打印玩家频道 / 模组相关消息（便于收集关键词）
useCustomKickCommand: true
command: "kick %player% %kickMessage%"   # 占位符：%player% %kickMessage%
notifyStaff: true
notificationPermission: fakemodblocker.notify
```

| 选项 | 说明 |
|------|------|
| `language` | 加载 `messages_<language>.yml` |
| `enable` | `false` 时关闭模组检测 |
| `logger` | 开启后控制台会刷频道列表，适合排查，生产环境可按需关闭 |
| `useCustomKickCommand` | 是否用自定义踢出命令代替默认踢人 |
| `command` | 自定义踢出命令模板 |
| `notifyStaff` | 是否通知有权限的员工 |

## forbiddenList

这是**插件频道关键词**，不是模组显示名。

```yaml
forbiddenList:
  - "fabric"
  - "forge"
  - "xaeroworldmap"
  - "servux:tweaks"      # 含 ':' 时只精确匹配该频道
  - "servux:structures"
```

匹配规则：

- **不含 `:`**：频道等于关键词，或以 `关键词:` 开头（前缀匹配）
  - 例：`xaeroworldmap` 可匹配 `xaeroworldmap:main`
- **含 `:`**：仅精确匹配该完整频道名
  - 例：`servux:tweaks` 不会误伤 `servux:litematics`

::: danger 常见误区
把 `meteorclient`、`sodium` 这种**显示名**直接写进列表通常**无效**——除非该模组真的注册了同名频道。

正确做法：

1. 打开 `logger: true`
2. 带着模组进服，看控制台打印的频道
3. 把真实频道关键词写进 `forbiddenList`
4. 若模组根本不注册频道，改用 [告示牌翻译检测](./sign-detection)
:::

## extra-detections

```yaml
extra-detections:
  packet-events:
    enabled: true          # 需安装 PacketEvents
  sign-translation:
    enabled: true
    skip-bedrock-via-floodgate: true
    mods:
      Freecam:
        detect:
          key: "key.freecam.toggle"
        punishment:
          action: "KICK"   # NOTICE | KICK | BAN | IGNORE
          reason: "&c..."
```

## escalation

可选的"警告 → 踢出 → 封禁"阶梯处罚系统，**默认关闭**。

```yaml
escalation:
  enabled: false
  per-mod: true
  count-once-per-session: true
  reset-after: "30d"
  ladder:
    - violations: 1
      action: KICK      # WARN | KICK | BAN | COMMAND | IGNORE
      message: "&c&l警告 &7(第 %count% 次) &f请卸载 %mod%，下次将被封禁。"
    - violations: 2
      action: BAN
      duration: ""      # 留空为永久
```

计数按玩家保存在 `violations.yml`，用 `/modblocker violations <玩家>` 和
`/modblocker clear <玩家> [模组]` 管理。

完整说明：[阶梯处罚](./escalation)

详见：[频道检测](./channel-detection)、[告示牌翻译检测](./sign-detection)
