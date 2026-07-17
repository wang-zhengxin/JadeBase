package ai.jadebase.knowledge.admin;

import ai.jadebase.identity.api.AuthenticatedRequest;
import ai.jadebase.identity.domain.JadeUser;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/admin/knowledge")
public class DocumentKnowledgeAdminController {

    private final DocumentKnowledgeAdminService service;

    public DocumentKnowledgeAdminController(DocumentKnowledgeAdminService service) {
        this.service = service;
    }

    @GetMapping("/summary")
    public DocumentKnowledgeAdminService.InventorySummary summary(HttpServletRequest request) {
        requireOwner(request);
        return service.summary();
    }

    @GetMapping("/documents")
    public List<DocumentKnowledgeAdminService.DocumentView> documents(HttpServletRequest request) {
        requireOwner(request);
        return service.inventory();
    }

    @GetMapping("/document-sets")
    public List<DocumentKnowledgeAdminService.DocumentSetView> documentSets(HttpServletRequest request) {
        requireOwner(request);
        return service.listDocumentSets();
    }

    @PostMapping("/document-sets")
    @ResponseStatus(HttpStatus.CREATED)
    public DocumentKnowledgeAdminService.DocumentSetView createDocumentSet(
            HttpServletRequest request, @RequestBody DocumentKnowledgeAdminService.DocumentSetInput input) {
        requireOwner(request);
        return service.createDocumentSet(input);
    }

    @PutMapping("/document-sets/{setId}")
    public DocumentKnowledgeAdminService.DocumentSetView updateDocumentSet(
            HttpServletRequest request, @PathVariable UUID setId,
            @RequestBody DocumentKnowledgeAdminService.DocumentSetInput input) {
        requireOwner(request);
        return service.updateDocumentSet(setId, input);
    }

    @DeleteMapping("/document-sets/{setId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void deleteDocumentSet(HttpServletRequest request, @PathVariable UUID setId) {
        requireOwner(request);
        service.deleteDocumentSet(setId);
    }

    @GetMapping("/index-settings")
    public DocumentKnowledgeAdminService.IndexSettingsView indexSettings(HttpServletRequest request) {
        requireOwner(request);
        return service.indexSettings();
    }

    @PutMapping("/index-settings")
    public DocumentKnowledgeAdminService.IndexSettingsView updateIndexSettings(
            HttpServletRequest request, @RequestBody DocumentKnowledgeAdminService.IndexSettingsInput input) {
        requireOwner(request);
        return service.updateIndexSettings(input);
    }

    @PostMapping("/reindex")
    @ResponseStatus(HttpStatus.ACCEPTED)
    public DocumentKnowledgeAdminService.ReindexResult reindex(HttpServletRequest request) {
        requireOwner(request);
        return service.reindexAll();
    }

    private void requireOwner(HttpServletRequest request) {
        if (AuthenticatedRequest.user(request).getRole() != JadeUser.Role.OWNER) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "只有工作区所有者可以管理文档与索引");
        }
    }
}
