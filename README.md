# JadeBase

> Enterprise AI Search & Agent Platform for Chinese teams.

JadeBase 是面向中国企业的智能搜索与 Agent 平台。第一阶段提供一个可运行的知识库问答闭环：文档上传、中文分块、混合检索、OpenAI 兼容模型接入，以及可追溯的引用结果。

## 当前能力

- 知识库创建与文档管理
- PDF、DOCX、Markdown、TXT、CSV 文本提取
- 面向中文的重叠分块
- 关键词与向量混合召回
- DeepSeek、通义千问等 OpenAI 兼容接口
- 回答引用与相关度展示
- 对话、消息与引用来源持久化
- 服务端工作区设置与通知状态
- 对话偏好、个人指令与可引用的工作区记忆
- 邮箱注册登录、BCrypt 密码哈希与可撤销的服务端会话
- 异步文档索引、进度轮询和失败重试
- Flyway 数据库迁移与 PostgreSQL pgvector HNSW
- 中文 BM25、向量召回、RRF 融合与可选 BGE/Qwen Reranker
- 持久化索引任务、租约恢复与 SSE 实时进度
- 飞书云文档、Wiki 空间与云盘文件夹连接器
- 飞书全量/增量同步、游标恢复、限流退避和远程删除映射
- RAG 评测 API、检索诊断与 Prometheus 指标
- 无模型密钥时的本地演示模式
- H2 零依赖启动与 PostgreSQL 容器化部署

## 快速开始

需要 Java 21、Maven 3.9+、Node.js 22 和 pnpm。

```bash
mvn spring-boot:run
```

另开一个终端启动独立前端：

```bash
cd frontend
corepack enable
pnpm install
pnpm dev
```

访问 <http://localhost:5173>，注册首个账号后即可进入工作区。首个账号自动成为工作区所有者；
不配置模型密钥也可以完成上传、检索和引用演示。

接入 DeepSeek：

```bash
export MODEL_BASE_URL=https://api.deepseek.com
export MODEL_API_KEY=your-key
export CHAT_MODEL=deepseek-chat
mvn spring-boot:run
```

DeepSeek 暂不提供 Embedding 接口时，可以单独配置通义千问兼容接口：

```bash
export EMBEDDING_BASE_URL=https://dashscope.aliyuncs.com/compatible-mode
export EMBEDDING_API_KEY=your-key
export EMBEDDING_MODEL=text-embedding-v3
```

使用 Docker Compose：

```bash
cp .env.example .env
docker compose up --build
```

访问 <http://localhost:8080>。Compose 使用独立 Nginx 前端统一代理 API，后端不直接暴露到宿主机。

容器环境自动启用 PostgreSQL 检索实现，包括 pgvector HNSW 和数据库侧 BM25。Embedding
统一输出 384 维；更换模型后需要设置 `EMBEDDING_DIMENSIONS=384` 并重新索引知识库。

## 核心接口

```text
GET    /api/v1/knowledge-bases
POST   /api/v1/knowledge-bases
GET    /api/v1/knowledge-bases/{id}/documents
POST   /api/v1/knowledge-bases/{id}/documents
DELETE /api/v1/knowledge-bases/{id}/documents/{documentId}
POST   /api/v1/knowledge-bases/{id}/documents/{documentId}/retry
POST   /api/v1/knowledge-bases/{id}/documents/{documentId}/reindex
POST   /api/v1/knowledge-bases/{id}/reindex
GET    /api/v1/knowledge-bases/{id}/documents/events
POST   /api/v1/chat
POST   /api/v1/evaluations
GET    /api/v1/conversations
GET    /api/v1/conversations/{id}
DELETE /api/v1/conversations/{id}
GET    /api/v1/settings
PUT    /api/v1/settings
GET    /api/v1/notifications
POST   /api/v1/notifications/read-all
GET    /api/v1/memories
POST   /api/v1/memories
DELETE /api/v1/memories/{id}
POST   /api/v1/auth/register
POST   /api/v1/auth/login
GET    /api/v1/auth/me
PATCH  /api/v1/auth/me
POST   /api/v1/auth/change-password
POST   /api/v1/auth/logout
GET    /api/v1/connectors/feishu/connections
POST   /api/v1/connectors/feishu/connections
POST   /api/v1/connectors/feishu/connections/test
GET    /api/v1/connectors/feishu/connections/{id}/spaces
GET    /api/v1/connectors/feishu/connections/{id}/folders
GET    /api/v1/connectors/feishu/sources
POST   /api/v1/connectors/feishu/sources
POST   /api/v1/connectors/feishu/sources/{id}/sync
GET    /api/v1/connectors/feishu/sync-tasks
POST   /api/v1/connectors/feishu/sync-tasks/{id}/retry
GET    /actuator/prometheus
```

详细设计见 [架构说明](docs/architecture.md)、[飞书连接器管理指南](docs/feishu-connector.md)、[评测指南](docs/evaluation.md) 和
[阶段路线图](docs/roadmap.md)。

## 技术栈

- Java 21 / Spring Boot 3
- Spring Data JPA / H2 / PostgreSQL
- PDFBox / Apache POI
- Vite / 原生 HTML、CSS、JavaScript 独立管理工作台
- Nginx 同源 API 与 SSE 反向代理

## 开发命令

```bash
mvn test
mvn package
cd frontend && pnpm install --frozen-lockfile && pnpm build
```

## License

MIT License
