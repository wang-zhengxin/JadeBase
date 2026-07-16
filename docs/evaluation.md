# RAG 评测指南

评测接口只执行检索，不调用聊天模型，因此适合持续集成和回归基线。每个用例包含问题、
期望命中的文档名和应在召回上下文中出现的关键术语。

```bash
curl -X POST http://localhost:8080/api/v1/evaluations \
  -H 'Content-Type: application/json' \
  -d '{
    "knowledgeBaseId": "YOUR_KNOWLEDGE_BASE_ID",
    "topK": 6,
    "cases": [{
      "question": "高风险变更如何审批？",
      "expectedDocuments": ["engineering-guidelines.md"],
      "expectedTerms": ["两名", "审查者"]
    }]
  }'
```

报告字段：

- `recallAtK`：期望文档是否出现在前 K 个结果中。
- `mrr`：第一个期望文档排名的倒数均值。
- `expectedTermCoverage`：期望术语在召回上下文中的覆盖率。
- `averageLatencyMillis`：检索链路平均耗时。

建议在 `examples/evaluation-dataset.json` 中维护 50 到 100 条中文黄金用例，并在修改
分块、Embedding、BM25 参数或 Reranker 后保存前后两份报告。
