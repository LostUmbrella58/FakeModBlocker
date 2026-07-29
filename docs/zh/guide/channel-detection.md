# 频道检测

这是 FakeModBlocker 的**主检测路径**，兼容版本最广。

## 原理

许多模组 / 加载器会向服务端注册 **Plugin Message Channel**。玩家加入时，插件检查其监听的频道列表，并与 `forbiddenList` 中的关键词比对。

典型例子：

| 关键词 / 频道 | 常见来源 |
|---------------|----------|
| `fml:hs` / `forge` | Forge |
| `fabric:registry/sync` / `fabric` | Fabric |
| `xaeroworldmap` | Xaero's World Map |
| `replay` | Replay Mod |
| `servux:tweaks` | Tweakeroo（经 Servux） |
| `servux:structures` | MiniHUD / Structures（经 Servux） |

## PacketEvents 增强（可选）

```yaml
extra-detections:
  packet-events:
    enabled: true
```

安装 [PacketEvents](https://www.spigotmc.org/resources/packetevents-api.80279/) 后可直接拦截频道注册包：

- 比「进服后再延迟检查」更早
- 对「会话中途才注册频道」的情况更可靠
- 未安装 PacketEvents 时该项无效，插件仍走默认路径

## 收集新关键词

1. `logger: true`
2. 用目标模组进服
3. 在控制台找到类似「listening on plugin channels」的日志
4. 把相关片段加入 `forbiddenList`（注意前缀 / 精确匹配规则）
5. `/modblocker reload`

也可用 `/modblocker check <玩家>` 在游戏内查看该玩家当前频道。

## 限制

- **只**能抓到会注册插件频道的模组
- 多数纯客户端外挂、部分 HUD / 移动模组**不会**开频道 → 列表检测无效
- 这类情况请改用 [告示牌翻译检测](./sign-detection)，或接受无法用本插件检测
