# 简介

FakeModBlocker 是一个**轻量、可配置**的 Minecraft 服务端插件，用于检测部分客户端模组 / 模组加载器。

它主要靠玩家加入时暴露的 **Plugin Message Channel（插件消息频道）** 来判断；在支持的服务端上，还可以开启可选的 **告示牌翻译键检测**。

::: warning 重要说明
FakeModBlocker **不是**完整反作弊，也**不能**检测所有外挂或模组。

许多纯客户端模组（如部分 HUD、移动类外挂）**不会**注册任何插件频道，频道列表抓不到它们。请把它当作「模组存在性检测」，而不是万能拦截方案。
:::

## 功能一览

- 轻量，无硬依赖
- 通过 `messages_xx.yml` 自定义消息与行为
- 1.16+ 支持 Hex 颜色（如 `&#00ffcc`）
- 主检测兼容约 **1.8–1.21.11+**
- 多语言（`en` / `cn`，也可自建）
- `/modblocker reload` 热重载
- 支持 Spigot / Paper / Purpur / Folia / PandaSpigot
- 可选：告示牌翻译检测、PacketEvents 增强

## 能检测什么

| 类型 | 示例 |
|------|------|
| 模组加载器频道 | Forge (`fml:hs`)、Fabric (`fabric:registry/sync`) |
| 地图 / 实用模组频道 | Xaero、VoxelMap、Replay、Freecam 等（取决于是否注册频道） |
| 自定义关键词 | 你在 `forbiddenList` 里添加的任意频道关键词 |
| 翻译键（可选） | 配置了 `extra-detections.sign-translation` 的模组 |

## 两种检测模式

1. **插件频道检测（主路径）** — 兼容面最广，几乎所有支持版本都能用。
2. **告示牌翻译检测（可选）** — 依赖服务端公开 API（Paper `openVirtualSign`，约 1.21.5+）；不支持时会自动跳过，不影响主插件。

下一步：[安装](./installation)
