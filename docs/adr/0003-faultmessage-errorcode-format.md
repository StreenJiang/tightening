# 持久化 NG 原因使用 errorCode 格式而非翻译后文案

持久化字段（`TaskRecord.faultMessage`、`export_task.error_message`）存储 errorCode 格式（`errorCode` 或 `errorCode:arg1,arg2`），而非翻译后的自然语言文案。前端展示时自行翻译。

**为什么不是纯快照（中文文案）**：项目一直有数据快照理念——拧紧数据冗余存储当时的工作站/面/螺栓信息。但这里有个关键区别：TighteningData 存的是"扭矩=5.2Nm"这样的物理测量值，天然语言无关。而 faultMessage 存的是人类可读文本，中文文案对英文操作员没有意义。纯中文快照在国际化需求面前丧失了可用性。同时，技术异常信息（如 `Connection refused`）与业务错误混在同一字段，中文翻译反而会掩盖排查线索。

**为什么不是纯 code**：如果像 API 响应一样分开存储（faultCode + faultMessage 两列），引入冗余且与其他快照字段模式不一致。单字段存 errorCode 格式是成本最低的折中——仍然是快照（记录了"哪个条件触发了 NG"这个事实），但形式是语言无关的结构化字符串，而非翻译文案。

**为什么不是 `"task.circular_dependency"` 无参数格式**：有些错误需要携带运行时上下文（如 `"task.circular_dependency:5"` 带 taskId）。`errorCode:arg1,arg2` 的格式让前端既可以匹配 errorCode 做精确翻译，也可以在翻译表缺失时展示有意义的原始内容。

**格式规则**（记录在 `docs/superpowers/specs/2026-07-24-i18n-design.md`）：
- 冒号前为 errorCode，冒号后为逗号分隔的参数
- 无参数时只有 errorCode，不追加冒号
- 无法映射到任何 errorCode 的未预料异常，存原始异常类名
