package ai.jadebase.rag.api;

import ai.jadebase.rag.application.ChatService;
import ai.jadebase.identity.api.AuthenticatedRequest;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.http.MediaType;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.io.IOException;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.Executor;

@RestController
@RequestMapping("/api/v1/chat")
public class ChatController {

    private final ChatService chatService;
    private final Executor chatExecutor;

    public ChatController(ChatService chatService, @Qualifier("chatExecutor") Executor chatExecutor) {
        this.chatService = chatService;
        this.chatExecutor = chatExecutor;
    }

    @PostMapping
    public ChatService.ChatResult ask(@Valid @RequestBody AskRequest request, HttpServletRequest servletRequest) {
        return chatService.ask(request.knowledgeBaseId(), request.conversationId(), request.question(),
                request.topK(), request.language(), Boolean.TRUE.equals(request.thinkMode()), request.agentId(),
                AuthenticatedRequest.user(servletRequest));
    }

    @PostMapping(path = "/stream", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public SseEmitter stream(@Valid @RequestBody AskRequest request, HttpServletRequest servletRequest) {
        SseEmitter emitter = new SseEmitter(180_000L);
        var actor = AuthenticatedRequest.user(servletRequest);
        boolean thinkMode = Boolean.TRUE.equals(request.thinkMode());
        if (thinkMode) send(emitter, "thinking", new ThinkingEvent("retrieval", "正在分析问题并检索知识库…"));
        chatExecutor.execute(() -> {
            try {
                ChatService.ChatResult result = chatService.ask(request.knowledgeBaseId(), request.conversationId(),
                        request.question(), request.topK(), request.language(), thinkMode, request.agentId(), actor);
                send(emitter, "result", result);
                emitter.complete();
            } catch (Exception exception) {
                send(emitter, "error", Map.of("message", safeMessage(exception)));
                emitter.complete();
            }
        });
        return emitter;
    }

    private void send(SseEmitter emitter, String name, Object data) {
        try {
            emitter.send(SseEmitter.event().name(name).data(data));
        } catch (IOException exception) {
            emitter.completeWithError(exception);
        }
    }

    private String safeMessage(Exception exception) {
        String message = exception.getMessage();
        return message == null || message.isBlank() ? "回答生成失败" : message;
    }

    public record AskRequest(@NotNull UUID knowledgeBaseId, UUID conversationId,
                             @NotBlank String question, @Min(1) @Max(12) Integer topK,
                             String language, Boolean thinkMode, UUID agentId) { }
    public record ThinkingEvent(String stage, String message) { }
}
