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

## 不要抢玩家的屏幕

打开检测告示牌在客户端就是 `setScreen()`，会把玩家眼前的界面直接顶掉。如果那正好是
**资源包确认框**，玩家就再也没机会做选择，也就静默地拿不到资源包；别家插件的 dialog、菜单同理。

所以检测会先等屏幕空出来：

```yaml
extra-detections:
  sign-translation:
    avoid-open-screens: true      # 有界面占屏时等待，而不是顶掉它
    max-attempts: 4               # 客户端一直不回传时，最多开几轮告示牌
    evade-timeout-seconds: 300    # 挂着界面超过这么久上报一次
    evade-action: NOTICE          # NOTICE | KICK
```

**这个等待故意不设上限。** 设了上限就等于告诉玩家「挂着任意 GUI 撑够时间，这局就免检」——
玩家总得关掉界面才能玩，关掉的那一秒检测就接上。若超过 `evade-timeout-seconds` 仍开不了，
只上报一次，然后继续等。

同理，一轮里客户端一个回传都没给的，会退避重开（10s / 30s / 60s / 120s）而不是当作干净放过，
免得客户端只要拖过响应超时就能免检。只要客户端回了任意一页，这一轮就算数。

`evade-action` 默认是 `NOTICE`：卡住的资源包确认框、慢客户端、网络抖动都会落到这里，
为这些踢人的代价比这套象征性检测本身的收益还大。

### 装 PacketEvents 会更准（可选）

Bukkit 只能看到**容器菜单**。装了 [PacketEvents](https://modrinth.com/plugin/packetevents) 之后，
dialog、资源包确认框、成书也能看见，并且多出一个信号：

> 客户端只在朝向真的变了时才发「转头」移动包，而**任何**界面打开都会释放鼠标、把朝向冻死。
> 所以看到玩家在转视角，就等于客户端在告诉我们它屏幕是空的。

这正是**按 ESC 关掉 dialog** 能被察觉的原因 —— 客户端在这条路上什么都不发，没有这个信号就只能
干等兜底计时器过期。它也不会造成反向误判：玩家盯着资源包确认框时，物理上发不出转头包。

PacketEvents 始终是**完全可选**的。没装的话检测只会避开容器菜单，是盲区变大，而不是功能失效。

## 如何找翻译键

1. 解压模组 jar，查看 `assets/.../lang/*.json`
2. 挑一个**该模组独有**、不易与原版冲突的 key（常见是 `key.*` 键位类）
3. 写入 `detect.key` / `detect.keys` 后重载测试

::: tip
`/modblocker check <玩家>` 在支持时也会触发一次告示牌检测，结果看控制台。
:::
