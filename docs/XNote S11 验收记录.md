# XNote S11 验收记录

## S11.1 事实源与权限基础

实现入口：`domain/agent/AgentContracts.kt`、`AgentPermissionPolicy.kt`、`AgentEditPolicy.kt`，以及 `data/db/AgentEntities.kt`、`AgentDao.kt`、`data/agent/AgentPermissionStore.kt`。

- Room 版本 4 新增独立的消息、片段、运行、队列、工具事件、权限、发送快照及引用、改动来源、单篇审阅、附件引用表。消息具有稳定 ID 和顺序；运行与队列固定模型配置 ID/版本；工具调用按运行/调用 ID 唯一。审阅与改动来源不进入 `NoteDocument`。
- 3→4 自动迁移只添加表；2→3→4 保留已有迁移路径。不再使用缺失迁移时删除重建的回退；没有迁移路径时明确失败并保留原库。
- 初始权限为不可查看、仅附加范围。当前正文读取、编辑和发送快照读取分别判定；回收站与已永久删除笔记不可读。多笔记本范围与权限等级独立。
- 用户每次保存权限递增 revision；运行临时授权和一级发送快照授权绑定 revision，设置变化后旧授权不能继续生效。模型不持有设置写入口。
- 创建目标只来自当前可写范围或用户本次授权；多个目标要求选择；仅附加范围无默认新建目标。新建后的临时编辑权只覆盖本次运行中新建笔记，不扩大笔记本授权。
- 领域校验拒绝未知 JSON 字段、非法文档结构、过期版本和选区，以及媒体内容、变换和相对顺序变动。背景和既有归属不属于可编辑正文输入。
- 接受后的撤回保留期固定为 30 天（边界不含第 30 天）；删除拒绝区分用户已恢复、原笔记本不存在和永久删除。实际差异合并、整篇回退事务及 UI 在 S11.6–S11.7 接入，本片不开启写工具。
- 附件回收计入 Agent 独立引用，后续消息与快照生命周期由 S11.5、S11.11 接入。

验证用例：`AgentPermissionPolicyTest`（权限矩阵、快照生命周期、撤权、创建归属）、`AgentEditPolicyTest`（媒体与结构边界、版本与选区、撤回期限和恢复判定）、`AgentFoundationTest`（3→4 保留正文、附件文件与设置，事实源与权限重开）、`NotebookMigrationTest`（2→3→4）。2026-09-22：112 项单元测试及 Android 16 模拟器上上述 3 项端侧测试全部通过；`testDebugUnitTest`、`lintDebug`、`assembleDebug`、专项 `connectedDebugAndroidTest` 成功。Lint 0 错误，16 条既有依赖版本、公共控件及资源警告。

