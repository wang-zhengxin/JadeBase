package ai.jadebase.search;

import ai.jadebase.identity.api.AuthenticatedRequest;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/search")
public class GlobalSearchController {

    private final GlobalSearchService service;

    public GlobalSearchController(GlobalSearchService service) {
        this.service = service;
    }

    @GetMapping
    public GlobalSearchService.SearchResult search(
            @RequestParam(defaultValue = "") String query, HttpServletRequest request) {
        return service.search(query, AuthenticatedRequest.user(request));
    }
}
