package ai.jadebase.connector.feishu;

import ai.jadebase.identity.api.AuthenticatedRequest;
import ai.jadebase.identity.domain.JadeUser;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/connectors/feishu")
public class FeishuConnectorController {

    private final FeishuConnectorService service;

    public FeishuConnectorController(FeishuConnectorService service) {
        this.service = service;
    }

    @GetMapping("/connections")
    public List<FeishuConnectorService.ConnectionView> connections(HttpServletRequest request) {
        requireOwner(request);
        return service.listConnections();
    }

    @PostMapping("/connections/test")
    public FeishuConnectorService.ConnectionTest test(HttpServletRequest request,
                                                       @RequestBody FeishuConnectorService.ConnectionInput input) {
        requireOwner(request);
        return service.test(input);
    }

    @PostMapping("/connections")
    @ResponseStatus(HttpStatus.CREATED)
    public FeishuConnectorService.ConnectionView create(HttpServletRequest request,
                                                         @RequestBody FeishuConnectorService.ConnectionInput input) {
        requireOwner(request);
        return service.create(input);
    }

    @PutMapping("/connections/{connectionId}")
    public FeishuConnectorService.ConnectionView update(HttpServletRequest request,
                                                         @PathVariable UUID connectionId,
                                                         @RequestBody FeishuConnectorService.ConnectionInput input) {
        requireOwner(request);
        return service.update(connectionId, input);
    }

    @PostMapping("/connections/{connectionId}/test")
    public FeishuConnectorService.ConnectionTest testExisting(HttpServletRequest request,
                                                               @PathVariable UUID connectionId) {
        requireOwner(request);
        return service.testExisting(connectionId);
    }

    @DeleteMapping("/connections/{connectionId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void deleteConnection(HttpServletRequest request, @PathVariable UUID connectionId) {
        requireOwner(request);
        service.deleteConnection(connectionId);
    }

    @GetMapping("/connections/{connectionId}/spaces")
    public List<FeishuApiClient.RemoteContainer> spaces(HttpServletRequest request,
                                                        @PathVariable UUID connectionId) {
        requireOwner(request);
        return service.spaces(connectionId);
    }

    @GetMapping("/connections/{connectionId}/folders")
    public FeishuConnectorService.FolderBrowse folders(HttpServletRequest request,
                                                        @PathVariable UUID connectionId,
                                                        @RequestParam(required = false) String folderToken) {
        requireOwner(request);
        return service.folders(connectionId, folderToken);
    }

    @GetMapping("/sources")
    public List<FeishuConnectorService.SourceView> sources(HttpServletRequest request) {
        requireOwner(request);
        return service.listSources();
    }

    @PostMapping("/sources")
    @ResponseStatus(HttpStatus.CREATED)
    public FeishuConnectorService.SourceView createSource(HttpServletRequest request,
                                                           @RequestBody FeishuConnectorService.SourceInput input) {
        requireOwner(request);
        return service.createSource(input);
    }

    @PatchMapping("/sources/{sourceId}")
    public FeishuConnectorService.SourceView setEnabled(HttpServletRequest request, @PathVariable UUID sourceId,
                                                         @RequestBody EnabledInput input) {
        requireOwner(request);
        return service.setEnabled(sourceId, input.enabled());
    }

    @DeleteMapping("/sources/{sourceId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void deleteSource(HttpServletRequest request, @PathVariable UUID sourceId) {
        requireOwner(request);
        service.deleteSource(sourceId);
    }

    @PostMapping("/sources/{sourceId}/sync")
    @ResponseStatus(HttpStatus.ACCEPTED)
    public FeishuConnectorService.TaskView sync(HttpServletRequest request, @PathVariable UUID sourceId,
                                                 @RequestBody(required = false) SyncInput input) {
        requireOwner(request);
        FeishuSyncTask.Mode mode = input == null || input.mode() == null
                ? FeishuSyncTask.Mode.INCREMENTAL : input.mode();
        return service.sync(sourceId, mode);
    }

    @GetMapping("/sync-tasks")
    public List<FeishuConnectorService.TaskView> tasks(HttpServletRequest request) {
        requireOwner(request);
        return service.tasks();
    }

    @PostMapping("/sync-tasks/{taskId}/retry")
    @ResponseStatus(HttpStatus.ACCEPTED)
    public FeishuConnectorService.TaskView retry(HttpServletRequest request, @PathVariable UUID taskId) {
        requireOwner(request);
        return service.retry(taskId);
    }

    private void requireOwner(HttpServletRequest request) {
        if (AuthenticatedRequest.user(request).getRole() != JadeUser.Role.OWNER) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "只有工作区所有者可以管理连接器");
        }
    }

    public record EnabledInput(boolean enabled) { }
    public record SyncInput(FeishuSyncTask.Mode mode) { }
}
