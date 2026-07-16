# 阶段路线图

## Phase 1 · Knowledge MVP

- [x] 知识库和文档管理
- [x] 常用文件解析与中文分块
- [x] 混合检索与引用
- [x] 国产模型兼容接口
- [x] 无密钥演示模式
- [x] Web 工作台和容器化配置
- [x] 会话历史、服务端设置和通知状态

## Phase 2 · Production RAG

- [x] Flyway、pgvector HNSW 索引和中文 BM25 搜索
- [x] 异步文档流水线、失败重试和进度轮询
- [x] 持久化任务租约、服务端进度事件（SSE）与应用重启恢复
- [x] RRF 融合、会话 Query Rewrite 和 BGE / Qwen Reranker
- [x] RAG 评测 API、检索诊断和 Prometheus 可观测性
- [ ] OCR、表格和图片内容理解（暂缓，后续独立阶段实现）

## Phase 3 · China Connectors

- [ ] 飞书云文档与知识库
- [ ] 企业微信文档
- [ ] 钉钉知识库
- [ ] 语雀、Gitee、TAPD 和禅道
- [ ] 增量同步和源权限继承

## Phase 4 · Agent Workspace

- [ ] Agent 与工具注册中心
- [ ] MCP 工具接入
- [ ] 工作流编排和人工确认节点
- [ ] 企业级 RBAC、SSO、审计和多租户
