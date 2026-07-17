# 文档与知识管理

管理后台的「文档与知识」区域对工作区所有者开放，包含已有连接器、添加连接器、文档集和索引设置四个模块。

## 连接器

「已有连接器」集中展示飞书连接实例、Wiki/文件夹同步来源和最近任务。可在同一页完成连接测试、凭证更新、增量同步、暂停来源、失败重试和删除。

「添加连接器」是企业知识源目录。当前可用飞书云文档；企业微信和钉钉保留明确的规划状态，不会误导用户进入无效配置。

## 文档集

文档集可以跨知识库组织已上传或已同步的文档，不会复制原始文档和索引。删除文档集仅删除组合关系。单个文档集最多包含 500 个文档。

## 索引设置

工作区可调整：

- 分块大小与重叠字符数；
- 最终回答片段数、候选池大小和 RRF 常数；
- Reranker 和会话 Query Rewrite 开关。

新文档立即使用最新参数。分块参数变化时系统会标记「需要重建」，由所有者显式触发全量重建，不会在保存设置时突然占用大量资源。

## 管理接口

```text
GET    /api/v1/admin/knowledge/summary
GET    /api/v1/admin/knowledge/documents
GET    /api/v1/admin/knowledge/document-sets
POST   /api/v1/admin/knowledge/document-sets
PUT    /api/v1/admin/knowledge/document-sets/{id}
DELETE /api/v1/admin/knowledge/document-sets/{id}
GET    /api/v1/admin/knowledge/index-settings
PUT    /api/v1/admin/knowledge/index-settings
POST   /api/v1/admin/knowledge/reindex
```

上述接口均校验工作区所有者角色。
