package ai.jadebase.agent;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.util.List;

final class AgentConfigJson {

    private static final ObjectMapper JSON = new ObjectMapper();
    private static final TypeReference<List<String>> STRING_LIST = new TypeReference<>() { };

    private AgentConfigJson() { }

    static String write(List<String> values) {
        try {
            return JSON.writeValueAsString(values == null ? List.of() : values);
        } catch (JsonProcessingException exception) {
            throw new IllegalArgumentException("Agent 列表配置无法序列化", exception);
        }
    }

    static List<String> read(String value) {
        if (value == null || value.isBlank()) return List.of();
        try {
            return List.copyOf(JSON.readValue(value, STRING_LIST));
        } catch (JsonProcessingException exception) {
            throw new IllegalStateException("Agent 列表配置已损坏", exception);
        }
    }
}
