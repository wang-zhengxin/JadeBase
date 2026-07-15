package ai.jadebase.conversation.api;

import ai.jadebase.conversation.domain.ConversationService;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/conversations")
public class ConversationController {

    private final ConversationService service;

    public ConversationController(ConversationService service) {
        this.service = service;
    }

    @GetMapping
    public List<ConversationService.ConversationSummary> list(
            @RequestParam(required = false) String query) {
        return service.list(query);
    }

    @GetMapping("/{conversationId}")
    public ConversationService.ConversationDetail get(@PathVariable UUID conversationId) {
        return service.get(conversationId);
    }

    @DeleteMapping("/{conversationId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void delete(@PathVariable UUID conversationId) {
        service.delete(conversationId);
    }
}
