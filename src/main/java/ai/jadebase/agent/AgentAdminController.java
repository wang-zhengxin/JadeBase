package ai.jadebase.agent;

import ai.jadebase.identity.api.AuthenticatedRequest;
import ai.jadebase.identity.domain.IdentityAccessException;
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

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/admin/agents")
public class AgentAdminController {

    private final AgentService service;

    public AgentAdminController(AgentService service) {
        this.service = service;
    }

    @GetMapping
    public List<AgentService.AgentView> list(HttpServletRequest request,
                                             @RequestParam(required = false) String query) {
        requireOwner(request);
        return service.listAdmin(query);
    }

    @GetMapping("/{agentId}")
    public AgentService.AgentView get(HttpServletRequest request, @PathVariable UUID agentId) {
        requireOwner(request);
        return service.getAdmin(agentId);
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public AgentService.AgentView create(HttpServletRequest request,
                                          @RequestBody AgentService.AgentInput input) {
        JadeUser actor = requireOwner(request);
        return service.create(input, actor);
    }

    @PutMapping("/{agentId}")
    public AgentService.AgentView update(HttpServletRequest request, @PathVariable UUID agentId,
                                          @RequestBody AgentService.AgentInput input) {
        requireOwner(request);
        return service.update(agentId, input);
    }

    @PostMapping("/{agentId}/publish")
    public AgentService.AgentView publish(HttpServletRequest request, @PathVariable UUID agentId) {
        JadeUser actor = requireOwner(request);
        return service.publish(agentId, actor);
    }

    @PatchMapping("/{agentId}/enabled")
    public AgentService.AgentView setEnabled(HttpServletRequest request, @PathVariable UUID agentId,
                                              @RequestBody EnabledInput input) {
        requireOwner(request);
        return service.setEnabled(agentId, input.enabled());
    }

    @GetMapping("/{agentId}/versions")
    public List<AgentService.VersionView> versions(HttpServletRequest request, @PathVariable UUID agentId) {
        requireOwner(request);
        return service.versions(agentId);
    }

    @GetMapping("/{agentId}/runs")
    public List<AgentService.RunView> runs(HttpServletRequest request, @PathVariable UUID agentId) {
        requireOwner(request);
        return service.runs(agentId);
    }

    @DeleteMapping("/{agentId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void delete(HttpServletRequest request, @PathVariable UUID agentId) {
        requireOwner(request);
        service.delete(agentId);
    }

    private JadeUser requireOwner(HttpServletRequest request) {
        JadeUser user = AuthenticatedRequest.user(request);
        if (user.getRole() != JadeUser.Role.OWNER) {
            throw new IdentityAccessException("只有工作区所有者可以管理 Agent");
        }
        return user;
    }

    public record EnabledInput(boolean enabled) { }
}
