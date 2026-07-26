package ai.jadebase.model;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.stereotype.Component;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.Comparator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;

@Component
public class OpenAiProviderClient {

    private final ObjectMapper json;
    private final HttpClient http;

    public OpenAiProviderClient(ObjectMapper json) {
        this.json = json;
        this.http = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(10)).build();
    }

    public List<String> discoverModels(String baseUrl, String apiKey) {
        JsonNode body = send(request(baseUrl, "models", apiKey).GET().build());
        JsonNode data = body.path("data");
        if (!data.isArray()) throw new ModelProviderException("模型接口未返回 data 列表，可手动填写模型 ID");
        LinkedHashSet<String> ids = new LinkedHashSet<>();
        data.forEach(item -> {
            String id = item.path("id").asText("").trim();
            if (!id.isBlank()) ids.add(id);
        });
        return ids.stream().sorted(Comparator.naturalOrder()).toList();
    }

    public void testChat(String baseUrl, String apiKey, String modelId) {
        try {
            String payload = json.writeValueAsString(Map.of(
                    "model", modelId,
                    "max_tokens", 4,
                    "messages", List.of(Map.of("role", "user", "content", "Reply OK"))));
            JsonNode body = send(request(baseUrl, "chat/completions", apiKey)
                    .header("Content-Type", "application/json")
                    .POST(HttpRequest.BodyPublishers.ofString(payload))
                    .build());
            if (!body.path("choices").isArray() || body.path("choices").isEmpty()) {
                throw new ModelProviderException("模型连接成功，但没有返回有效对话结果");
            }
        } catch (ModelProviderException exception) {
            throw exception;
        } catch (Exception exception) {
            throw new ModelProviderException("模型连接测试失败：" + exception.getMessage(), exception);
        }
    }

    private HttpRequest.Builder request(String baseUrl, String path, String apiKey) {
        HttpRequest.Builder builder = HttpRequest.newBuilder(endpoint(baseUrl, path))
                .timeout(Duration.ofSeconds(45))
                .header("Accept", "application/json");
        if (apiKey != null && !apiKey.isBlank()) builder.header("Authorization", "Bearer " + apiKey.trim());
        return builder;
    }

    private JsonNode send(HttpRequest request) {
        try {
            HttpResponse<String> response = http.send(request, HttpResponse.BodyHandlers.ofString());
            JsonNode body = response.body() == null || response.body().isBlank()
                    ? json.createObjectNode() : json.readTree(response.body());
            if (response.statusCode() < 200 || response.statusCode() >= 300) {
                String message = body.path("error").path("message").asText("");
                if (message.isBlank()) message = body.path("message").asText("");
                if (message.isBlank()) message = "HTTP " + response.statusCode();
                throw new ModelProviderException("模型服务请求失败：" + trim(message, 300));
            }
            return body;
        } catch (ModelProviderException exception) {
            throw exception;
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            throw new ModelProviderException("模型服务请求已中断", exception);
        } catch (Exception exception) {
            throw new ModelProviderException("无法连接模型服务：" + exception.getMessage(), exception);
        }
    }

    private URI endpoint(String baseUrl, String path) {
        String normalized = baseUrl.endsWith("/") ? baseUrl.substring(0, baseUrl.length() - 1) : baseUrl;
        return URI.create(normalized + "/" + path);
    }

    private String trim(String value, int length) {
        return value.length() <= length ? value : value.substring(0, length);
    }
}
