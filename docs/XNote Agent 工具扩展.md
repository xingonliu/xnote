# XNote Agent 工具扩展

## 注册与调用

`AgentToolProvider.registrations()` 返回 `AgentToolRegistration` 列表。将 Provider 传给 `AgentTimeline(toolProviders = ...)`，即可加入预算计算、模型请求和执行链路；不需要在 Timeline 或 Executor 中增加名称分支。内置笔记、记忆、文件工具通过同一注册表调用。

每个注册项声明：

| 字段 | 作用 |
| --- | --- |
| definition | 模型可见名称、说明及 JSON 参数结构 |
| available | 每次请求按当前权限决定是否提供工具 |
| authorize | 执行与缓存重放前重新检查授权，默认沿用 available |
| execute | 参数解析、资源权限校验及业务执行 |
| maxArgumentCharacters | 参数字符上限 |
| allowedInSelection | 是否允许出现在选区任务中 |
| auditReason | 持久化的授权／执行说明 |
| mode | LocalTransaction 或 External |
| reconcile | 外部结果不确定时核对原调用，返回 null 表示尚未确定 |

名称必须符合 `[A-Za-z0-9_-]{1,64}`，注册表拒绝重复名称。外部工具建议使用 `mcp_<server>_<tool>` 名称，Adapter 负责稳定映射原始名称，不能覆盖内置工具。

内置工具的可见性由笔记权限等级、有效运行授权和附加快照决定；执行函数继续检查具体笔记、选区、固定版本及单次创建授权。记忆和输出文件使用各自规则。外部 Provider 必须查询独立授权，不能以笔记三级权限代表外部服务授权。

## 执行与结果

`AgentToolExecutor` 负责运行状态、调用标识、参数一致性、执行状态、权限依据、来源记录和结果持久化。同一运行内同一调用 ID 只能对应同一工具与参数。已完成的调用重用结果，权限或来源失效时不重新执行。

处理函数返回 `AgentToolResult`。`Finished.result` 必须携带原调用 ID、工具名和 JSON 对象结果；错误使用 `error` 字段。`sources` 标记笔记及快照来源，`sourceMessageIds` 标记历史消息来源。参数解析和资源检查必须在业务写入之前完成；内置结构化参数使用严格解析。

本地工具使用 LocalTransaction。正文、审阅、来源与工具回执在同一数据库事务提交。所有内置写入继续通过原有合并与审阅规则。

外部工具使用 External。执行器先提交 Executing 记录，再在数据库事务外等待服务，最后提交结果。异常或取消后保存 Unknown；继续运行时先调用 reconcile 核对，结果未确定前保持暂停。Provider 应用 `context.access.runId` 与 `call.id` 标识同一个远端操作；reconcile 只能查询原操作，不能重新发起写入。外部执行应返回 Finished，授权须在调用前完成。远端不提供可核对结果时返回 null，不能承诺恰好一次执行。

连接测试仅检查一次文字流式请求，不影响注册表。真实模型不支持工具协议时按服务／协议错误展示，不把工具静默移除。

## 统一 read

| 参数 | 结果 |
| --- | --- |
| `{}` | 授权目录，包含笔记本与笔记元信息 |
| `{"notebook_id":"id"}` | 指定笔记本内的笔记元信息 |
| `{"notebook_id":""}` | 未归档笔记元信息 |
| `{"note_id":"id"}` | 当前正文及可继续分页的 snapshot_id |
| `{"note_id":"id","snapshot_id":"snapshot"}` | 固定发送版本正文 |

`note_id` 与 `notebook_id` 互斥；目录不接受 snapshot_id。目录每页最多 20 项，先过滤权限再按 offset 分页，返回 entries、has_more、next_offset。笔记条目包含归属 ID、标题、版本与更新时间。正文每页最多 6000 UTF-16 字符，分页保留同一个 snapshot_id。

附加笔记在本地保存完整快照，初始消息仅含 ID、快照 ID、版本、标题、更新时间及可选选区定位；Agent 按需调用 read。普通图片和文件消息继续使用各自的内容输入规则。

## MCP 接入边界

当前已提供外部 Provider 的注册、独立授权、事务外执行与结果核对接口。S13 的 MCP Adapter 将服务发现结果转换为注册项，将工具调用与结果转换为统一数据结构，并实现服务授权和结果核对；MCP 网络连接和管理页面不在本次交付范围。
