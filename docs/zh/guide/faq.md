# 常见问题

## 能检测 Vape / LiquidBounce 这类外挂吗？

一般不能。这类客户端通常不会暴露可检测的插件频道。本插件更适合抓 **Fabric / Forge / Lunar 等加载器**，以及你能配到频道或翻译键的实用模组。

## 这是反作弊吗？

不是。这是**模组存在性检测**，不是作弊行为检测系统。

## 原理是什么？

主路径：检查玩家加入时（或 PacketEvents 拦截时）监听的 Plugin Message Channel，与 `forbiddenList` 比对。

可选路径：在支持的 API 上用虚拟告示牌翻译键判断客户端是否装了特定模组。

## 告示牌检测每个版本都能用吗？

不能。依赖 Paper 公开的虚拟告示牌 API（约 1.21.5+）。不支持时自动跳过，不影响主功能。

## 为什么加了模组名还是检测不到？

`forbiddenList` 匹配的是**频道关键词**，不是模组显示名。请开 `logger`，用真实频道写入列表；若模组不开频道，改用告示牌翻译检测。

## 误踢了管理员怎么办？

给对应账号 `fakemodblocker.bypass`，或临时把 `enable` 设为 `false` 后 `/modblocker reload`。
