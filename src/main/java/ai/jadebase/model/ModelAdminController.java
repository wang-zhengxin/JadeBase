package ai.jadebase.model;

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
@RequestMapping("/api/v1/admin/model-providers")
public class ModelAdminController {

    private final ModelAdminService service;

    public ModelAdminController(ModelAdminService service) {
        this.service = service;
    }

    @GetMapping("/catalog")
    public List<ModelProviderCatalog.Preset> catalog(HttpServletRequest request) {
        requireOwner(request);
        return service.catalog();
    }

    @GetMapping
    public List<ModelAdminService.ProviderView> providers(HttpServletRequest request) {
        requireOwner(request);
        return service.list();
    }

    @PostMapping("/discover")
    public ModelAdminService.DiscoveryResult discover(HttpServletRequest request,
                                                       @RequestBody ModelAdminService.ProviderConnection input) {
        requireOwner(request);
        return service.discover(input);
    }

    @PostMapping("/{providerId}/discover")
    public ModelAdminService.DiscoveryResult discoverExisting(HttpServletRequest request,
                                                               @PathVariable UUID providerId,
                                                               @RequestBody ModelAdminService.ProviderConnection input) {
        requireOwner(request);
        return service.discoverExisting(providerId, input);
    }

    @PostMapping("/test")
    public ModelAdminService.TestResult test(HttpServletRequest request,
                                              @RequestBody ModelAdminService.ProviderTest input) {
        requireOwner(request);
        return service.test(input);
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public ModelAdminService.ProviderView create(HttpServletRequest request,
                                                  @RequestBody ModelAdminService.ProviderInput input) {
        requireOwner(request);
        return service.create(input);
    }

    @PutMapping("/{providerId}")
    public ModelAdminService.ProviderView update(HttpServletRequest request, @PathVariable UUID providerId,
                                                  @RequestBody ModelAdminService.ProviderInput input) {
        requireOwner(request);
        return service.update(providerId, input);
    }

    @PostMapping("/{providerId}/test")
    public ModelAdminService.TestResult testExisting(HttpServletRequest request, @PathVariable UUID providerId) {
        requireOwner(request);
        return service.testExisting(providerId);
    }

    @DeleteMapping("/{providerId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void delete(HttpServletRequest request, @PathVariable UUID providerId) {
        requireOwner(request);
        service.delete(providerId);
    }

    @PutMapping("/default")
    public ModelAdminService.CurrentModelView setDefault(HttpServletRequest request,
                                                          @RequestBody ModelAdminService.DefaultModelInput input) {
        requireOwner(request);
        return service.setDefault(input);
    }

    private void requireOwner(HttpServletRequest request) {
        if (AuthenticatedRequest.user(request).getRole() != JadeUser.Role.OWNER) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "只有工作区所有者可以管理语言模型");
        }
    }
}
