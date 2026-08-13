# 安装

## 要求

- Java 与你的服务端版本匹配
- Spigot / Paper / Purpur / Folia / PandaSpigot 等 Bukkit API 服务端
- 主功能：约 1.8–1.21.11+
- 告示牌翻译检测：需要 Paper **1.21.5+**（或提供同等 API 的分支）

## Soft 依赖（可选）

| 插件 | 作用 |
|------|------|
| PlaceholderAPI | 消息占位符扩展（如已集成） |
| LuckPerms | 权限管理 |
| floodgate | 告示牌检测可跳过基岩版玩家 |
| [PacketEvents](https://www.spigotmc.org/resources/packetevents-api.80279/) | 更早、更可靠地拦截插件频道注册包 |

未安装这些插件时，对应功能会安静跳过或回退到默认行为。

## 步骤

1. 从 [Releases](https://github.com/LostUmbrella58/FakeModBlocker/releases) 下载 `.jar`
2. 放入服务器的 `plugins/` 目录
3. 启动（或重载）服务器，生成配置文件：
   - `plugins/FakeModBlocker/config.yml`
   - `plugins/FakeModBlocker/messages_en.yml` 与 `messages_cn.yml`（两份都会自动生成，用 `language:` 选择）
4. 按需编辑配置
5. 执行 `/modblocker reload` 立即生效

## 快速验证

1. 用 OP 账号进入服务器
2. 执行 `/modblocker check <玩家>` 查看该玩家已注册的插件频道
3. 若开启了 `logger: true`，控制台会打印玩家频道列表，方便你补充 `forbiddenList`

下一步：[配置文件](./configuration)
