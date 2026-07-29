# 命令与权限

## 命令

主命令：`/modblocker`

| 用法 | 说明 |
|------|------|
| `/modblocker reload` | 重载配置与语言文件 |
| `/modblocker check <玩家>` | 检查在线玩家的插件频道，并匹配 `forbiddenList`；若告示牌检测可用，也会触发一次检测 |

需要权限：`fakemodblocker.admin`（默认 OP）

## 权限

| 权限节点 | 默认 | 说明 |
|----------|------|------|
| `fakemodblocker.admin` | op | 使用管理命令（reload / check） |
| `fakemodblocker.bypass` | op | 完全绕过检测（通知与踢出都跳过） |
| `fakemodblocker.kickbypass` | op | 不会被踢，但频道日志仍可能打印到控制台 |
| `fakemodblocker.notify` | op | 收到员工通知（对应 `notifyStaff` / `notificationPermission`） |

::: tip
给管理或旁观账号开 `fakemodblocker.bypass`，避免自己被误踢。调试时可用 `kickbypass`：仍能在控制台看到频道信息，但不会踢人。
:::
