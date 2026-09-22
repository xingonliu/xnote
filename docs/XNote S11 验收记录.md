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
