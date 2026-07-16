package ai.jadebase.knowledge.api;

import ai.jadebase.knowledge.domain.Document;
import ai.jadebase.knowledge.domain.KnowledgeBase;
import ai.jadebase.knowledge.domain.KnowledgeBaseService;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;
import ai.jadebase.knowledge.domain.DocumentProgressBroker;

import java.io.IOException;
import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/knowledge-bases")
public class KnowledgeBaseController {

    private final KnowledgeBaseService service;
    private final DocumentProgressBroker progressBroker;

    public KnowledgeBaseController(KnowledgeBaseService service, DocumentProgressBroker progressBroker) {
        this.service = service;
        this.progressBroker = progressBroker;
    }

    @GetMapping
    public List<KnowledgeBase> list() {
        return service.list();
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public KnowledgeBase create(@Valid @RequestBody CreateKnowledgeBase request) {
        return service.create(request.name(), request.description());
    }

    @GetMapping("/{knowledgeBaseId}/documents")
    public List<Document> listDocuments(@PathVariable UUID knowledgeBaseId) {
        return service.listDocuments(knowledgeBaseId);
    }

    @GetMapping(path = "/{knowledgeBaseId}/documents/events", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public SseEmitter documentEvents(@PathVariable UUID knowledgeBaseId) {
        return progressBroker.subscribe(knowledgeBaseId, service.listDocuments(knowledgeBaseId));
    }

    @PostMapping("/{knowledgeBaseId}/documents")
    @ResponseStatus(HttpStatus.ACCEPTED)
    public Document upload(@PathVariable UUID knowledgeBaseId,
                           @RequestPart("file") MultipartFile file) throws IOException {
        return service.upload(knowledgeBaseId, file);
    }

    @PostMapping("/{knowledgeBaseId}/documents/{documentId}/retry")
    @ResponseStatus(HttpStatus.ACCEPTED)
    public Document retry(@PathVariable UUID knowledgeBaseId, @PathVariable UUID documentId) {
        return service.retryDocument(knowledgeBaseId, documentId);
    }

    @PostMapping("/{knowledgeBaseId}/documents/{documentId}/reindex")
    @ResponseStatus(HttpStatus.ACCEPTED)
    public Document reindexDocument(@PathVariable UUID knowledgeBaseId, @PathVariable UUID documentId) {
        return service.reindexDocument(knowledgeBaseId, documentId);
    }

    @PostMapping("/{knowledgeBaseId}/reindex")
    @ResponseStatus(HttpStatus.ACCEPTED)
    public List<Document> reindexKnowledgeBase(@PathVariable UUID knowledgeBaseId) {
        return service.reindexKnowledgeBase(knowledgeBaseId);
    }

    @DeleteMapping("/{knowledgeBaseId}/documents/{documentId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void deleteDocument(@PathVariable UUID knowledgeBaseId, @PathVariable UUID documentId) {
        service.deleteDocument(knowledgeBaseId, documentId);
    }

    public record CreateKnowledgeBase(@NotBlank String name, String description) { }
}
