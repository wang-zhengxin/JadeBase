# Agent 注册中心

Agent 注册中心用于把系统提示词、知识库、模型和运行策略封装成可发布的工作区能力。工作区所有者负责配置与发布，普通用户只能使用已启用且有权限访问的发布版本。

## 生命周期

1. 创建 Agent 草稿，填写名称、描述和系统提示词。
2. 绑定一个知识库，并选择工作区默认模型或已启用的指定模型。
3. 设置访问范围、默认深度思考和最大执行轮次。
4. 发布后生成不可变版本。后续编辑只改变草稿，不影响正在使用的发布版本。
5. 再次发布生成新版本；停用后立即从用户侧 Agent 列表移除。

运行时始终读取最新发布版本的快照。每次调用都会记录 Agent 版本、用户、问题、会话、耗时和成功或失败状态，便于后续审计与工作流观测。

## 权限

- 只有工作区所有者可以创建、编辑、发布、停用和删除 Agent。
- `EVERYONE` 可被工作区所有用户调用。
- `PRIVATE` 仅创建者和工作区所有者可调用。
- 草稿、未发布或已停用 Agent 不能从聊天接口执行。

## 对话集成

`POST /api/v1/chat` 可选传入 `agentId`。选择 Agent 后，服务端会使用发布版本中的知识库、模型、系统提示词和深度思考设置，不信任客户端重复提交这些配置。

```json
{
  "agentId": "c393bca1-352e-43c4-98a8-55d8ce4f179d",
  "question": "总结产品的部署要求",
  "thinkMode": false
}
```

当 Agent 默认开启深度思考时，它会覆盖请求中的关闭状态。响应会返回实际执行的 `agentId`、`agentName` 和 `agentVersion`。

## 接口

```text
GET    /api/v1/agents
GET    /api/v1/admin/agents
GET    /api/v1/admin/agents/{agentId}
POST   /api/v1/admin/agents
PUT    /api/v1/admin/agents/{agentId}
POST   /api/v1/admin/agents/{agentId}/publish
PATCH  /api/v1/admin/agents/{agentId}/enabled
GET    /api/v1/admin/agents/{agentId}/versions
GET    /api/v1/admin/agents/{agentId}/runs
DELETE /api/v1/admin/agents/{agentId}
```

`maxIterations` 已纳入版本配置，供后续 MCP 工具循环和工作流编排阶段使用；当前知识问答执行仍是单次模型调用。