迁移依据：[Room 官方迁移文档](https://developer.android.com/training/data-storage/room/migrating-db-versions)，通过 Context7 核查自动迁移、schema 导出及删除重建回退的行为。

## S11.2 三家模型接入与配置

实现入口：`domain/agent/ModelContracts.kt`、`data/agent/Model*`、`feature/agent/ModelSettingsScreen.kt`。

- P18 从“我的 → 模型与服务商”进入；主页只展示已配置列表与新增入口；新增和编辑为独立页面，编辑页管理测试、默认项和删除。预设通过 OpenRouter 公开目录拉取文字模型，主流供应商优先、同供应商按发布时间倒序，支持搜索与刷新；选择后补全模型 ID、OpenRouter 基地址、OpenAI 兼容协议及容量预算，填写 OpenRouter API Key。自定义手填地址和模型 ID，支持三种协议。目录失败可重试或使用自定义，编辑已有配置不依赖目录成功。
- Room 5 新增 Profile 表，4→5 自动迁移保留 Agent 事实源。普通配置不包含 API Key；凭据使用 Android Keystore AES/GCM 加密，密文位于 `noBackupFilesDir/model-credentials`。替换和删除成功后移除旧密文。界面不回填密钥，临时输入不写入保存状态。
- 配置保存递增版本；当前运行和待处理队列使用的配置禁止替换、删除或切换默认项。修改无关配置不会干扰当前运行；无有效默认项时明确要求处理，不自动选其他配置。认证信息只进入请求 Header，不放进 URL、消息、普通配置或错误日志。
- 统一消息、文字增量、工具调用、工具结果、用量与结束原因；OpenAI 需要结束原因和 `[DONE]`，Anthropic 需要结束原因和 `message_stop`，Gemini 需要 `finishReason`。EOF 不冒充完成；输出上限与过滤结果独立于正常完成。Gemini 工具回传保留原始 parts 与 thoughtSignature。
- 能力测试提示最多三次请求及可能产生费用；先测试文字流式，再用无副作用的函数验证调用及结果回传。仅文字成功时仍可聊天，工具保持未验证。模型、服务、密钥或预算配置变化使能力验证失效。
- OkHttp 5.5.0 处理 HTTPS、取消与超时；不自动重试或跟随重定向。连接/写入 15 秒、读取 90 秒、单请求总时长 180 秒；SSE 单事件最多 1 Mi 字符、总响应最多 4 Mi 字符。网络、认证、配额、服务、协议、超时分别给出固定提示，不展示服务错误正文。
- 目录请求无需凭据，15 秒总超时，响应上限 8 MiB，不自动重试或跟随重定向；仅选择支持文字输入与输出的模型，不自动证明工具能力。预设上下文采用目录值（不超过本地 200 万上限），输出取 4096、目录输出上限及上下文预留后的较小值；用户可调整。
- 自定义 Profile 默认上下文 32768 Token、输出 4096 Token，要求用户依据服务能力填写；它们是预算输入，不能作为已验证的模型容量。测试输出最多 1024 Token；工具预算预留 1024 Token。用量取服务返回值，不显示推测费用。

验证：`ModelProtocolTest` 在三种协议上覆盖文字、取消、HTTP 认证失败、工具参数分片与结果序列化、结束标记、SSE 边界和秘密隔离；`ModelProfileStoreTest` 验证 Keystore 密文、删除及配置锁；`ModelSettingsFlowTest` 验证新增、编辑、能力状态与删除。2026-09-22 上述测试通过。通过本地 `XNOTE_LIVE_CONFIG` 指向用户的 `apikey.env`，OpenAI 兼容服务上的 `gpt-5.6-terra` 已通过文字流式、工具调用及工具结果回传三步真实请求；密钥不写进代码或测试输出。Anthropic、Gemini 当前为受控协议测试，尚无对应真实服务凭据；三家真实服务全覆盖仍由 S11.12 验收。

2026-09-23 配置页面验收：`testDebugUnitTest` 共 131 项，130 项通过、1 项需真实服务凭据的测试跳过；`assembleDebug` 成功，`lintDebug` 0 错误、16 条既有警告。Android 16 模拟器上 `ModelSettingsFlowTest` 与 `ModelProfileStoreTest` 共 5 项通过，覆盖自定义新增/编辑/测试/删除、预设自动补全与持久化、目录失败重试、离线编辑已有预设、取消返回及凭据和配置锁。`ModelCatalogTest` 覆盖供应商排序、最新模型排序、去重、文字能力筛选、预算边界和无效目录。已核对模型列表页与独立新增页截图。公开目录实测返回 454 个条目；本次未使用 OpenRouter 付费推理请求作为验证。预设模式随 Profile 保存，自定义 OpenRouter 地址仍保留手填模式；变更服务地址时要求填写对应凭据。

目录依据：[OpenRouter 模型目录](https://openrouter.ai/docs/guides/overview/models)，通过 Context7 核查公开接口、模型元数据与凭据要求。

协议依据：[OpenAI Chat Completions 流式事件](https://developers.openai.com/api/reference/resources/chat/subresources/completions/streaming-events)、[Anthropic 流式 Messages](https://platform.claude.com/docs/en/build-with-claude/streaming)、[Gemini GenerateContent](https://ai.google.dev/api/generate-content)、[Android Keystore](https://developer.android.com/privacy-and-security/keystore)、[OkHttp 取消与超时](https://square.github.io/okhttp/recipes/)。Anthropic、Gemini 及 OkHttp 文档同时通过 Context7 核查。

## S11.3 单时间线与流式对话

实现入口：`domain/agent/AgentContextBudget.kt`、`data/agent/AgentTimeline.kt`、`feature/agent/AgentScreen.kt`。

- P10 通过应用级运行对象使用明确保存的默认配置；每次运行绑定配置版本。用户消息、助手占位、运行记录和草稿清空在同一事务提交，流式正文逐步保存，最终消息与运行状态在同一事务提交。
- Room 6 新增独立草稿表，5→6 自动迁移。冷启动恢复同一时间线和草稿，将遗留运行及未完成消息标记中断；未完成任务可保留内容并结束。失败、取消、断流与正常完成独立，输出上限和服务上下文超限进入容量暂停状态。
- 单条输入先按预算校验；超限保留完整草稿、不创建运行、不发送请求。预算从配置容量扣除最大输出及 1024 Token 工具预留；输入估算采用 UTF-8 字节数加每条 64 Token 开销，明确属于保守估算，不替代服务 Token 用量。
- 上下文只收录当前话题内已完成的完整问答对，排除带笔记来源的历史。接近预算时按最近优先选择完成轮次，每条长消息保留首尾各 120 字符并标记省略；当前输入始终保留全文。完整时间线不截断，模型摘要及长期召回由 S11.8 继续实现。
- 容量片段仅在上一运行已结束、发送下一条输入前关闭；“开始新话题”保留时间线并明确重置后续请求历史。此片未开放真实工具；模型异常返回工具调用会失败，不能触发笔记操作。
- 手机键盘打开时收起底部导航及空状态说明，为输入和发送保留空间；收起键盘后恢复导航。文字可选择复制；服务用量仅显示实际返回值。

验证：`AgentContextBudgetTest` 覆盖完整当前输入、完成轮次压缩、容量边界及保守估算；`AgentTimelineTest` 覆盖草稿超限、流式持久化、断流、输出/上下文超限、取消、磁盘库重开恢复、新话题上下文隔离；`AgentFlowTest` 覆盖发送、键盘布局、页面往返和新话题。2026-09-22 Android 16 模拟器上真实 `AgentLiveTimelineTest` 通过，从受保护凭据读取到真实回复及最终事实落库完整验证；测试传入的临时明文文件随即删除，结束后移除测试凭据。

最终构建：`testDebugUnitTest`、`lintDebug`、`assembleDebug` 成功；128 项单元测试中 127 项通过，1 项真实服务测试默认跳过，该项已单独显式开启并通过。Lint 0 错误、16 条既有警告。

端侧最终记录（Android 16、720×1280）：S11 事实源/迁移、模型配置、时间线及两页 UI 专项共 14 项通过，真实 Android 对话另行通过。检查手机配置页、键盘输入及完整对话截图，发送按钮可见、页面往返保留记录。

扩大到全部端侧用例时，运行 112 项出现 7 项失败，不能视为全套回归通过。对照本次接入前的 `f7424b3` 基线，复现 5 项相同失败：`existingTableAtDocumentEndAllowsTypingAfterItAndSaving`、`tabletPanesKeepDraftAndSelectionAcrossSearchResizeAndRecreation`、`wideWindowKeepsTheNavigationRailWhileSearchExpandsInTheListPane`、`notebookGridAdaptsToThemeWindowAndLargeText`、`edgesFadeIntoBothThemeBackgroundsAndRestoreWhenHidden`。其余 `insertBubbleAndImagePillWorkInEditor`、`existingImageAtDocumentEndAllowsTypingAfterItAndSaving` 在当前版本与基线单独重跑均通过，记录为不稳定用例；本次未改动这些用例或编辑器业务逻辑。完整手机/平板回归仍归入 S11.12。
