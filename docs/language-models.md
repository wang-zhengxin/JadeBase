# 语言模型管理指南

工作区所有者可从“管理后台 -> 语言模型”统一配置聊天模型。普通成员只能使用管理员选定的
默认模型，不能读取供应商配置或凭证。

## 支持的供应商

| 类型 | 默认 API Base URL | 说明 |
| --- | --- | --- |
| DeepSeek | `https://api.deepseek.com` | DeepSeek 官方 OpenAI 兼容接口 |
| 阿里云百炼 | `https://dashscope.aliyuncs.com/compatible-mode/v1` | 通义千问 Qwen |
| 智谱 AI | `https://open.bigmodel.cn/api/paas/v4` | GLM 系列 |
| Kimi | `https://api.moonshot.cn/v1` | 月之暗面 |
| 火山方舟 | `https://ark.cn-beijing.volces.com/api/v3` | 豆包与方舟模型 |
| 百度千帆 | `https://qianfan.baidubce.com/v2` | 文心与千帆模型 |
| 硅基流动 | `https://api.siliconflow.cn/v1` | 国内模型聚合平台 |
| OpenAI | `https://api.openai.com/v1` | GPT 系列 |
| OpenAI 通用接口 | 自定义 | 任意兼容 `/models` 与 `/chat/completions` 的服务 |
| Ollama | `http://host.docker.internal:11434/v1` | 本机或局域网 Ollama |
| vLLM | `http://host.docker.internal:8000/v1` | 自托管 vLLM |
| LM Studio | `http://host.docker.internal:1234/v1` | 桌面本地模型 |
| LocalAI | `http://host.docker.internal:8080/v1` | 自托管兼容服务 |

API Base URL 必须是完整的兼容接口根路径。JadeBase 会在其后追加 `/models` 和
`/chat/completions`，并拒绝包含查询参数、用户信息或非 HTTP(S) 协议的地址。

## 接入流程

1. 点击供应商卡片，确认 API Base URL 并填写 API Key。
2. 点击“发现模型”读取供应商的 `/models`；接口不支持发现时可手动填写模型 ID。
3. 选择需要开放给工作区的模型并点击“测试连接”。测试会发送一个最多生成 4 token 的最小请求，可能产生极少量模型调用费用。
4. 保存供应商。首个启用模型会自动成为默认模型，也可在页面顶部随时切换。

编辑供应商时 API Key 留空会保留原密钥。删除当前默认供应商后，系统会自动选择另一个已启用
模型；没有其他配置时回退到 `MODEL_BASE_URL`、`MODEL_API_KEY` 和 `CHAT_MODEL` 环境变量。
为兼容旧部署，环境变量中的纯主机地址会自动补 `/v1`，管理后台中的地址则始终按完整 API 根路径使用。

## 本地模型

JadeBase 直接运行在宿主机时，可将 Base URL 写为 `http://127.0.0.1:<port>/v1`。Docker
Compose 部署时，默认使用 `host.docker.internal` 访问宿主机；模型服务位于其他机器时填写其
局域网地址，并确保容器网络能够访问。Ollama、vLLM、LM Studio 和 LocalAI 的 API Key 可留空。

部分本地服务不实现 `/models`，此时手动添加精确模型 ID 即可。模型 ID 必须与服务端实际加载
名称一致，否则连接测试或聊天请求会失败。

## 凭证与升级

生产环境必须配置长期稳定的密钥：

```bash
CREDENTIAL_ENCRYPTION_KEY=replace-with-a-long-random-deployment-secret
```

模型 API Key 和连接器密钥均使用该值进行 AES-GCM 加密，读取接口只返回“已配置”状态，不会
返回明文。修改或丢失密钥后已有凭证无法解密。旧部署如果只设置了
`CONNECTOR_ENCRYPTION_KEY`，升级时会自动将其作为兼容值；建议后续迁移到统一变量并保持值不变。
