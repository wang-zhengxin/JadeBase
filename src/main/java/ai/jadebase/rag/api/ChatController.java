package ai.jadebase.rag.api;

import ai.jadebase.rag.application.ChatService;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

@RestController
@RequestMapping("/api/v1/chat")
public class ChatController {

    private final ChatService chatService;

    public ChatController(ChatService chatService) {
        this.chatService = chatService;
    }

    @PostMapping
    public ChatService.ChatResult ask(@Valid @RequestBody AskRequest request) {
        return chatService.ask(request.knowledgeBaseId(), request.conversationId(), request.question(),
                request.topK(), request.language());
    }

    public record AskRequest(@NotNull UUID knowledgeBaseId, UUID conversationId,
                             @NotBlank String question, @Min(1) @Max(12) Integer topK,
                             String language) { }
}
