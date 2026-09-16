# Discord 通知

可选功能。原本只会进控制台和在线管理聊天框的检测结果，可以同时发到 Discord 频道，
经由 **[DiscordSRV](https://modrinth.com/plugin/discordsrv)** 转发。

**默认关闭，且需要已安装 DiscordSRV。** 本插件不打包任何东西：jar 里仍然只有自己的 class。

## 这里的"可选"是怎么做到的

`DiscordSRVBridge` 是本插件里唯一提到 DiscordSRV / JDA 类型的 class，
而它只能通过 `Class.forName` 被加载——跟 [PacketEvents](./channel-detection) 和
[告示牌检测](./sign-detection) 那两个 bridge 是同一套做法。
没装 DiscordSRV 的服上，这个 class 根本不会被加载，那些类型自然也永远不会被解析，不可能出错。

只有**同时满足**这两个条件时，bridge 才会被创建：

1. `discord.enabled` 为 `true`
2. 服务器装了 DiscordSRV

如果开了却没装 DiscordSRV，控制台会提示一次，功能保持关闭。

## 启用

```yaml
discord:
  enabled: true
  channel: ""
```

然后 `/modblocker reload`，再 `/modblocker discord test`。

`channel` 支持三种写法：

| 值 | 含义 |
|----|------|
| `""` | DiscordSRV 的主频道 |
| `"global"` | DiscordSRV 的**游戏频道名**——映射关系仍由 DiscordSRV 自己的配置决定 |
| `"123456789012345678"` | 直接填 Discord **频道 ID** |

::: tip 从旧版本升级上来？
已有的 `config.yml` 不会被覆盖，所以里面还没有 `discord:` 段。缺这一段等同于"关闭"，不会报错。
把本页的配置块粘进你的 `config.yml` 再重载即可。
语言文件不用管：缺失的条目会自动回退到插件内置的同名文件，所以 `discord:` 的消息模板开箱即用。
:::

## 会推送什么

| 事件 | 触发时机 |
|------|---------|
| `channel-detection` | 匹配到 `forbiddenList` 里的插件频道（见[频道检测](./channel-detection)） |
| `sign-detection` | 告示牌翻译键被客户端翻译了（见[告示牌翻译检测](./sign-detection)） |
| `escalation` | 触发了阶梯处罚的某一级（见[阶梯处罚](./escalation)） |
| `evade` | 告示牌检测始终无法完成 |

```yaml
discord:
  events:
    channel-detection: true
    sign-detection: true
    escalation: true
    evade: false
```

### 一次检测只发一条

当阶梯处罚接管了某次检测时，只发 `escalation` 那条，单次检测那条会被跳过。
同一次进服不会收到两条消息。

action 为 `IGNORE` 的检测默认不推送，因为 `IGNORE` 通常就代表"这个不用管"。
如果你就是想把它当数据收集，把 `include-ignored` 改成 `true`。

## 外观

```yaml
discord:
  embed: true                  # false 则发纯文本单行，不发 embed
  mention: "<@&123456789012345678>"
  server-name: ""              # 模板里的 %server%，留空则用服务端自身名称
  colors:
    notice: "#E67E22"
    kick: "#E74C3C"
    ban: "#992D22"
    evade: "#F1C40F"
    test: "#2ECC71"
```

`mention` 会加在每条消息前面。角色用 `<@&角色ID>`，个人用 `<@用户ID>`——
在 Discord 里打开开发者模式后右键即可复制 ID。

::: tip
`mention` 留空时，任何人都不会被 @：就算某个模组名或踢出理由里混进了 `@everyone`，
消息也会以"禁止提醒"的方式发出，只会显示成普通文字。
:::

## 消息文本

模板在[语言文件](./messages)的 `discord:` 段里，会跟着 `language:` 自动切换：

```yaml
discord:
  channel-detection: "**%player%** 带着被禁止的插件频道进服：`%mods%`"
  sign-detection: "**%player%** 被告示牌检测判定使用了 **%mod%**（处理：%action%）"
  escalation: "**%player%** 因 **%mod%** 累计违规 %count% 次 -> 第 %step%/%steps% 级：%action%"
  evade: "**%player%** 始终没有完成模组检测（%reason%）"
  title-detection: "检测到模组"
  field-player: "玩家"
```

占位符：`%player%` `%uuid%` `%server%` `%mod%` `%mods%` `%count%` `%step%` `%steps%`
`%reason%` `%source%` `%action%`

支持 Discord 的 markdown（`**粗体**`、`` `代码` ``、`*斜体*`）。
Minecraft 颜色代码会被自动去掉，所以 `&c&l检测到 Freecam` 这样的理由发出来是"检测到 Freecam"。

某个事件用不到的 embed 字段会被整个跳过，而不是显示成空值。

## 可靠性

本插件里没有队列、没有线程、也没有重试逻辑：这些全部由 DiscordSRV 的 JDA 负责
（请求队列、限流、重试）。这边每次调用都立即返回，所以 Discord 再慢或者连不上，
都不会拖慢玩家进服、数据包处理或区域线程。

如果检测触发时 DiscordSRV 还没连上，这条消息会被跳过，控制台提示一次。
`/modblocker discord test` 会直接告诉你当前状态：

| 回复 | 含义 |
|------|------|
| 测试消息已发出 | 正常 |
| DiscordSRV 还在连接 | JDA 握手还没完成，稍后重试 |
| 无法解析目标频道 | `discord.channel` 指向的东西 DiscordSRV 找不到 |
| 未安装 DiscordSRV | bridge 压根没被加载 |

## 为什么不直接用 webhook

直接发 webhook 可以不依赖 DiscordSRV，但那意味着本插件要自己维护一个 HTTP 客户端、
发送队列、后台线程，以及 429 / 5xx 的重试逻辑；或者 shade 进 OkHttp 和 kotlin-stdlib，
那会让 jar 从约 100KB 涨到好几 MB，违背"无依赖，丢进去就能用"这个卖点。
走 DiscordSRV 可以把这些全部挡在 jar 外面。
