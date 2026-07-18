package ai.jadebase.agent;

import ai.jadebase.identity.api.AuthenticatedRequest;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/v1/agents")
public class AgentController {

    private final AgentService service;

    public AgentController(AgentService service) {
        this.service = service;
    }

    @GetMapping
    public List<AgentService.AvailableAgentView> available(HttpServletRequest request) {
        return service.listAvailable(AuthenticatedRequest.user(request));
    }
}
