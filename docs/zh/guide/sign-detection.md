# 告示牌翻译检测

可选增强模块。用于抓那些**不注册插件频道**、但客户端语言文件里有独特翻译键的模组。

## 原理（简版）

1. 玩家加入时，插件打开一张**虚拟告示牌**
2. 告示牌行内容使用 `Component.translatable(<key>)`
3. 若客户端把该 key **翻译成别的文字**（而不是原样返回 key），说明客户端装了拥有该 key 的模组 → 判定命中

## 版本要求

- 需要 Paper **1.21.5+** 的 `Player#openVirtualSign`（**1.21.4 及更早没有该 API**）
- Spigot 或部分 Paper 分支（Purpur / Pufferfish / Leaves 等）可能尚未同步该 API
- API 不可用时，插件会在启动时**自动禁用**本模块，并在控制台说明缺了哪个类 / 方法；**主频道检测不受影响**

## 配置示例

```yaml
extra-detections:
  sign-translation:
    enabled: true
    skip-bedrock-via-floodgate: true   # 有 floodgate 时跳过基岩版
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

      # 多 key：任一被翻译即判定
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

| 字段 | 说明 |
|------|------|
| `key` | 单个翻译键 |
| `keys` | 多个翻译键，任一命中即判定（适合版本差异或多种键位名） |

### punishment.action

| 动作 | 含义 |
|------|------|
| `NOTICE` | 通知（不踢） |
| `KICK` | 踢出 |
| `BAN` | 封禁（按插件实现） |
| `IGNORE` | 忽略 / 仅记录用途 |

## 如何找翻译键

1. 解压模组 jar，查看 `assets/.../lang/*.json`
2. 挑一个**该模组独有**、不易与原版冲突的 key（常见是 `key.*` 键位类）
3. 写入 `detect.key` / `detect.keys` 后重载测试

::: tip
`/modblocker check <玩家>` 在支持时也会触发一次告示牌检测，结果看控制台。
:::
