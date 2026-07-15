package ai.jadebase.rag.api;

import ai.jadebase.rag.application.ChatService;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
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
        return chatService.ask(request.knowledgeBaseId(), request.question());
    }

    public record AskRequest(@NotNull UUID knowledgeBaseId, @NotBlank String question) { }
}
