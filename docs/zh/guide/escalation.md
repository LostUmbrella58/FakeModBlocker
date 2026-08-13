# 阶梯处罚（警告 → 踢出 → 封禁）

可选的**累犯计数系统**。开启后，插件不再每次都执行同一个处罚，而是按玩家累计违规次数，逐级升级：

> 第 1 次带 Freecam 进服 → 警告并踢出
> 第 2 次带 Freecam 进服 → 永久封禁

该功能**默认关闭**。`escalation.enabled` 为 `false` 时，行为与旧版本完全一致：每次检测直接执行各自的固定处罚。

## 启用

```yaml
escalation:
  enabled: true
  per-mod: true
  count-once-per-session: true
  reset-after: "30d"

  ladder:
    - violations: 1
      action: KICK
    - violations: 2
      action: BAN
      duration: ""      # 留空 / 0 / "permanent" 表示永久
```

改完执行 `/modblocker reload`。

以上就是插件自带的默认阶梯，只需要把 `enabled` 改成 `true`。文案取自[语言文件](./messages)里的
`escalation.warn` / `escalation.kick` / `escalation.ban`，会自动跟随 `language:` 切换中英文。
只有想单独覆盖某一级时才写 `message:`：

```yaml
    - violations: 1
      action: KICK
      message: |-
        &c&l警告 &7(第 %count% 次)
        &f检测到你正在使用 &c%mod%&f，本服禁止该模组。
        &7请卸载后再进服，再次带着它进来将被永久封禁。
```

## 哪些检测会走阶梯

| 检测方式 | 是否走阶梯 |
|----------|-----------|
| `forbiddenList` 频道命中 | ✅ 总是 |
| 告示牌检测且 `action: KICK` / `BAN` | ✅ 除非该模组写了 `punishment.escalation: false` |
| 告示牌检测且 `action: NOTICE` / `IGNORE` | ❌ 从不 |

`NOTICE` 和 `IGNORE` 保持"只观察"是刻意设计：开启阶梯处罚不应该把你原本只想记录的模组升级成封禁。

想让某个模组固定处罚、不走阶梯：

```yaml
      Freecam:
        detect:
          key: "key.freecam.toggle"
        punishment:
          action: "KICK"
          reason: "&c本服禁止 Freecam。"
          escalation: false     # 永远只踢，不会升级到封禁
```

## 选项

| 选项 | 默认值 | 说明 |
|------|--------|------|
| `enabled` | `false` | 整个系统的总开关 |
| `per-mod` | `true` | `true`：每个模组独立计数；`false`：每位玩家只有一个总计数 |
| `count-once-per-session` | `true` | 同一模组在同一次在线期间最多只记 1 次 |
| `reset-after` | `"30d"` | 超过这段时间没再触发就清零；`0` / `never` 表示永不过期 |
| `use-custom-ban-command` | `false` | 把 `BAN` 交给其他封禁插件，而不是原版封禁列表 |
| `ban-command` | `ban %player% %reason%` | 永久封禁那一级使用 |
| `temp-ban-command` | `tempban %player% %duration% %reason%` | 带 `duration` 的那一级使用 |
| `ladder` | 2 级 | 阶梯本体，见下 |

::: warning count-once-per-session
建议保持 `true`。一次进服常常会被多个检测同时命中（PacketEvents 频道包、进服扫描、告示牌检测），
关掉它可能导致一次进服连跳好几级。
:::

## 阶梯写法

每一级由 `violations`（从第几次违规开始生效）和一个动作组成。**最后一级会对之后所有违规继续生效**，
所以以永久封禁结尾的阶梯不会"用完"。

| 动作 | 效果 |
|------|------|
| `WARN` | 只发消息，玩家留在服务器 |
| `KICK` | 消息作为踢出理由（遵循 `useCustomKickCommand`） |
| `BAN` | 按 `duration` 封禁（留空为永久），然后踢出 |
| `COMMAND` | 由控制台执行 `command:` |
| `IGNORE` | 只计数和记录日志 |

时长支持 `30d`、`12h`、`90m`、`45s`、`2w`，也支持 `1d12h` 这类组合写法。

更温和的四级示例：

```yaml
  ladder:
    - violations: 1
      action: WARN
      message: "&e&l警告 &r&f本服禁止 %mod%，请卸载——下次将直接踢出。"
    - violations: 2
      action: KICK
    - violations: 3
      action: BAN
      duration: "7d"
    - violations: 4
      action: BAN
      duration: ""
```

交给 LiteBans / AdvancedBan / CMI 执行封禁：

```yaml
  use-custom-ban-command: true
  ban-command: "banoffline %player% %reason%"
  temp-ban-command: "tempban %player% %duration% %reason%"
```

开启 `use-custom-ban-command` 后，FakeModBlocker 只负责执行命令，踢人交由对方插件完成。

## 占位符

可用于每一级的 `message`、语言文件 `messages_<lang>.yml` 里的 `escalation.*`，以及各类命令模板。

| 占位符 | 含义 |
|--------|------|
| `%player%` | 玩家名 |
| `%mod%` | 命中的模组 / 频道关键词（多个时用逗号分隔） |
| `%count%` | 该计数器上此玩家的累计违规次数 |
| `%step%` / `%steps%` | 当前第几级 / 共几级 |
| `%next%` | 下一级所需的违规次数，最后一级为 `-` |
| `%duration%` | 封禁时长，永久时为 `permanent` |
| `%reason%` | 告示牌检测中该模组自己配置的 `reason`（如果有） |
| `%source%` | `plugin channel` 或 `sign translation` |
| `%action%` | `warn` / `kick` / `ban` / `command` / `ignore` |

某一级不写 `message` 时，会回退到语言文件里的 `escalation.warn` / `escalation.kick` / `escalation.ban`。

## 管理违规记录

```
/modblocker violations <玩家>        # 查看已记录的计数（离线玩家也能查）
/modblocker clear <玩家>             # 清空该玩家全部计数
/modblocker clear <玩家> Freecam     # 只清空某一个计数
```

记录保存在 `plugins/FakeModBlocker/violations.yml`，异步写入，重启后依然有效；加载时会自动清理已过期的计数。

```yaml
records:
  - uuid: 069a79f4-44e9-4726-a5be-fca90e38aaf5
    name: Notch
    counters:
      - key: Freecam
        count: 1
        last: 1755000000000
```

只在服务器关闭时手动编辑该文件，平时请用上面的命令。

## 备注

- 拥有 `fakemodblocker.bypass` 或 `fakemodblocker.kickbypass` 的玩家不会被频道检测记违规，与之前不会被踢一致。
- 拥有 `fakemodblocker.notify` 的管理员每次升级处罚都会收到提示（遵循 `notifyStaff`）。
- 如果阶梯为空或写错，插件会在控制台告警并回退到原本的固定处罚——配置写错绝不会变成"放过作弊者"。
