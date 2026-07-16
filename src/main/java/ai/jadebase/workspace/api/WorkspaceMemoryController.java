package ai.jadebase.workspace.api;

import ai.jadebase.workspace.domain.WorkspaceMemory;
import ai.jadebase.workspace.domain.WorkspaceMemoryService;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/memories")
public class WorkspaceMemoryController {

    private final WorkspaceMemoryService service;

    public WorkspaceMemoryController(WorkspaceMemoryService service) {
        this.service = service;
    }

    @GetMapping
    public List<MemoryResponse> list() {
        return service.list().stream().map(MemoryResponse::from).toList();
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public MemoryResponse add(@Valid @RequestBody AddMemory request) {
        return MemoryResponse.from(service.add(request.content()));
    }

    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void delete(@PathVariable UUID id) {
        service.delete(id);
    }

    public record AddMemory(@NotBlank @Size(max = 500) String content) { }
    public record MemoryResponse(UUID id, String content, Instant createdAt) {
        static MemoryResponse from(WorkspaceMemory memory) {
            return new MemoryResponse(memory.getId(), memory.getContent(), memory.getCreatedAt());
        }
    }
}
