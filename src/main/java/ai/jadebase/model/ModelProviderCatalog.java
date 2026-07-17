package ai.jadebase.model;

import org.springframework.stereotype.Component;

import java.util.List;

@Component
public class ModelProviderCatalog {

    private final List<Preset> presets = List.of(
            preset(ModelProvider.Type.DEEPSEEK, "DeepSeek", "深度求索", "https://api.deepseek.com", "DS", List.of("deepseek-v4-flash", "deepseek-v4-pro")),
            preset(ModelProvider.Type.DASHSCOPE, "阿里云百炼", "通义千问 Qwen", "https://dashscope.aliyuncs.com/compatible-mode/v1", "QW", List.of("qwen-plus", "qwen-max", "qwen-turbo")),
            preset(ModelProvider.Type.ZHIPU, "智谱 AI", "GLM", "https://open.bigmodel.cn/api/paas/v4", "GLM", List.of("glm-5.1", "glm-4.7")),
            preset(ModelProvider.Type.MOONSHOT, "Kimi", "月之暗面", "https://api.moonshot.cn/v1", "K", List.of("kimi-k3", "kimi-k2.6")),
            preset(ModelProvider.Type.VOLCENGINE, "火山方舟", "豆包与第三方模型", "https://ark.cn-beijing.volces.com/api/v3", "DB", List.of()),
            preset(ModelProvider.Type.QIANFAN, "百度千帆", "文心与第三方模型", "https://qianfan.baidubce.com/v2", "QF", List.of()),
            preset(ModelProvider.Type.SILICONFLOW, "硅基流动", "国产模型聚合平台", "https://api.siliconflow.cn/v1", "SF", List.of()),
            preset(ModelProvider.Type.OPENAI, "OpenAI", "GPT 系列", "https://api.openai.com/v1", "AI", List.of()),
            preset(ModelProvider.Type.OPENAI_COMPATIBLE, "OpenAI 通用接口", "任意兼容 Chat Completions 的服务", "https://your-provider.example/v1", "<>" , List.of()),
            preset(ModelProvider.Type.OLLAMA, "Ollama", "本地模型", "http://host.docker.internal:11434/v1", "OL", List.of()),
            preset(ModelProvider.Type.VLLM, "vLLM", "OpenAI 兼容推理服务", "http://host.docker.internal:8000/v1", "VL", List.of()),
            preset(ModelProvider.Type.LM_STUDIO, "LM Studio", "桌面本地模型", "http://host.docker.internal:1234/v1", "LM", List.of()),
            preset(ModelProvider.Type.LOCALAI, "LocalAI", "自托管兼容服务", "http://host.docker.internal:8080/v1", "LA", List.of())
    );

    public List<Preset> all() {
        return presets;
    }

    public Preset require(ModelProvider.Type type) {
        if (type == null) throw new IllegalArgumentException("请选择模型供应商");
        return presets.stream().filter(item -> item.type() == type).findFirst()
                .orElseThrow(() -> new IllegalArgumentException("不支持的模型供应商"));
    }

    private Preset preset(ModelProvider.Type type, String name, String description, String url,
                          String mark, List<String> models) {
        return new Preset(type, name, description, url, mark, type.apiKeyRequired(), type.local(), models);
    }

    public record Preset(ModelProvider.Type type, String name, String description, String defaultBaseUrl,
                         String mark, boolean apiKeyRequired, boolean local, List<String> suggestedModels) { }
}
