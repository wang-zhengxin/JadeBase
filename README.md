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
- 异步文档索引、进度轮询和失败重试
- 无模型密钥时的本地演示模式
- H2 零依赖启动与 PostgreSQL 容器化部署

## 快速开始

需要 Java 21 和 Maven 3.9+。

```bash
mvn spring-boot:run
```

访问 <http://localhost:8080>。系统会自动创建一个示例知识库；不配置模型密钥也可以完成上传、检索和引用演示。

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

## 核心接口

```text
GET    /api/v1/knowledge-bases
POST   /api/v1/knowledge-bases
GET    /api/v1/knowledge-bases/{id}/documents
POST   /api/v1/knowledge-bases/{id}/documents
DELETE /api/v1/knowledge-bases/{id}/documents/{documentId}
POST   /api/v1/knowledge-bases/{id}/documents/{documentId}/retry
POST   /api/v1/chat
GET    /api/v1/conversations
GET    /api/v1/conversations/{id}
DELETE /api/v1/conversations/{id}
GET    /api/v1/settings
PUT    /api/v1/settings
GET    /api/v1/notifications
POST   /api/v1/notifications/read-all
```

详细设计见 [架构说明](docs/architecture.md) 和 [阶段路线图](docs/roadmap.md)。

## 技术栈

- Java 21 / Spring Boot 3
- Spring Data JPA / H2 / PostgreSQL
- PDFBox / Apache POI
- 原生 HTML、CSS、JavaScript 管理工作台

## 开发命令

```bash
mvn test
mvn package
```

## License

MIT License
