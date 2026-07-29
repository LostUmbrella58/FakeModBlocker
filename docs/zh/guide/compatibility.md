# 兼容性

## 主频道检测

- 设计目标：约 **1.8 – 1.21.11+**
- Spigot / Paper / Purpur / Folia / PandaSpigot 等
- Folia 通过兼容调度器处理

## 告示牌翻译检测

| 环境 | 预期 |
|------|------|
| Paper 1.21.5+ | 可用（有 `openVirtualSign`） |
| Paper ≤ 1.21.4 | 不可用，自动跳过 |
| 部分 Paper 分支 | 取决于是否跟进上游 API |
| Spigot | 通常无该 API，自动跳过 |

不可用时**不会**拖垮插件，仅禁用该模块。

## Soft 依赖

| 插件 | 影响 |
|------|------|
| PacketEvents | 开启后可更早拦截频道注册 |
| floodgate | `skip-bedrock-via-floodgate` 可跳过基岩玩家的告示牌检测 |
| LuckPerms / PlaceholderAPI | 权限与占位符生态，按需安装 |
