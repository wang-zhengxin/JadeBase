package ai.jadebase.connector.feishu;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.stereotype.Component;

import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Component
public class FeishuApiClient {

    private static final int HTTP_ATTEMPTS = 4;
    private final ObjectMapper json;
    private final HttpClient http;
    private final FeishuConnectorProperties properties;

    public FeishuApiClient(ObjectMapper json, FeishuConnectorProperties properties) {
        this.json = json;
        this.properties = properties;
        this.http = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(10)).build();
    }

    public String authenticate(FeishuConnection connection, String appSecret) {
        try {
            String body = json.writeValueAsString(Map.of("app_id", connection.getAppId(), "app_secret", appSecret));
            JsonNode root = execute(connection, "POST", "/auth/v3/tenant_access_token/internal", Map.of(), body, null);
            String token = root.path("tenant_access_token").asText();
            if (token.isBlank()) throw new FeishuApiException("飞书未返回 tenant_access_token", false);
            return token;
        } catch (FeishuApiException exception) {
            throw exception;
        } catch (Exception exception) {
            throw new FeishuApiException("飞书认证请求失败", true, exception);
        }
    }

    public Page<RemoteContainer> listWikiSpaces(FeishuConnection connection, String token, String pageToken) {
        Map<String, String> query = pageQuery(pageToken);
        JsonNode data = execute(connection, "GET", "/wiki/v2/spaces", query, null, token).path("data");
        List<RemoteContainer> items = new ArrayList<>();
        data.path("items").forEach(item -> items.add(new RemoteContainer(
                text(item, "space_id"), text(item, "name"), FeishuSource.Type.WIKI)));
        return new Page<>(items, text(data, "page_token"), data.path("has_more").asBoolean(false));
    }

    public Page<RemoteItem> listWikiNodes(FeishuConnection connection, String token, String spaceId,
                                          String parentNodeToken, String pageToken) {
        Map<String, String> query = pageQuery(pageToken);
        if (parentNodeToken != null && !parentNodeToken.isBlank()) query.put("parent_node_token", parentNodeToken);
        JsonNode data = execute(connection, "GET", "/wiki/v2/spaces/" + encodePath(spaceId) + "/nodes",
                query, null, token).path("data");
        List<RemoteItem> items = new ArrayList<>();
        data.path("items").forEach(item -> {
            String nodeToken = text(item, "node_token");
            items.add(new RemoteItem(
                    text(item, "obj_token"), nodeToken, text(item, "parent_node_token"),
                    text(item, "obj_type"), text(item, "title"), text(item, "owner"),
                    connection.getWebBaseUrl() + "/wiki/" + nodeToken,
                    instant(item, "obj_edit_time"), item.path("has_child").asBoolean(false)));
        });
        return new Page<>(items, text(data, "page_token"), data.path("has_more").asBoolean(false));
    }

    public Page<RemoteItem> listFolderItems(FeishuConnection connection, String token, String folderToken,
                                            String pageToken) {
        Map<String, String> query = pageQuery(pageToken);
        query.put("folder_token", folderToken);
        JsonNode data = execute(connection, "GET", "/drive/v1/files", query, null, token).path("data");
        List<RemoteItem> items = new ArrayList<>();
        data.path("files").forEach(item -> items.add(new RemoteItem(
                text(item, "token"), null, text(item, "parent_token"), text(item, "type"),
                text(item, "name"), text(item, "owner_id"), text(item, "url"),
                instant(item, "modified_time"), "folder".equals(text(item, "type")))));
        return new Page<>(items, text(data, "next_page_token"), data.path("has_more").asBoolean(false));
    }

    public RemoteContainer getRootFolder(FeishuConnection connection, String token) {
        JsonNode data = execute(connection, "GET", "/drive/explorer/v2/root_folder/meta",
                Map.of(), null, token).path("data");
        String id = text(data, "token");
        if (id.isBlank()) id = text(data, "id");
        return new RemoteContainer(id, "我的空间", FeishuSource.Type.FOLDER);
    }

    public DocxDocument getDocument(FeishuConnection connection, String token, String documentId) {
        JsonNode document = execute(connection, "GET", "/docx/v1/documents/" + encodePath(documentId),
                Map.of(), null, token).path("data").path("document");
        return new DocxDocument(text(document, "title"), text(document, "revision_id"));
    }

    public String getRawContent(FeishuConnection connection, String token, String documentId) {
        JsonNode data = execute(connection, "GET", "/docx/v1/documents/" + encodePath(documentId) + "/raw_content",
                Map.of(), null, token).path("data");
        String content = text(data, "content");
        if (content.isBlank()) throw new FeishuApiException("飞书文档正文为空", false);
        return content;
    }

    private JsonNode execute(FeishuConnection connection, String method, String path, Map<String, String> query,
                             String body, String token) {
        FeishuApiException last = null;
        for (int attempt = 1; attempt <= HTTP_ATTEMPTS; attempt++) {
            try {
                HttpRequest.Builder builder = HttpRequest.newBuilder(uri(connection.getApiBaseUrl(), path, query))
                        .timeout(Duration.ofSeconds(30)).header("Accept", "application/json");
                if (token != null) builder.header("Authorization", "Bearer " + token);
                if (body == null) builder.method(method, HttpRequest.BodyPublishers.noBody());
                else builder.header("Content-Type", "application/json; charset=utf-8")
                        .method(method, HttpRequest.BodyPublishers.ofString(body));
                HttpResponse<String> response = http.send(builder.build(), HttpResponse.BodyHandlers.ofString());
                JsonNode root = response.body().isBlank() ? json.createObjectNode() : json.readTree(response.body());
                int code = root.path("code").asInt(0);
                boolean retryable = response.statusCode() == 429 || response.statusCode() >= 500
                        || code == 99991400 || code == 1061045;
                if (response.statusCode() >= 200 && response.statusCode() < 300 && code == 0) return root;
                String message = root.path("msg").asText("HTTP " + response.statusCode());
                last = new FeishuApiException("飞书 API 请求失败：" + message + " (code=" + code + ")", retryable);
                if (!retryable || attempt == HTTP_ATTEMPTS) throw last;
                pause(response.headers().firstValue("Retry-After").orElse(null), attempt);
            } catch (FeishuApiException exception) {
                throw exception;
            } catch (Exception exception) {
                last = new FeishuApiException("飞书 API 网络请求失败：" + exception.getMessage(), true, exception);
                if (attempt == HTTP_ATTEMPTS) throw last;
                pause(null, attempt);
            }
        }
        throw last == null ? new FeishuApiException("飞书 API 请求失败", true) : last;
    }

    private URI uri(String baseUrl, String path, Map<String, String> query) {
        String normalized = baseUrl.endsWith("/") ? baseUrl.substring(0, baseUrl.length() - 1) : baseUrl;
        StringBuilder value = new StringBuilder(normalized).append(path.startsWith("/") ? path : "/" + path);
        if (!query.isEmpty()) {
            value.append('?');
            query.forEach((key, item) -> value.append(encode(key)).append('=').append(encode(item)).append('&'));
            value.setLength(value.length() - 1);
        }
        return URI.create(value.toString());
    }

    private Map<String, String> pageQuery(String pageToken) {
        Map<String, String> query = new LinkedHashMap<>();
        query.put("page_size", String.valueOf(properties.pageSize()));
        if (pageToken != null && !pageToken.isBlank()) query.put("page_token", pageToken);
        return query;
    }

    private void pause(String retryAfter, int attempt) {
        long millis = 250L * (1L << Math.min(4, attempt - 1));
        if (retryAfter != null) {
            try { millis = Math.min(5000, Long.parseLong(retryAfter) * 1000); }
            catch (NumberFormatException ignored) { }
        }
        try { Thread.sleep(millis); }
        catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            throw new FeishuApiException("飞书 API 重试被中断", true, exception);
        }
    }

    private String text(JsonNode node, String field) {
        JsonNode value = node.path(field);
        return value.isMissingNode() || value.isNull() ? "" : value.asText();
    }

    private Instant instant(JsonNode node, String field) {
        String value = text(node, field);
        if (value.isBlank()) return null;
        try {
            long epoch = Long.parseLong(value);
            return Instant.ofEpochSecond(epoch > 10_000_000_000L ? epoch / 1000 : epoch);
        } catch (NumberFormatException exception) {
            try { return Instant.parse(value); }
            catch (Exception ignored) { return null; }
        }
    }

    private String encode(String value) { return URLEncoder.encode(value, StandardCharsets.UTF_8); }
    private String encodePath(String value) { return encode(value).replace("+", "%20"); }

    public record Page<T>(List<T> items, String nextPageToken, boolean hasMore) { }
    public record RemoteContainer(String id, String name, FeishuSource.Type type) { }
    public record RemoteItem(String objectToken, String nodeToken, String parentToken, String objectType,
                             String title, String author, String sourceUrl, Instant updatedAt, boolean container) { }
    public record DocxDocument(String title, String revision) { }
}
